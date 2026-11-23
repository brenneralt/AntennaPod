package de.danoeh.antennapod.net.common.youtube;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class YouTubeStreamResolverTest {
    @Test
    public void testMediaUrlFromYoutubeFeedIsRecognized() {
        assertTrue(YouTubeStreamResolver.isYouTubeMediaUrl(
                "https://www.youtube.com/v/yN-yZ7-PJVk?version=3"));
    }

    @Test
    public void testWatchUrlIsRecognized() {
        assertTrue(YouTubeStreamResolver.isYouTubeMediaUrl(
                "https://www.youtube.com/watch?v=yN-yZ7-PJVk"));
        assertTrue(YouTubeStreamResolver.isYouTubeMediaUrl(
                "https://m.youtube.com/watch?v=yN-yZ7-PJVk"));
        assertTrue(YouTubeStreamResolver.isYouTubeMediaUrl(
                "https://youtu.be/yN-yZ7-PJVk"));
    }

    @Test
    public void testNonVideoYoutubeUrlIsRejected() {
        assertFalse(YouTubeStreamResolver.isYouTubeMediaUrl(
                "https://www.youtube.com/channel/UCIHwyJi76D027pbkzct731g"));
        assertFalse(YouTubeStreamResolver.isYouTubeMediaUrl(
                "https://www.youtube.com/feeds/videos.xml?channel_id=UCIHwyJi76D027pbkzct731g"));
    }

    @Test
    public void testOtherHostsAreRejected() {
        assertFalse(YouTubeStreamResolver.isYouTubeMediaUrl("https://example.com/v/abc123"));
        assertFalse(YouTubeStreamResolver.isYouTubeMediaUrl("https://notyoutube.com/watch?v=abc"));
        assertFalse(YouTubeStreamResolver.isYouTubeMediaUrl("https://youtube.com.evil.example/watch?v=x"));
    }

    @Test
    public void testInvalidInputIsRejected() {
        assertFalse(YouTubeStreamResolver.isYouTubeMediaUrl(null));
        assertFalse(YouTubeStreamResolver.isYouTubeMediaUrl("not a url"));
        assertFalse(YouTubeStreamResolver.isYouTubeMediaUrl(""));
    }

    @Test
    public void testOriginalAudioTrackIsPreferredOverDubbed() {
        AudioStream dubbed = audioStream(AudioTrackType.DUBBED, Locale.US, MediaFormat.M4A, 128);
        AudioStream original = audioStream(AudioTrackType.ORIGINAL,
                Locale.forLanguageTag("es-US"), MediaFormat.M4A, 128);
        assertSame(original, YouTubeStreamResolver.selectBestAudioStream(
                Arrays.asList(dubbed, original)));
    }

    @Test
    public void testPortugueseAudioTrackIsPreferredWhenThereIsNoOriginal() {
        AudioStream dubbed = audioStream(AudioTrackType.DUBBED, Locale.US, MediaFormat.M4A, 128);
        AudioStream portuguese = audioStream(AudioTrackType.DUBBED,
                Locale.forLanguageTag("pt-BR"), MediaFormat.M4A, 128);
        assertSame(portuguese, YouTubeStreamResolver.selectBestAudioStream(
                Arrays.asList(dubbed, portuguese)));
    }

    @Test
    public void testMp4WithHighestBitrateIsPreferredWithoutTrackInfo() {
        AudioStream webm = audioStream(null, null, MediaFormat.WEBMA_OPUS, 160);
        AudioStream mp4Low = audioStream(null, null, MediaFormat.M4A, 48);
        AudioStream mp4High = audioStream(null, null, MediaFormat.M4A, 128);
        assertSame(mp4High, YouTubeStreamResolver.selectBestAudioStream(
                Arrays.asList(webm, mp4Low, mp4High)));
    }

    private static AudioStream audioStream(AudioTrackType trackType, Locale locale,
            MediaFormat format, int bitrate) {
        return new AudioStream.Builder()
                .setId(Stream.ID_UNKNOWN)
                .setContent("https://example.com/stream", true)
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setMediaFormat(format)
                .setAverageBitrate(bitrate)
                .setAudioTrackType(trackType)
                .setAudioLocale(locale)
                .build();
    }
}
