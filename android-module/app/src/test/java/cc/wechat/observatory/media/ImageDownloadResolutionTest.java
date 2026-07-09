package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadResolutionTest {
    @Test
    public void evaluateReturnsImageInfoFileFirst() throws Exception {
        File imageInfo = Files.createTempFile("wxo-info", ".jpg").toFile();
        File downloadedFallback = Files.createTempFile("wxo-after", ".jpg").toFile();
        writeJpeg(imageInfo);
        writeJpeg(downloadedFallback);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluate(
                10L,
                20L,
                true,
                imageInfo,
                downloadedFallback);

        assertEquals(imageInfo.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_FILE, resolution.status());
        assertTrue(resolution.hasSelectableFile());
        assertTrue(resolution.isImageInfoSource());
        assertTrue(!resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
        assertTrue(resolution.logMessage().contains("from ImgInfo"));
    }

    @Test
    public void evaluateReportsImageInfoRefTargetSeparately() throws Exception {
        File imageInfo = Files.createTempFile("wxo-info-ref", ".jpg").toFile();
        writeJpeg(imageInfo);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluateCandidates(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.refTarget(imageInfo),
                ImageDownloadResolution.Candidate.missing());

        assertEquals(imageInfo.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET, resolution.status());
        assertTrue(resolution.hasSelectableFile());
        assertTrue(resolution.isImageInfoSource());
        assertTrue(!resolution.isDownloadedFallbackSource());
        assertTrue(resolution.isReferenceTarget());
        assertTrue(resolution.logMessage().contains("ImgInfo ref target"));
    }

    @Test
    public void evaluateCandidateSetUsesSemanticCandidates() throws Exception {
        File downloaded = Files.createTempFile("wxo-downloaded-set", ".jpg").toFile();
        writeJpeg(downloaded);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluateCandidateSet(
                10L,
                20L,
                ImageDownloadCandidateSet.of(
                        true,
                        ImageDownloadResolution.Candidate.missing(),
                        ImageDownloadResolution.Candidate.refTarget(downloaded)));

        assertEquals(downloaded.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET, resolution.status());
        assertTrue(!resolution.isImageInfoSource());
        assertTrue(resolution.isDownloadedFallbackSource());
        assertTrue(resolution.isReferenceTarget());
    }

    @Test
    public void evaluateRejectsLowQualityImageInfoThumbnail() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-thumb-info").toFile();
        File thumbnail = new File(new File(new File(new File(appRoot, "MicroMsg/profile/image2"), "ab"), "cd"), "th_abcd1234");
        writeJpeg(thumbnail);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluate(
                10L,
                20L,
                true,
                thumbnail,
                null);

        assertNull(resolution.file());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL, resolution.status());
        assertTrue(!resolution.hasSelectableFile());
        assertTrue(resolution.isImageInfoSource());
        assertTrue(!resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
        assertTrue(resolution.isThumbnailOnly());
        assertTrue(resolution.logMessage().contains("thumbnail only msgId=10"));
    }

    @Test
    public void evaluatePrefersDownloadedFileOverImageInfoThumbnail() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-thumb-info-with-download").toFile();
        File thumbnail = new File(new File(new File(new File(appRoot, "MicroMsg/profile/image2"), "ab"), "cd"), "th_abcd1234");
        File downloadedFallback = Files.createTempFile("wxo-after-thumbnail", ".jpg").toFile();
        writeJpeg(thumbnail);
        writeJpeg(downloadedFallback);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluate(
                10L,
                20L,
                true,
                thumbnail,
                downloadedFallback);

        assertEquals(downloadedFallback.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, resolution.status());
        assertTrue(!resolution.isImageInfoSource());
        assertTrue(resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
        assertTrue(resolution.logMessage().contains("after NetSceneGetMsgImg"));
    }

    @Test
    public void evaluateReportsUnsupportedImageInfoCandidate() throws Exception {
        File unsupported = Files.createTempFile("wxo-info-unsupported", ".ref").toFile();

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluateCandidates(
                10L,
                20L,
                false,
                ImageDownloadResolution.Candidate.unsupported(unsupported),
                ImageDownloadResolution.Candidate.missing());

        assertNull(resolution.file());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_UNSUPPORTED, resolution.status());
        assertTrue(!resolution.hasSelectableFile());
        assertTrue(resolution.isImageInfoSource());
        assertTrue(!resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
        assertTrue(resolution.isUnsupported());
        assertTrue(resolution.logMessage().contains("unsupported from ImgInfo"));
    }

    @Test
    public void evaluatePrefersDownloadedFileOverUnsupportedImageInfo() throws Exception {
        File unsupported = Files.createTempFile("wxo-info-unsupported-with-download", ".ref").toFile();
        File downloadedFallback = Files.createTempFile("wxo-after-unsupported", ".jpg").toFile();
        writeJpeg(downloadedFallback);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluateCandidates(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.unsupported(unsupported),
                ImageDownloadResolution.Candidate.fromFile(downloadedFallback));

        assertEquals(downloadedFallback.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, resolution.status());
        assertTrue(!resolution.isImageInfoSource());
        assertTrue(resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
    }

    @Test
    public void evaluateReturnsDownloadedFileWhenInfoMissing() throws Exception {
        File downloadedFallback = Files.createTempFile("wxo-downloaded", ".jpg").toFile();
        writeJpeg(downloadedFallback);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluate(
                10L,
                20L,
                true,
                null,
                downloadedFallback);

        assertEquals(downloadedFallback.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, resolution.status());
        assertTrue(!resolution.isImageInfoSource());
        assertTrue(resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
        assertTrue(resolution.logMessage().contains("after NetSceneGetMsgImg"));
    }

    @Test
    public void evaluateReportsDownloadedRefTargetSeparately() throws Exception {
        File downloadedFallback = Files.createTempFile("wxo-downloaded-ref", ".jpg").toFile();
        writeJpeg(downloadedFallback);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluateCandidates(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.missing(),
                ImageDownloadResolution.Candidate.refTarget(downloadedFallback));

        assertEquals(downloadedFallback.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET, resolution.status());
        assertTrue(!resolution.isImageInfoSource());
        assertTrue(resolution.isDownloadedFallbackSource());
        assertTrue(resolution.isReferenceTarget());
        assertTrue(resolution.logMessage().contains("ref target after NetSceneGetMsgImg"));
    }

    @Test
    public void evaluateReportsDownloadedThumbnailSeparately() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-thumb-downloaded").toFile();
        File thumbnail = new File(new File(new File(new File(appRoot, "MicroMsg/profile/image2"), "ab"), "cd"), "th_abcd1234");
        writeJpeg(thumbnail);

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluate(
                10L,
                20L,
                true,
                null,
                thumbnail);

        assertNull(resolution.file());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL, resolution.status());
        assertTrue(!resolution.hasSelectableFile());
        assertTrue(!resolution.isImageInfoSource());
        assertTrue(resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
        assertTrue(resolution.isThumbnailOnly());
        assertTrue(resolution.logMessage().contains("thumbnail only after NetSceneGetMsgImg"));
    }

    @Test
    public void evaluateReportsDownloadedUnsupportedSeparately() throws Exception {
        File unsupported = Files.createTempFile("wxo-downloaded-unsupported", ".ref").toFile();

        ImageDownloadResolution resolution = ImageDownloadResolution.evaluateCandidates(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.missing(),
                ImageDownloadResolution.Candidate.unsupported(unsupported));

        assertNull(resolution.file());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_UNSUPPORTED, resolution.status());
        assertTrue(!resolution.hasSelectableFile());
        assertTrue(!resolution.isImageInfoSource());
        assertTrue(resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
        assertTrue(resolution.isUnsupported());
        assertTrue(resolution.logMessage().contains("unsupported after NetSceneGetMsgImg"));
    }

    @Test
    public void evaluateReportsRetryWhenNoFileIsReady() {
        ImageDownloadResolution resolution = ImageDownloadResolution.evaluate(
                10L,
                20L,
                false,
                null,
                null);

        assertNull(resolution.file());
        assertEquals(ImageDownloadResolution.Status.NOT_READY, resolution.status());
        assertTrue(!resolution.hasSelectableFile());
        assertTrue(!resolution.isImageInfoSource());
        assertTrue(!resolution.isDownloadedFallbackSource());
        assertTrue(!resolution.isReferenceTarget());
        assertTrue(!resolution.isThumbnailOnly());
        assertTrue(!resolution.isUnsupported());
        assertTrue(resolution.logMessage().contains("image local retry"));
        assertTrue(resolution.logMessage().contains("msgSvrId=20"));
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
