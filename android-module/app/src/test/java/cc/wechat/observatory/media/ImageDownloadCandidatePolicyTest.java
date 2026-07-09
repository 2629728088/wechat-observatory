package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadCandidatePolicyTest {
    @Test
    public void imageFileAndRefTargetAreImmediateImageInfoCandidates() throws Exception {
        File image = Files.createTempFile("wxo-policy-image", ".jpg").toFile();
        File refTarget = Files.createTempFile("wxo-policy-ref", ".jpg").toFile();
        writeJpeg(image);
        writeJpeg(refTarget);

        assertTrue(ImageDownloadCandidatePolicy.isImmediateImageInfoCandidate(
                ImageDownloadResolution.Candidate.fromFile(image)));
        assertTrue(ImageDownloadCandidatePolicy.isImmediateImageInfoCandidate(
                ImageDownloadResolution.Candidate.refTarget(refTarget)));
    }

    @Test
    public void thumbnailAndUnsupportedAreDeferredImageInfoCandidates() throws Exception {
        File thumbnail = thumbnailFile();
        File unsupported = Files.createTempFile("wxo-policy-unsupported", ".ref").toFile();
        writeJpeg(thumbnail);
        writeText(unsupported, "not-image");

        assertTrue(ImageDownloadCandidatePolicy.isDeferredImageInfoCandidate(
                ImageDownloadResolution.Candidate.fromFile(thumbnail)));
        assertTrue(ImageDownloadCandidatePolicy.isDeferredImageInfoCandidate(
                ImageDownloadResolution.Candidate.unsupported(unsupported)));
        assertFalse(ImageDownloadCandidatePolicy.isImmediateImageInfoCandidate(
                ImageDownloadResolution.Candidate.fromFile(thumbnail)));
        assertFalse(ImageDownloadCandidatePolicy.isImmediateImageInfoCandidate(
                ImageDownloadResolution.Candidate.unsupported(unsupported)));
    }

    @Test
    public void missingCandidateIsNotUsable() {
        ImageDownloadResolution.Candidate missing = ImageDownloadResolution.Candidate.missing();

        assertFalse(ImageDownloadCandidatePolicy.isUsable(missing));
        assertFalse(ImageDownloadCandidatePolicy.isImmediateImageInfoCandidate(missing));
        assertFalse(ImageDownloadCandidatePolicy.isDeferredImageInfoCandidate(missing));
    }

    @Test
    public void downloadedFallbackIsSkippedOnlyForImmediateImageInfoCandidates() throws Exception {
        File image = Files.createTempFile("wxo-policy-fallback-image", ".jpg").toFile();
        File thumbnail = thumbnailFile();
        File unsupported = Files.createTempFile("wxo-policy-fallback-unsupported", ".ref").toFile();
        writeJpeg(image);
        writeJpeg(thumbnail);
        writeText(unsupported, "not-image");

        assertFalse(ImageDownloadCandidatePolicy.shouldResolveDownloadedFallback(
                ImageDownloadResolution.Candidate.fromFile(image)));
        assertTrue(ImageDownloadCandidatePolicy.shouldResolveDownloadedFallback(
                ImageDownloadResolution.Candidate.fromFile(thumbnail)));
        assertTrue(ImageDownloadCandidatePolicy.shouldResolveDownloadedFallback(
                ImageDownloadResolution.Candidate.unsupported(unsupported)));
        assertTrue(ImageDownloadCandidatePolicy.shouldResolveDownloadedFallback(
                ImageDownloadResolution.Candidate.missing()));
        assertTrue(ImageDownloadCandidatePolicy.shouldResolveDownloadedFallback(null));
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-policy-thumb").toFile(), "image2");
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

    private static void writeText(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes("UTF-8"));
        }
    }
}
