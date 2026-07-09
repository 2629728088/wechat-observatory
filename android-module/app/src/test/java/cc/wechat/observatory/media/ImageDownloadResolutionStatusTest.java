package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadResolutionStatusTest {
    @Test
    public void selectableFileStatusesRequireExistingFiles() throws Exception {
        File image = Files.createTempFile("wxo-resolution-status", ".jpg").toFile();

        assertTrue(ImageDownloadResolutionStatus.hasSelectableFile(
                ImageDownloadResolution.Status.IMAGE_INFO_FILE,
                image));
        assertTrue(ImageDownloadResolutionStatus.hasSelectableFile(
                ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET,
                image));
        assertFalse(ImageDownloadResolutionStatus.hasSelectableFile(
                ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL,
                image));
        assertFalse(ImageDownloadResolutionStatus.hasSelectableFile(
                ImageDownloadResolution.Status.DOWNLOADED_FILE,
                new File(image.getParentFile(), "missing.jpg")));
    }

    @Test
    public void statusCategoriesStayDistinct() {
        assertTrue(ImageDownloadResolutionStatus.isImageInfoSource(
                ImageDownloadResolution.Status.IMAGE_INFO_UNSUPPORTED));
        assertFalse(ImageDownloadResolutionStatus.isImageInfoSource(
                ImageDownloadResolution.Status.DOWNLOADED_UNSUPPORTED));
        assertTrue(ImageDownloadResolutionStatus.isDownloadedFallbackSource(
                ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL));
        assertFalse(ImageDownloadResolutionStatus.isDownloadedFallbackSource(
                ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL));
        assertTrue(ImageDownloadResolutionStatus.isReferenceTarget(
                ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET));
        assertTrue(ImageDownloadResolutionStatus.isThumbnailOnly(
                ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL));
        assertTrue(ImageDownloadResolutionStatus.isUnsupported(
                ImageDownloadResolution.Status.IMAGE_INFO_UNSUPPORTED));
        assertFalse(ImageDownloadResolutionStatus.isUnsupported(
                ImageDownloadResolution.Status.NOT_READY));
    }
}
