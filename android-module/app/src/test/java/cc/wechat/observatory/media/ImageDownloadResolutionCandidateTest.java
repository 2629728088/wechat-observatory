package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadResolutionCandidateTest {
    @Test
    public void imageFileCandidateExposesSemanticState() throws Exception {
        File image = Files.createTempFile("wxo-candidate-image", ".jpg").toFile();
        writeJpeg(image);

        ImageDownloadResolution.Candidate candidate = ImageDownloadResolution.Candidate.fromFile(image);

        assertFalse(candidate.isMissing());
        assertFalse(candidate.isReferenceTarget());
        assertFalse(candidate.isLowQualityThumbnail());
        assertFalse(candidate.isUnsupported());
        assertTrue(candidate.hasExistingFile());
        assertEquals(image.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void refTargetThumbnailAndUnsupportedStayDistinct() throws Exception {
        File refTarget = Files.createTempFile("wxo-candidate-ref", ".jpg").toFile();
        File thumbnail = lowQualityThumbnailFile();
        File unsupported = Files.createTempFile("wxo-candidate-unsupported", ".ref").toFile();
        writeJpeg(refTarget);
        writeJpeg(thumbnail);

        assertTrue(ImageDownloadResolution.Candidate.refTarget(refTarget).isReferenceTarget());
        assertTrue(ImageDownloadResolution.Candidate.fromFile(thumbnail).isLowQualityThumbnail());
        assertTrue(ImageDownloadResolution.Candidate.unsupported(unsupported).isUnsupported());
    }

    @Test
    public void missingCandidateHasNoExistingFile() {
        ImageDownloadResolution.Candidate candidate = ImageDownloadResolution.Candidate.missing();

        assertTrue(candidate.isMissing());
        assertFalse(candidate.hasExistingFile());
        assertNull(candidate.file());
    }

    private static File lowQualityThumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-candidate-thumb").toFile(), "image2");
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
}
