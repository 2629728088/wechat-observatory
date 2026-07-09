package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadSelectionTest {
    @Test
    public void downloadedFileBecomesDownloadFileSelection() throws Exception {
        File image = Files.createTempFile("wxo-download-selection", ".jpg").toFile();
        writeJpeg(image);

        MediaFileSelector.Selection selection = WechatImageDownloadSelection.from(
                request(),
                downloaded(ImageDownloadResolution.Candidate.fromFile(image)),
                message -> {
                });

        assertEquals(image.getCanonicalFile(), selection.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE, selection.status());
    }

    @Test
    public void downloadedRefTargetKeepsRefSelectionStatus() throws Exception {
        File image = Files.createTempFile("wxo-download-selection-ref", ".jpg").toFile();
        writeJpeg(image);

        MediaFileSelector.Selection selection = WechatImageDownloadSelection.from(
                request(),
                downloaded(ImageDownloadResolution.Candidate.refTarget(image)),
                message -> {
                });

        assertEquals(image.getCanonicalFile(), selection.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_REF_TARGET, selection.status());
    }

    @Test
    public void thumbnailSelectionHasNoFileAndLogsStatus() throws Exception {
        File thumbnail = thumbnailFile();
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selection = WechatImageDownloadSelection.from(
                request(),
                downloaded(ImageDownloadResolution.Candidate.fromFile(thumbnail)),
                logs::add);

        assertNull(selection.file());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_THUMBNAIL, selection.status());
        assertTrue(contains(logs, "skip thumbnail image upload"));
        assertTrue(contains(logs, "status=DOWNLOADED_THUMBNAIL"));
    }

    @Test
    public void thumbnailStatusWithFileIsStillSkipped() throws Exception {
        File thumbnail = thumbnailFile();
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selection = WechatImageDownloadSelection.from(
                request(),
                new ImageDownloadResolution(
                        ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL,
                        thumbnail,
                        ""),
                logs::add);

        assertNull(selection.file());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_THUMBNAIL, selection.status());
        assertTrue(contains(logs, "skip thumbnail image upload"));
    }

    @Test
    public void unsupportedSelectionHasNoFileAndLogsStatus() throws Exception {
        File unsupported = Files.createTempFile("wxo-download-selection-unsupported", ".ref").toFile();
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selection = WechatImageDownloadSelection.from(
                request(),
                downloaded(ImageDownloadResolution.Candidate.unsupported(unsupported)),
                logs::add);

        assertNull(selection.file());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_UNSUPPORTED, selection.status());
        assertTrue(contains(logs, "skip unsupported image upload"));
        assertTrue(contains(logs, "status=DOWNLOADED_UNSUPPORTED"));
    }

    @Test
    public void notReadyResolutionReturnsNullSelection() {
        MediaFileSelector.Selection selection = WechatImageDownloadSelection.from(
                request(),
                ImageDownloadResolution.evaluateCandidates(
                        12L,
                        34L,
                        false,
                        ImageDownloadResolution.Candidate.missing(),
                        ImageDownloadResolution.Candidate.missing()),
                message -> {
                });

        assertNull(selection);
    }

    private static MediaFileSelector.Request request() {
        return new MediaFileSelector.Request(
                3,
                "media.jpg",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "",
                12L);
    }

    private static ImageDownloadResolution downloaded(
            ImageDownloadResolution.Candidate downloadedFallbackCandidate) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                true,
                ImageDownloadResolution.Candidate.missing(),
                downloadedFallbackCandidate);
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-download-selection-thumb").toFile(), "image2");
        return new File(image2, "th_abcd1234");
    }

    private static void writeJpeg(File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        byte[] bytes = new byte[512];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xd8;
        bytes[2] = (byte) 0xff;
        bytes[3] = (byte) 0xe0;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
    }

    private static boolean contains(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
