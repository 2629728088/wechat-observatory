package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadResolutionMapperTest {
    @Test
    public void imageInfoMapsCandidateKindsToImageInfoStatuses() throws Exception {
        File image = Files.createTempFile("wxo-mapper-info-image", ".jpg").toFile();
        File refTarget = Files.createTempFile("wxo-mapper-info-ref", ".jpg").toFile();
        File thumbnail = thumbnailFile("wxo-mapper-info-thumb");
        File unsupported = Files.createTempFile("wxo-mapper-info-unsupported", ".ref").toFile();
        writeJpeg(image);
        writeJpeg(refTarget);
        writeJpeg(thumbnail);
        writeText(unsupported, "not-image");

        ImageDownloadResolution imageResult = ImageDownloadResolutionMapper.fromImageInfo(
                41L,
                ImageDownloadResolution.Candidate.fromFile(image));
        ImageDownloadResolution refResult = ImageDownloadResolutionMapper.fromImageInfo(
                42L,
                ImageDownloadResolution.Candidate.refTarget(refTarget));
        ImageDownloadResolution thumbResult = ImageDownloadResolutionMapper.fromImageInfo(
                43L,
                ImageDownloadResolution.Candidate.fromFile(thumbnail));
        ImageDownloadResolution unsupportedResult = ImageDownloadResolutionMapper.fromImageInfo(
                44L,
                ImageDownloadResolution.Candidate.unsupported(unsupported));

        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_FILE, imageResult.status());
        assertEquals(image.getCanonicalFile(), imageResult.file().getCanonicalFile());
        assertTrue(imageResult.logMessage().contains("from ImgInfo"));
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET, refResult.status());
        assertEquals(refTarget.getCanonicalFile(), refResult.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL, thumbResult.status());
        assertNull(thumbResult.file());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_UNSUPPORTED, unsupportedResult.status());
        assertNull(unsupportedResult.file());
    }

    @Test
    public void downloadedFallbackMapsCandidateKindsToDownloadedStatuses() throws Exception {
        File image = Files.createTempFile("wxo-mapper-down-image", ".jpg").toFile();
        File refTarget = Files.createTempFile("wxo-mapper-down-ref", ".jpg").toFile();
        File thumbnail = thumbnailFile("wxo-mapper-down-thumb");
        File unsupported = Files.createTempFile("wxo-mapper-down-unsupported", ".ref").toFile();
        writeJpeg(image);
        writeJpeg(refTarget);
        writeJpeg(thumbnail);
        writeText(unsupported, "not-image");

        ImageDownloadResolution imageResult = ImageDownloadResolutionMapper.fromDownloadedFallback(
                45L,
                ImageDownloadResolution.Candidate.fromFile(image));
        ImageDownloadResolution refResult = ImageDownloadResolutionMapper.fromDownloadedFallback(
                46L,
                ImageDownloadResolution.Candidate.refTarget(refTarget));
        ImageDownloadResolution thumbResult = ImageDownloadResolutionMapper.fromDownloadedFallback(
                47L,
                ImageDownloadResolution.Candidate.fromFile(thumbnail));
        ImageDownloadResolution unsupportedResult = ImageDownloadResolutionMapper.fromDownloadedFallback(
                48L,
                ImageDownloadResolution.Candidate.unsupported(unsupported));

        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, imageResult.status());
        assertEquals(image.getCanonicalFile(), imageResult.file().getCanonicalFile());
        assertTrue(imageResult.logMessage().contains("after NetSceneGetMsgImg"));
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET, refResult.status());
        assertEquals(refTarget.getCanonicalFile(), refResult.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL, thumbResult.status());
        assertNull(thumbResult.file());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_UNSUPPORTED, unsupportedResult.status());
        assertNull(unsupportedResult.file());
    }

    @Test
    public void missingCandidateDoesNotMapToAResolution() {
        assertNull(ImageDownloadResolutionMapper.fromImageInfo(
                49L,
                ImageDownloadResolution.Candidate.missing()));
        assertNull(ImageDownloadResolutionMapper.fromDownloadedFallback(
                50L,
                ImageDownloadResolution.Candidate.missing()));
    }

    private static File thumbnailFile(String prefix) throws Exception {
        File image2 = new File(Files.createTempDirectory(prefix).toFile(), "image2");
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
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
