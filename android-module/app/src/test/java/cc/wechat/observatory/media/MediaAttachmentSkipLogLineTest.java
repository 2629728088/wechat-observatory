package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MediaAttachmentSkipLogLineTest {
    @Test
    public void selectorMissingKeepsExistingFormat() {
        assertEquals(
                "media attachment skipped type=3 msgId=123 reason=selector_missing",
                MediaAttachmentSkipLogLine.selectorMissing(3, 123L));
    }

    @Test
    public void mediaNotSelectedKeepsSelectionStatusFormat() {
        MediaFileSelector.Selection selection = MediaFileSelector.Selection.of(
                MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_THUMBNAIL,
                null);

        assertEquals(
                "media attachment skipped type=3 msgId=123 selectionStatus=IMAGE_DOWNLOAD_THUMBNAIL",
                MediaAttachmentSkipLogLine.mediaNotSelected(3, 123L, selection));
    }

    @Test
    public void mediaNotSelectedKeepsNullSelectionFormat() {
        assertEquals(
                "media attachment skipped type=3 msgId=123 selectionStatus=null",
                MediaAttachmentSkipLogLine.mediaNotSelected(3, 123L, null));
    }

    @Test
    public void writeFailedKeepsWriterStatusFormat() {
        assertEquals(
                "media attachment skipped type=3 msgId=123 selectionStatus=BASE_FILE writerStatus=ENCODED_EMPTY",
                MediaAttachmentSkipLogLine.writeFailed(
                        3,
                        123L,
                        MediaFileSelector.SelectionStatus.BASE_FILE,
                        MediaPayloadWriter.Status.ENCODED_EMPTY));
    }
}
