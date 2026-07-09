package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadResolutionDecisionTest {
    @Test
    public void immediateImageInfoCandidateAcceptsImageAndRefTargetOnly() throws Exception {
        File image = Files.createTempFile("wxo-decision-info", ".jpg").toFile();
        File refTarget = Files.createTempFile("wxo-decision-ref", ".jpg").toFile();
        writeJpeg(image);
        writeJpeg(refTarget);

        ImageDownloadResolution imageResolution =
                ImageDownloadResolutionDecision.fromImmediateImageInfoCandidate(
                        10L,
                        ImageDownloadResolution.Candidate.fromFile(image));
        ImageDownloadResolution refResolution =
                ImageDownloadResolutionDecision.fromImmediateImageInfoCandidate(
                        11L,
                        ImageDownloadResolution.Candidate.refTarget(refTarget));

        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_FILE, imageResolution.status());
        assertEquals(image.getCanonicalFile(), imageResolution.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET, refResolution.status());
        assertEquals(refTarget.getCanonicalFile(), refResolution.file().getCanonicalFile());
    }

    @Test
    public void immediateImageInfoCandidateDefersThumbnailAndUnsupportedCandidate() throws Exception {
        File thumbnail = thumbnailFile();
        File unsupported = Files.createTempFile("wxo-decision-info-unsupported", ".ref").toFile();
        writeJpeg(thumbnail);

        assertNull(ImageDownloadResolutionDecision.fromImmediateImageInfoCandidate(
                10L,
                ImageDownloadResolution.Candidate.fromFile(thumbnail)));
        assertNull(ImageDownloadResolutionDecision.fromImmediateImageInfoCandidate(
                10L,
                ImageDownloadResolution.Candidate.unsupported(unsupported)));
    }

    @Test
    public void downloadedFallbackCandidateReturnsDownloadedAndTerminalStatuses() throws Exception {
        File image = Files.createTempFile("wxo-decision-downloaded", ".jpg").toFile();
        File refTarget = Files.createTempFile("wxo-decision-downloaded-ref", ".jpg").toFile();
        File thumbnail = thumbnailFile();
        File unsupported = Files.createTempFile("wxo-decision-downloaded-unsupported", ".ref").toFile();
        writeJpeg(image);
        writeJpeg(refTarget);
        writeJpeg(thumbnail);

        assertEquals(
                ImageDownloadResolution.Status.DOWNLOADED_FILE,
                ImageDownloadResolutionDecision.fromDownloadedFallbackCandidate(
                        10L,
                        ImageDownloadResolution.Candidate.fromFile(image)).status());
        assertEquals(
                ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET,
                ImageDownloadResolutionDecision.fromDownloadedFallbackCandidate(
                        10L,
                        ImageDownloadResolution.Candidate.refTarget(refTarget)).status());
        assertEquals(
                ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL,
                ImageDownloadResolutionDecision.fromDownloadedFallbackCandidate(
                        10L,
                        ImageDownloadResolution.Candidate.fromFile(thumbnail)).status());
        assertEquals(
                ImageDownloadResolution.Status.DOWNLOADED_UNSUPPORTED,
                ImageDownloadResolutionDecision.fromDownloadedFallbackCandidate(
                        10L,
                        ImageDownloadResolution.Candidate.unsupported(unsupported)).status());
    }

    @Test
    public void deferredImageInfoCandidateReturnsOnlyThumbnailAndUnsupportedStatuses() throws Exception {
        File image = Files.createTempFile("wxo-decision-deferred-image", ".jpg").toFile();
        File thumbnail = thumbnailFile();
        File unsupported = Files.createTempFile("wxo-decision-deferred-unsupported", ".ref").toFile();
        writeJpeg(image);
        writeJpeg(thumbnail);

        assertNull(ImageDownloadResolutionDecision.fromDeferredImageInfoCandidate(
                10L,
                ImageDownloadResolution.Candidate.fromFile(image)));
        assertEquals(
                ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL,
                ImageDownloadResolutionDecision.fromDeferredImageInfoCandidate(
                        10L,
                        ImageDownloadResolution.Candidate.fromFile(thumbnail)).status());
        assertEquals(
                ImageDownloadResolution.Status.IMAGE_INFO_UNSUPPORTED,
                ImageDownloadResolutionDecision.fromDeferredImageInfoCandidate(
                        10L,
                        ImageDownloadResolution.Candidate.unsupported(unsupported)).status());
    }

    @Test
    public void missingCandidatesReturnNull() {
        ImageDownloadResolution.Candidate missing = ImageDownloadResolution.Candidate.missing();

        assertNull(ImageDownloadResolutionDecision.fromImmediateImageInfoCandidate(10L, missing));
        assertNull(ImageDownloadResolutionDecision.fromDownloadedFallbackCandidate(10L, missing));
        assertNull(ImageDownloadResolutionDecision.fromDeferredImageInfoCandidate(10L, missing));
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-decision-thumb").toFile(), "image2");
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
