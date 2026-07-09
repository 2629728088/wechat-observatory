package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadCandidateMapperTest {
    @Test
    public void mediaResultRefTargetMapsToRefTargetCandidateUsingTargetFile() throws Exception {
        File pointer = Files.createTempFile("wxo-candidate-mapper-ref", ".ref").toFile();
        File target = Files.createTempFile("wxo-candidate-mapper-target", ".jpg").toFile();
        writeJpeg(target);
        MediaResolver.Result result = MediaResolver.Result.found(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_REF_TARGET,
                target,
                pointer);

        ImageDownloadResolution.Candidate candidate =
                ImageDownloadCandidateMapper.fromMediaResult(result);

        assertTrue(candidate.isReferenceTarget());
        assertEquals(target.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void imageInfoThumbnailMapsToThumbnailCandidateUsingSource() throws Exception {
        File thumbnail = new File(
                new File(Files.createTempDirectory("wxo-candidate-mapper-thumb").toFile(), "image2"),
                "th_abcd1234");
        writeJpeg(thumbnail);
        MediaResolver.ImageInfoResult result = MediaResolver.ImageInfoResult.of(
                MediaResolver.ImageInfoStatus.PROFILE_IMAGE_THUMBNAIL,
                null,
                thumbnail,
                12L,
                null,
                null);

        ImageDownloadResolution.Candidate candidate =
                ImageDownloadCandidateMapper.fromImageInfoResult(result);

        assertTrue(candidate.isLowQualityThumbnail());
        assertEquals(thumbnail.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void unsupportedMapsToUnsupportedCandidateUsingSource() throws Exception {
        File unsupported = Files.createTempFile("wxo-candidate-mapper-unsupported", ".ref").toFile();
        MediaResolver.Result result = MediaResolver.Result.unsupported(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_UNSUPPORTED,
                unsupported);

        ImageDownloadResolution.Candidate candidate =
                ImageDownloadCandidateMapper.fromMediaResult(result);

        assertTrue(candidate.isUnsupported());
        assertEquals(unsupported.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void nullAndMissingResultsMapToMissingCandidate() {
        assertTrue(ImageDownloadCandidateMapper.fromMediaResult(null).isMissing());
        assertTrue(ImageDownloadCandidateMapper.fromImageInfoResult(null).isMissing());
        assertTrue(ImageDownloadCandidateMapper.fromMediaResult(MediaResolver.Result.notFound()).isMissing());
        assertTrue(ImageDownloadCandidateMapper.fromImageInfoResult(MediaResolver.ImageInfoResult.missing()).isMissing());
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
