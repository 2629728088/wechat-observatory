package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadCandidateSourceTest {
    @Test
    public void mediaResultImageFileUsesSameSourceAndFile() throws Exception {
        File image = Files.createTempFile("wxo-candidate-source-image", ".jpg").toFile();
        writeJpeg(image);
        MediaResolver.Result result = MediaResolver.Result.found(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_FILE,
                image,
                image);

        ImageDownloadCandidateSource source = ImageDownloadCandidateSource.fromMediaResult(result);

        assertTrue(source.isImageFile());
        assertEquals(image.getCanonicalFile(), source.source().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), source.file().getCanonicalFile());
    }

    @Test
    public void mediaResultRefTargetKeepsSourceAndTargetFileDistinct() throws Exception {
        File pointer = Files.createTempFile("wxo-candidate-source-ref", ".ref").toFile();
        File target = Files.createTempFile("wxo-candidate-source-target", ".jpg").toFile();
        writeJpeg(target);
        MediaResolver.Result result = MediaResolver.Result.found(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_REF_TARGET,
                target,
                pointer);

        ImageDownloadCandidateSource source = ImageDownloadCandidateSource.fromMediaResult(result);

        assertTrue(source.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), source.source().getCanonicalFile());
        assertEquals(target.getCanonicalFile(), source.file().getCanonicalFile());
    }

    @Test
    public void mediaResultRefTargetSourceKeepsTargetFile() throws Exception {
        File pointer = Files.createTempFile("wxo-candidate-source-ref-candidate", ".ref").toFile();
        File target = Files.createTempFile("wxo-candidate-source-target-candidate", ".jpg").toFile();
        writeJpeg(target);
        MediaResolver.Result result = MediaResolver.Result.found(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_REF_TARGET,
                target,
                pointer);

        ImageDownloadCandidateSource source = ImageDownloadCandidateSource.fromMediaResult(result);

        assertTrue(source.isReferenceTarget());
        assertEquals(target.getCanonicalFile(), source.file().getCanonicalFile());
    }

    @Test
    public void imageInfoThumbnailKeepsSourceWithoutFile() throws Exception {
        File thumbnail = Files.createTempFile("wxo-candidate-source-thumb", ".jpg").toFile();
        writeJpeg(thumbnail);
        MediaResolver.ImageInfoResult result = MediaResolver.ImageInfoResult.of(
                MediaResolver.ImageInfoStatus.DIRECT_IMAGE_THUMBNAIL,
                null,
                thumbnail,
                12L,
                null,
                null);

        ImageDownloadCandidateSource source = ImageDownloadCandidateSource.fromImageInfoResult(result);

        assertTrue(source.isThumbnail());
        assertEquals(thumbnail.getCanonicalFile(), source.source().getCanonicalFile());
        assertNull(source.file());
    }

    @Test
    public void imageInfoThumbnailSourceKeepsThumbnailSourceFile() throws Exception {
        File thumbnail = new File(
                new File(Files.createTempDirectory("wxo-candidate-source-thumb-candidate").toFile(), "image2"),
                "th_abcd1234");
        writeJpeg(thumbnail);
        MediaResolver.ImageInfoResult result = MediaResolver.ImageInfoResult.of(
                MediaResolver.ImageInfoStatus.DIRECT_IMAGE_THUMBNAIL,
                null,
                thumbnail,
                12L,
                null,
                null);

        ImageDownloadCandidateSource source = ImageDownloadCandidateSource.fromImageInfoResult(result);

        assertTrue(source.isThumbnail());
        assertEquals(thumbnail.getCanonicalFile(), source.source().getCanonicalFile());
    }

    @Test
    public void unsupportedResultKeepsUnsupportedSourceFile() throws Exception {
        File unsupported = Files.createTempFile("wxo-candidate-source-unsupported", ".ref").toFile();
        MediaResolver.Result result = MediaResolver.Result.unsupported(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_UNSUPPORTED,
                unsupported);

        ImageDownloadCandidateSource source = ImageDownloadCandidateSource.fromMediaResult(result);

        assertTrue(source.isUnsupported());
        assertEquals(unsupported.getCanonicalFile(), source.source().getCanonicalFile());
    }

    @Test
    public void imageInfoRefTargetKeepsSourceAndTargetFileDistinct() throws Exception {
        File pointer = Files.createTempFile("wxo-candidate-source-info-ref", ".ref").toFile();
        File target = Files.createTempFile("wxo-candidate-source-info-target", ".jpg").toFile();
        writeJpeg(target);
        MediaResolver.ImageInfoResult result = MediaResolver.ImageInfoResult.of(
                MediaResolver.ImageInfoStatus.DIRECT_IMAGE_REF_TARGET,
                target,
                pointer,
                34L,
                null,
                null);

        ImageDownloadCandidateSource source = ImageDownloadCandidateSource.fromImageInfoResult(result);

        assertTrue(source.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), source.source().getCanonicalFile());
        assertEquals(target.getCanonicalFile(), source.file().getCanonicalFile());
    }

    @Test
    public void nullAndMissingFileResultsBecomeMissing() {
        assertTrue(ImageDownloadCandidateSource.fromMediaResult(null).isMissing());
        assertTrue(ImageDownloadCandidateSource.fromImageInfoResult(null).isMissing());
        assertTrue(ImageDownloadCandidateSource.fromMediaResult(MediaResolver.Result.notFound()).isMissing());
        assertTrue(ImageDownloadCandidateSource.fromImageInfoResult(MediaResolver.ImageInfoResult.missing()).isMissing());
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
