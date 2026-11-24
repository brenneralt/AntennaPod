package de.danoeh.antennapod.net.common.youtube;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class YouTubeFeedUrlTest {
    @Test
    public void testPlaylistUrlIsConvertedToFeedUrl() {
        assertEquals("https://www.youtube.com/feeds/videos.xml?playlist_id=PLKxb0YgUkju4gjIIjUN57_jBixiLLK4VI",
                YouTubeFeedUrl.convertLocally(
                        "https://www.youtube.com/playlist?list=PLKxb0YgUkju4gjIIjUN57_jBixiLLK4VI"));
    }

    @Test
    public void testWatchUrlWithPlaylistIsConvertedToFeedUrl() {
        assertEquals("https://www.youtube.com/feeds/videos.xml?playlist_id=PLKxb0YgUkju4gjIIjUN57_jBixiLLK4VI",
                YouTubeFeedUrl.convertLocally(
                        "https://www.youtube.com/watch?v=abc123&list=PLKxb0YgUkju4gjIIjUN57_jBixiLLK4VI"));
    }

    @Test
    public void testChannelUrlIsConvertedToFeedUrl() {
        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=UCIHwyJi76D027pbkzct731g",
                YouTubeFeedUrl.convertLocally(
                        "https://www.youtube.com/channel/UCIHwyJi76D027pbkzct731g"));
    }

    @Test
    public void testUploadsPlaylistIsConvertedToChannelFeedUrl() {
        assertEquals("https://www.youtube.com/feeds/videos.xml?channel_id=UCIHwyJi76D027pbkzct731g",
                YouTubeFeedUrl.convertLocally(
                        "https://www.youtube.com/watch?v=abc123&list=UUIHwyJi76D027pbkzct731g"));
    }

    @Test
    public void testFeedUrlIsNotConverted() {
        assertNull(YouTubeFeedUrl.convertLocally(
                "https://www.youtube.com/feeds/videos.xml?channel_id=UCIHwyJi76D027pbkzct731g"));
        assertFalse(YouTubeFeedUrl.needsLookup(
                "https://www.youtube.com/feeds/videos.xml?channel_id=UCIHwyJi76D027pbkzct731g"));
    }

    @Test
    public void testWatchUrlWithoutPlaylistNeedsNoLookup() {
        assertNull(YouTubeFeedUrl.convertLocally("https://www.youtube.com/watch?v=abc123"));
        assertFalse(YouTubeFeedUrl.needsLookup("https://www.youtube.com/watch?v=abc123"));
    }

    @Test
    public void testNonYoutubeUrlNeedsNoLookup() {
        assertFalse(YouTubeFeedUrl.needsLookup("https://example.com/feed.xml"));
        assertFalse(YouTubeFeedUrl.needsLookup("https://notyoutube.com/watch?v=abc"));
        assertFalse(YouTubeFeedUrl.needsLookup("https://youtube.com.evil.example/playlist?list=PL1"));
        assertFalse(YouTubeFeedUrl.needsLookup(null));
    }

    @Test
    public void testChannelPageNeedsLookup() {
        assertTrue(YouTubeFeedUrl.needsLookup("https://www.youtube.com/@eslenpodcast"));
        assertTrue(YouTubeFeedUrl.needsLookup("https://www.youtube.com/@eslenpodcast/videos"));
        assertTrue(YouTubeFeedUrl.needsLookup("https://m.youtube.com/channel/UCIHwyJi76D027pbkzct731g"));
    }

    @Test
    public void testFromUserInputNormalizesYoutubeUrls() {
        assertEquals("https://www.youtube.com/@eslenpodcast",
                YouTubeFeedUrl.fromUserInput("@eslenpodcast"));
        assertEquals("https://youtube.com/playlist?list=PL1",
                YouTubeFeedUrl.fromUserInput("youtube.com/playlist?list=PL1"));
        assertEquals("https://www.youtube.com/watch?v=abc",
                YouTubeFeedUrl.fromUserInput("https://www.youtube.com/watch?v=abc"));
    }

    @Test
    public void testFromUserInputRejectsNonYoutubeInput() {
        assertNull(YouTubeFeedUrl.fromUserInput("https://example.com/feed.xml"));
        assertNull(YouTubeFeedUrl.fromUserInput("some podcast name"));
        assertNull(YouTubeFeedUrl.fromUserInput(null));
    }
}
