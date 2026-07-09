package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadResolutionEvaluatorTest {
    @Test
    public void evaluateFilesWrapsRawFilesAsSemanticCandidates() throws Exception {
        File imageInfo = Files.createTempFile("wxo-evaluator-info", ".jpg").toFile();
        File downloaded = Files.createTempFile("wxo-evaluator-downloaded", ".jpg").toFile();
        writeJpeg(imageInfo);
        writeJpeg(downloaded);

        ImageDownloadResolution result = ImageDownloadResolutionEvaluator.evaluateFiles(
                10L,
                20L,
                true,
                imageInfo,
                downloaded);

        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_FILE, result.status());
        assertEquals(imageInfo.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void evaluateCandidatesKeepsDownloadedRefTargetSemantics() throws Exception {
        File refTarget = Files.createTempFile("wxo-evaluator-ref", ".jpg").toFile();
        writeJpeg(refTarget);

        ImageDownloadResolution result = ImageDownloadResolutionEvaluator.evaluateCandidates(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.missing(),
                ImageDownloadResolution.Candidate.refTarget(refTarget));

        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET, result.status());
        assertEquals(refTarget.getCanonicalFile(), result.file().getCanonicalFile());
        assertTrue(result.isReferenceTarget());
    }

    @Test
    public void evaluateCandidateSetTreatsNullAsMissing() {
        ImageDownloadResolution result = ImageDownloadResolutionEvaluator.evaluateCandidateSet(
                10L,
                20L,
                null);

        assertEquals(ImageDownloadResolution.Status.NOT_READY, result.status());
        assertTrue(result.logMessage().contains("image local retry"));
        assertTrue(result.logMessage().contains("msgSvrId=20"));
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
}
