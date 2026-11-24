package de.danoeh.antennapod.net.common.youtube;

import android.util.Log;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YouTubeFeedUrl {
    private static final String TAG = "YouTubeFeedUrl";
    private static final String FEED_BASE = "https://www.youtube.com/feeds/videos.xml";
    private static final Pattern CANONICAL_LINK = Pattern.compile(
            "<link[^>]*rel=\"canonical\"[^>]*href=\"([^\"]+)\"[^>]*>");
    private static final Pattern CANONICAL_LINK_REVERSED = Pattern.compile(
            "<link[^>]*href=\"([^\"]+)\"[^>]*rel=\"canonical\"[^>]*>");

    private YouTubeFeedUrl() {
    }

    public static String fromUserInput(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        String candidate;
        if (trimmed.startsWith("@")) {
            candidate = "https://www.youtube.com/" + trimmed;
        } else if (trimmed.matches("(?i)https?://.*")) {
            candidate = trimmed;
        } else {
            candidate = "https://" + trimmed;
        }
        HttpUrl httpUrl = HttpUrl.parse(candidate);
        if (httpUrl == null || !isYouTubeHost(httpUrl.host())) {
            return null;
        }
        return candidate;
    }

    public static boolean needsLookup(String url) {
        if (url == null) {
            return false;
        }
        String normalized = fromUserInput(url);
        if (normalized == null) {
            normalized = url;
        }
        HttpUrl httpUrl = HttpUrl.parse(normalized);
        if (httpUrl == null || !isYouTubeHost(httpUrl.host())) {
            return false;
        }
        if (httpUrl.encodedPath().startsWith("/feeds/videos.xml")) {
            return false;
        }
        return convertLocally(normalized) != null || isChannelPage(httpUrl);
    }

    public static String resolve(String url) {
        if (url == null) {
            return null;
        }
        String normalized = fromUserInput(url);
        if (normalized == null) {
            normalized = url;
        }
        String local = convertLocally(normalized);
        if (local != null) {
            return local;
        }
        HttpUrl httpUrl = HttpUrl.parse(normalized);
        if (httpUrl == null || !isYouTubeHost(httpUrl.host()) || !isChannelPage(httpUrl)) {
            return normalized;
        }
        String channelId = fetchChannelId(normalized);
        if (channelId == null) {
            return normalized;
        }
        return FEED_BASE + "?channel_id=" + channelId;
    }

    static String convertLocally(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null || !isYouTubeHost(httpUrl.host())) {
            return null;
        }
        String listId = httpUrl.queryParameter("list");
        if (listId != null && !listId.isEmpty()) {
            if (listId.startsWith("PL")) {
                return FEED_BASE + "?playlist_id=" + listId;
            }
            if (listId.length() == 24 && listId.startsWith("UU")) {
                return FEED_BASE + "?channel_id=UC" + listId.substring(2);
            }
            return null;
        }
        List<String> segments = httpUrl.pathSegments();
        if (segments.size() >= 2 && "channel".equals(segments.get(0))
                && segments.get(1).length() == 24 && segments.get(1).startsWith("UC")) {
            return FEED_BASE + "?channel_id=" + segments.get(1);
        }
        return null;
    }

    private static boolean isChannelPage(HttpUrl httpUrl) {
        List<String> segments = httpUrl.pathSegments();
        if (segments.isEmpty()) {
            return false;
        }
        String first = segments.get(0);
        return first.startsWith("@") || "c".equals(first) || "user".equals(first);
    }

    private static boolean isYouTubeHost(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.US);
        return lower.equals("youtube.com") || lower.endsWith(".youtube.com")
                || lower.equals("youtu.be") || lower.endsWith(".youtu.be");
    }

    private static String fetchChannelId(String pageUrl) {
        Request request = new Request.Builder().url(pageUrl).build();
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "Could not load " + pageUrl + ": HTTP " + response.code());
                return null;
            }
            String canonical = findCanonical(response.body().string());
            if (canonical == null) {
                Log.w(TAG, "No canonical channel link found on " + pageUrl);
                return null;
            }
            if (canonical.startsWith("//")) {
                canonical = "https:" + canonical;
            }
            HttpUrl canonicalUrl = HttpUrl.parse(canonical);
            if (canonicalUrl == null) {
                return null;
            }
            List<String> segments = canonicalUrl.pathSegments();
            if (segments.size() >= 2 && "channel".equals(segments.get(0))
                    && segments.get(1).startsWith("UC")) {
                return segments.get(1);
            }
            return null;
        } catch (IOException | IllegalArgumentException e) {
            Log.w(TAG, "Could not resolve channel for " + pageUrl, e);
            return null;
        }
    }

    private static String findCanonical(String body) {
        Matcher matcher = CANONICAL_LINK.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = CANONICAL_LINK_REVERSED.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
