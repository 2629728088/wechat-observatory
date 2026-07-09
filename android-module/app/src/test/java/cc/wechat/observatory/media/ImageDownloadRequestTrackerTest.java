package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadRequestTrackerTest {
    @Test
    public void requestIdPrefersLocalThenServer() {
        assertEquals(12L, ImageDownloadRequestTracker.requestId(12L, 99L));
        assertEquals(99L, ImageDownloadRequestTracker.requestId(0L, 99L));
        assertEquals(0L, ImageDownloadRequestTracker.requestId(0L, 0L));
        assertEquals(0L, ImageDownloadRequestTracker.requestId(-1L, -2L));
    }

    @Test
    public void rememberReturnsFalseForDuplicateOrInvalidId() {
        ImageDownloadRequestTracker tracker = new ImageDownloadRequestTracker(1024);

        assertFalse(tracker.remember(0L));
        assertTrue(tracker.remember(100L));
        assertFalse(tracker.remember(100L));
        assertTrue(tracker.remember(101L));
    }

    @Test
    public void rememberClearsWhenCapacityWasExceeded() {
        ImageDownloadRequestTracker tracker = new ImageDownloadRequestTracker(1);

        assertTrue(tracker.remember(1L));
        assertTrue(tracker.remember(2L));
        assertTrue(tracker.remember(1L));
    }
}
