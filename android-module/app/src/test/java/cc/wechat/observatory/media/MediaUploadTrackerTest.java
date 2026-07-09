package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaUploadTrackerTest {
    @Test
    public void markRetryScheduledRejectsInvalidAndDuplicateIds() {
        MediaUploadTracker tracker = new MediaUploadTracker(8);

        assertFalse(tracker.markRetryScheduled(0L));
        assertTrue(tracker.markRetryScheduled(12L));
        assertFalse(tracker.markRetryScheduled(12L));
    }

    @Test
    public void rememberUploadedTracksPositiveIdsOnly() {
        MediaUploadTracker tracker = new MediaUploadTracker(8);

        tracker.rememberUploaded(0L);
        tracker.rememberUploaded(7L);

        assertFalse(tracker.hasUploaded(0L));
        assertTrue(tracker.hasUploaded(7L));
        assertFalse(tracker.hasUploaded(8L));
    }

    @Test
    public void scheduledIdsAreTrimmedWhenCapacityIsExceeded() {
        MediaUploadTracker tracker = new MediaUploadTracker(1);

        assertTrue(tracker.markRetryScheduled(1L));
        assertTrue(tracker.markRetryScheduled(2L));
        assertTrue(tracker.markRetryScheduled(1L));
    }

    @Test
    public void uploadedIdsAreTrimmedWhenCapacityIsExceeded() {
        MediaUploadTracker tracker = new MediaUploadTracker(1);

        tracker.rememberUploaded(1L);
        tracker.rememberUploaded(2L);
        tracker.rememberUploaded(3L);

        assertFalse(tracker.hasUploaded(1L));
        assertFalse(tracker.hasUploaded(2L));
        assertTrue(tracker.hasUploaded(3L));
    }
}
