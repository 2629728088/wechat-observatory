package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentFileSelectionTest {
    @Test
    public void selectReportsSelectorMissingAndLogsReason() {
        List<String> logs = new ArrayList<>();

        MediaAttachmentFileSelection.Result result = new MediaAttachmentFileSelection(null, logs::add)
                .select(request(3));

        assertNull(result.file());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.SELECTOR_MISSING, result.status());
        assertNull(result.selectionStatus());
        assertTrue(contains(logs, "reason=selector_missing"));
    }

    @Test
    public void selectReportsMissingMediaAndKeepsSelectionStatus() {
        List<String> logs = new ArrayList<>();

        MediaAttachmentFileSelection.Result result = new MediaAttachmentFileSelection(
                selector(ImageDownloadResolution.Candidate.missing()),
                logs::add)
                .select(request(34));

        assertNull(result.file());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.MEDIA_NOT_SELECTED, result.status());
        assertEquals(MediaFileSelector.SelectionStatus.NOT_FOUND, result.selectionStatus());
        assertTrue(contains(logs, "selectionStatus=NOT_FOUND"));
    }

    @Test
    public void selectReturnsSelectedFileAndStatus() throws Exception {
        File file = Files.createTempFile("wxo-selection", ".mp4").toFile();
        writeByte(file);

        MediaAttachmentFileSelection.Result result = new MediaAttachmentFileSelection(
                selector(ImageDownloadResolution.Candidate.fromFile(file)),
                message -> {
                })
                .select(request(43));

        assertEquals(file.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.ATTACHED, result.status());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, result.selectionStatus());
    }

    @Test
    public void resultHasFileRequiresExistingFile() throws Exception {
        File root = Files.createTempDirectory("wxo-attachment-selection").toFile();
        File missing = new File(root, "missing.mp4");

        MediaAttachmentFileSelection.Result result = MediaAttachmentFileSelection.Result.selected(
                missing,
                MediaFileSelector.SelectionStatus.BASE_FILE);

        assertFalse(result.hasFile());
    }

    private static MediaFileSelector selector(ImageDownloadResolution.Candidate candidate) {
        return new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> candidate,
                null,
                null,
                null,
                message -> {
                });
    }

    private static MediaAttachmentProcessor.Request request(int type) {
        return new MediaAttachmentProcessor.Request(
                new MessagePayload(),
                type,
                "media",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "",
                56L,
                true,
                2048L);
    }

    private static void writeByte(File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(1);
        }
    }

    private static boolean contains(List<String> values, String expected) {
        for (String value : values) {
            if (value != null && value.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
