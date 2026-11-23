package de.danoeh.antennapod.net.common.youtube;

import androidx.annotation.NonNull;
import android.util.Log;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.HttpUrl;
import okhttp3.RequestBody;
import okhttp3.Request.Builder;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class YouTubeStreamResolver {
    private static final String TAG = "YouTubeStreamResolver";
    private static final long CACHE_DURATION_MS = TimeUnit.HOURS.toMillis(4);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0";
    private static final String AUDIO_MP4 = "audio/mp4";
    private static final Map<String, CachedStream> CACHE = new ConcurrentHashMap<>();
    private static final Object INIT_LOCK = new Object();

    private static volatile boolean initialized;

    private YouTubeStreamResolver() {
    }

    public static boolean isYouTubeMediaUrl(String url) {
        if (url == null) {
            return false;
        }
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            return false;
        }
        String host = httpUrl.host().toLowerCase(Locale.US);
        boolean youtubeHost = host.equals("youtube.com") || host.endsWith(".youtube.com")
                || host.equals("youtu.be") || host.endsWith(".youtu.be");
        if (!youtubeHost) {
            return false;
        }
        String path = httpUrl.encodedPath();
        return path.startsWith("/watch") || path.startsWith("/v/") || host.endsWith("youtu.be");
    }

    public static String resolve(@NonNull String url) throws IOException {
        if (!isYouTubeMediaUrl(url)) {
            return url;
        }
        long now = System.currentTimeMillis();
        CachedStream cached = CACHE.get(url);
        if (cached != null && cached.expiresAtMillis > now) {
            return cached.streamUrl;
        }
        try {
            ensureInitialized();
            StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(url);
            extractor.fetchPage();
            AudioStream bestStream = selectBestAudioStream(extractor.getAudioStreams());
            if (bestStream == null) {
                throw new IOException("No playable audio stream found for " + url);
            }
            String streamUrl = bestStream.getContent();
            CACHE.put(url, new CachedStream(streamUrl, now + CACHE_DURATION_MS));
            evictExpired(now);
            return streamUrl;
        } catch (ExtractionException e) {
            Log.w(TAG, "Could not extract audio stream for " + url, e);
            throw new IOException("Could not extract audio stream for " + url, e);
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (!initialized) {
                NewPipe.init(new ExtractorDownloader());
                initialized = true;
            }
        }
    }

    static AudioStream selectBestAudioStream(List<AudioStream> streams) {
        if (streams == null) {
            return null;
        }
        AudioStream best = null;
        boolean bestIsMp4 = false;
        int bestRank = 0;
        for (AudioStream stream : streams) {
            if (stream.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) {
                continue;
            }
            String content = stream.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }
            boolean isMp4 = stream.getFormat() != null
                    && AUDIO_MP4.equals(stream.getFormat().mimeType);
            int rank = audioTrackRank(stream);
            boolean better = best == null
                    || rank < bestRank
                    || (rank == bestRank && isMp4 && !bestIsMp4)
                    || (rank == bestRank && isMp4 == bestIsMp4
                    && stream.getAverageBitrate() > best.getAverageBitrate());
            if (better) {
                best = stream;
                bestIsMp4 = isMp4;
                bestRank = rank;
            }
        }
        return best;
    }

    private static int audioTrackRank(AudioStream stream) {
        if (stream.getAudioTrackType() == AudioTrackType.ORIGINAL) {
            return 0;
        }
        Locale locale = stream.getAudioLocale();
        if (locale != null && "pt".equals(locale.getLanguage())) {
            return 1;
        }
        return 2;
    }

    private static void evictExpired(long now) {
        for (Map.Entry<String, CachedStream> entry : CACHE.entrySet()) {
            if (entry.getValue().expiresAtMillis <= now) {
                CACHE.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static final class CachedStream {
        private final String streamUrl;
        private final long expiresAtMillis;

        private CachedStream(String streamUrl, long expiresAtMillis) {
            this.streamUrl = streamUrl;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private static final class ExtractorDownloader extends Downloader {
        @Override
        public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
            String httpMethod = request.httpMethod();
            String url = request.url();
            byte[] dataToSend = request.dataToSend();
            RequestBody requestBody = null;
            if (dataToSend != null) {
                requestBody = RequestBody.create(dataToSend);
            }
            Builder builder = new Builder()
                    .method(httpMethod, requestBody)
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT);
            for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
                if (header.getKey() == null || header.getValue() == null) {
                    continue;
                }
                builder.removeHeader(header.getKey());
                for (String value : header.getValue()) {
                    builder.addHeader(header.getKey(), value);
                }
            }
            try (var response = AntennapodHttpClient.getHttpClient().newBuilder()
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .build()))
                    .build()
                    .newCall(builder.build()).execute()) {
                if (response.code() == 429) {
                    throw new ReCaptchaException("reCaptcha Challenge requested", url);
                }
                String body = response.body() != null ? response.body().string() : "";
                return new Response(response.code(), response.message(),
                        response.headers().toMultimap(), body,
                        response.request().url().toString());
            }
        }
    }
}
