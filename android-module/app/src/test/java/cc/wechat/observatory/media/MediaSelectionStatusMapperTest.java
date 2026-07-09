package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaSelectionStatusMapperTest {
    @Test
    public void baseStatusKeepsRefAndUnsupportedDistinct() throws Exception {
        File image = Files.createTempFile("wxo-base-ref", ".jpg").toFile();
        writeJpeg(image);
        File unsupported = Files.createTempFile("wxo-base-unsupported", ".ref").toFile();

        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE,
                MediaSelectionStatusMapper.baseStatus(ImageDownloadResolution.Candidate.fromFile(image)));
        assertEquals(MediaFileSelector.SelectionStatus.BASE_REF_TARGET,
                MediaSelectionStatusMapper.baseStatus(ImageDownloadResolution.Candidate.refTarget(image)));
        assertEquals(MediaFileSelector.SelectionStatus.BASE_UNSUPPORTED,
                MediaSelectionStatusMapper.baseStatus(ImageDownloadResolution.Candidate.unsupported(unsupported)));
    }

    @Test
    public void imageDownloadFileStatusKeepsInfoAndDownloadedRefDistinct() throws Exception {
        File image = Files.createTempFile("wxo-info-image", ".jpg").toFile();
        writeJpeg(image);

        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_INFO_FILE,
                MediaSelectionStatusMapper.imageDownloadFileStatus(resolution(
                        ImageDownloadResolution.Candidate.fromFile(image),
                        ImageDownloadResolution.Candidate.missing(),
                        false)));
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_INFO_REF_TARGET,
                MediaSelectionStatusMapper.imageDownloadFileStatus(resolution(
                        ImageDownloadResolution.Candidate.refTarget(image),
                        ImageDownloadResolution.Candidate.missing(),
                        false)));
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_REF_TARGET,
                MediaSelectionStatusMapper.imageDownloadFileStatus(resolution(
                        ImageDownloadResolution.Candidate.missing(),
                        ImageDownloadResolution.Candidate.refTarget(image),
                        true)));
    }

    @Test
    public void imageDownloadNonFileStatusesStayStructured() throws Exception {
        File thumbnail = lowQualityThumbnailFile();
        writeJpeg(thumbnail);
        File unsupported = Files.createTempFile("wxo-unsupported", ".ref").toFile();

        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_INFO_THUMBNAIL,
                MediaSelectionStatusMapper.imageDownloadThumbnailStatus(resolution(
                        ImageDownloadResolution.Candidate.fromFile(thumbnail),
                        ImageDownloadResolution.Candidate.missing(),
                        false)));
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_THUMBNAIL,
                MediaSelectionStatusMapper.imageDownloadThumbnailStatus(resolution(
                        ImageDownloadResolution.Candidate.missing(),
                        ImageDownloadResolution.Candidate.fromFile(thumbnail),
                        true)));
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_INFO_UNSUPPORTED,
                MediaSelectionStatusMapper.imageDownloadUnsupportedStatus(resolution(
                        ImageDownloadResolution.Candidate.unsupported(unsupported),
                        ImageDownloadResolution.Candidate.missing(),
                        false)));
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_UNSUPPORTED,
                MediaSelectionStatusMapper.imageDownloadUnsupportedStatus(resolution(
                        ImageDownloadResolution.Candidate.missing(),
                        ImageDownloadResolution.Candidate.unsupported(unsupported),
                        true)));
        assertNull(MediaSelectionStatusMapper.imageDownloadUnsupportedStatus(null));
    }

    @Test
    public void selectableImageDownloadFileRequiresFileStatusAndExistingFile() throws Exception {
        File image = Files.createTempFile("wxo-selectable-image", ".jpg").toFile();
        writeJpeg(image);

        assertTrue(MediaSelectionStatusMapper.hasSelectableImageDownloadFile(new ImageDownloadResolution(
                ImageDownloadResolution.Status.DOWNLOADED_FILE,
                image,
                "")));
        assertTrue(MediaSelectionStatusMapper.hasSelectableImageDownloadFile(new ImageDownloadResolution(
                ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET,
                image,
                "")));
        assertFalse(MediaSelectionStatusMapper.hasSelectableImageDownloadFile(new ImageDownloadResolution(
                ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL,
                image,
                "")));
        assertFalse(MediaSelectionStatusMapper.hasSelectableImageDownloadFile(new ImageDownloadResolution(
                ImageDownloadResolution.Status.NOT_READY,
                image,
                "")));
        assertFalse(MediaSelectionStatusMapper.hasSelectableImageDownloadFile(new ImageDownloadResolution(
                ImageDownloadResolution.Status.DOWNLOADED_FILE,
                new File(image.getParentFile(), "missing.jpg"),
                "")));
    }

    @Test
    public void candidatePredicatesExposeSelectorInputs() throws Exception {
        File image = Files.createTempFile("wxo-candidate", ".jpg").toFile();
        writeJpeg(image);
        File thumbnail = lowQualityThumbnailFile();
        writeJpeg(thumbnail);

        ImageDownloadResolution.Candidate candidate = ImageDownloadResolution.Candidate.fromFile(image);
        ImageDownloadResolution.Candidate thumbnailCandidate = ImageDownloadResolution.Candidate.fromFile(thumbnail);

        assertEquals(image.getCanonicalFile(), MediaSelectionStatusMapper.candidateFile(candidate).getCanonicalFile());
        assertTrue(MediaSelectionStatusMapper.isThumbnailCandidate(thumbnailCandidate));
    }

    private static ImageDownloadResolution resolution(
            ImageDownloadResolution.Candidate imageInfoCandidate,
            ImageDownloadResolution.Candidate downloadedFallbackCandidate,
            boolean requested) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                requested,
                imageInfoCandidate,
                downloadedFallbackCandidate);
    }

    private static File lowQualityThumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-thumb").toFile(), "image2");
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
