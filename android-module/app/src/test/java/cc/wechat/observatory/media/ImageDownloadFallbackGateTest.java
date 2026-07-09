package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadFallbackGateTest {
    @Test
    public void immediateImageInfoFileAndRefTargetSkipFallback() throws Exception {
        File image = Files.createTempFile("wxo-fallback-gate-image", ".jpg").toFile();
        File refTarget = Files.createTempFile("wxo-fallback-gate-ref", ".jpg").toFile();
        writeJpeg(image);
        writeJpeg(refTarget);

        assertFalse(ImageDownloadFallbackGate.shouldResolveFallback(
                ImageDownloadResolution.Candidate.fromFile(image)));
        assertFalse(ImageDownloadFallbackGate.shouldResolveFallback(
                ImageDownloadResolution.Candidate.refTarget(refTarget)));
    }

    @Test
    public void thumbnailUnsupportedMissingAndNullAllowFallback() throws Exception {
        File thumbnail = thumbnailFile();
        File unsupported = Files.createTempFile("wxo-fallback-gate-unsupported", ".ref").toFile();
        writeJpeg(thumbnail);

        assertTrue(ImageDownloadFallbackGate.shouldResolveFallback(
                ImageDownloadResolution.Candidate.fromFile(thumbnail)));
        assertTrue(ImageDownloadFallbackGate.shouldResolveFallback(
                ImageDownloadResolution.Candidate.unsupported(unsupported)));
        assertTrue(ImageDownloadFallbackGate.shouldResolveFallback(
                ImageDownloadResolution.Candidate.missing()));
        assertTrue(ImageDownloadFallbackGate.shouldResolveFallback(null));
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-fallback-gate-thumb").toFile(), "image2");
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
