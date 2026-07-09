package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class MediaRetryLogLineTest {
    @Test
    public void uploadedKeepsExistingFormat() {
        assertEquals(
                "media retry uploaded type=3 msgId=123 attempt=2 size=456",
                MediaRetryLogLine.uploaded(3, 123L, 2, 456L));
    }

    @Test
    public void exhaustedKeepsExistingFormat() {
        assertEquals(
                "media retry exhausted type=49 msgId=123",
                MediaRetryLogLine.exhausted(49, 123L));
    }

    @Test
    public void emptyKeepsExistingFormat() {
        assertEquals(
                "media retry empty type=3 msgId=123 attempt=2",
                MediaRetryLogLine.empty(3, 123L, 2));
    }

    @Test
    public void stoppedReportsSafeReason() {
        assertEquals(
                "media retry stopped type=3 msgId=123 attempt=1 reason=attempt unavailable",
                MediaRetryLogLine.stopped(3, 123L, 1, "attempt unavailable"));
    }

    @Test
    public void failedUsesRootCause() {
        Throwable failure = new IllegalStateException(
                "wrapper",
                new IllegalArgumentException("decode failed"));

        String line = MediaRetryLogLine.failed(3, 123L, 1, failure);

        assertEquals(
                "media retry failed type=3 msgId=123 attempt=1 error=decode failed",
                line);
        assertFalse(line.contains("<msg"));
        assertFalse(line.contains("base64"));
    }
}
