package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public final class MediaAttachmentLogLineTest {
    @Test
    public void shouldLogAttachedAndActionableFailures() {
        assertTrue(MediaAttachmentLogLine.shouldLog(
                MediaAttachmentProcessor.Result.attached("image", MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE)));
        assertTrue(MediaAttachmentLogLine.shouldLog(
                MediaAttachmentProcessor.Result.skipped(
                        MediaAttachmentProcessor.AttachmentStatus.MEDIA_NOT_SELECTED,
                        "image",
                        MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_THUMBNAIL,
                        null)));
        assertTrue(MediaAttachmentLogLine.shouldLog(
                MediaAttachmentProcessor.Result.skipped(
                        MediaAttachmentProcessor.AttachmentStatus.WRITE_FAILED,
                        "image",
                        MediaFileSelector.SelectionStatus.BASE_FILE,
                        MediaPayloadWriter.Status.ENCODED_EMPTY)));
    }

    @Test
    public void shouldSkipExpectedNonMediaStates() {
        assertFalse(MediaAttachmentLogLine.shouldLog(null));
        assertFalse(MediaAttachmentLogLine.shouldLog(
                MediaAttachmentProcessor.Result.skipped(
                        MediaAttachmentProcessor.AttachmentStatus.UNSUPPORTED_TYPE,
                        "",
                        null,
                        null)));
        assertFalse(MediaAttachmentLogLine.shouldLog(
                MediaAttachmentProcessor.Result.skipped(
                        MediaAttachmentProcessor.AttachmentStatus.UPLOAD_DISABLED,
                        "image",
                        null,
                        null)));
    }

    @Test
    public void formatUsesOnlySafeStatusFields() {
        String line = MediaAttachmentLogLine.format(
                3,
                123L,
                MediaAttachmentProcessor.Result.skipped(
                        MediaAttachmentProcessor.AttachmentStatus.WRITE_FAILED,
                        "image",
                        MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE,
                        MediaPayloadWriter.Status.READ_FAILED));

        assertEquals(
                "media attachment result type=3 msgId=123 status=WRITE_FAILED kind=image selectionStatus=IMAGE_DOWNLOAD_FILE writerStatus=READ_FAILED",
                line);
        assertFalse(line.contains("wxid"));
        assertFalse(line.contains("base64"));
        assertFalse(line.contains("<msg"));
    }
}
