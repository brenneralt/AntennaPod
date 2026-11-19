package de.danoeh.antennapod.net.common.youtube;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
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
}
