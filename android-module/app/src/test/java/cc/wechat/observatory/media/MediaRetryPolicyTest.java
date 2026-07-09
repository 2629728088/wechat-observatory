package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaRetryPolicyTest {
    @Test
    public void shouldScheduleSupportedMediaWhenUploadIsMissingFromFirstPayload() {
        assertTrue(MediaRetryPolicy.shouldSchedule(true, 12L, 3, false, ""));
        assertTrue(MediaRetryPolicy.shouldSchedule(true, 13L, 34, false, null));
        assertTrue(MediaRetryPolicy.shouldSchedule(true, 14L, 49, false, ""));
    }

    @Test
    public void shouldNotScheduleWhenUploadIsDisabledOrRecordIdIsMissing() {
        assertFalse(MediaRetryPolicy.shouldSchedule(false, 12L, 3, false, ""));
        assertFalse(MediaRetryPolicy.shouldSchedule(true, 0L, 3, false, ""));
        assertFalse(MediaRetryPolicy.shouldSchedule(true, -1L, 3, false, ""));
    }

    @Test
    public void shouldNotScheduleWhenMediaAlreadyUploadedOrFirstPayloadHasMedia() {
        assertFalse(MediaRetryPolicy.shouldSchedule(true, 12L, 3, true, ""));
        assertFalse(MediaRetryPolicy.shouldSchedule(true, 12L, 3, false, "base64"));
    }

    @Test
    public void shouldNotScheduleUnsupportedMessageType() {
        assertFalse(MediaRetryPolicy.shouldSchedule(true, 12L, 1, false, ""));
        assertFalse(MediaRetryPolicy.shouldSchedule(true, 12L, 999, false, ""));
    }
}
