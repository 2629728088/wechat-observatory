package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatImageResultMapperTest {
    @Test
    public void mediaResultKeepsDirectAndProfileFileStatusesDistinct() throws Exception {
        File image = Files.createTempFile("wxo-result-image", ".jpg").toFile();
        writeJpeg(image);

        MediaResolver.Result direct = WechatImageResultMapper.mediaResult(
                WechatImageFileResolver.CandidateResolution.realImage(image),
                true);
        MediaResolver.Result profile = WechatImageResultMapper.mediaResult(
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.realImage(image)),
                false);

        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_FILE, direct.status());
        assertEquals(MediaResolver.ResolutionStatus.PROFILE_IMAGE_FILE, profile.status());
    }

    @Test
    public void mediaResultKeepsRefThumbnailAndUnsupportedStatusesDistinct() throws Exception {
        File image = Files.createTempFile("wxo-result-ref", ".jpg").toFile();
        File thumbnail = Files.createTempFile("wxo-result-thumb", ".jpg").toFile();
        File unsupported = Files.createTempFile("wxo-result-unsupported", ".ref").toFile();
        writeJpeg(image);
        writeJpeg(thumbnail);

        MediaResolver.Result ref = WechatImageResultMapper.mediaResult(
                WechatImageFileResolver.CandidateResolution.refTarget(unsupported, image),
                true);

        assertEquals(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_REF_TARGET,
                ref.status());
        assertEquals(unsupported.getCanonicalFile(), ref.source().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), ref.file().getCanonicalFile());
        assertEquals(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_THUMBNAIL,
                WechatImageResultMapper.mediaResult(
                        WechatImageFileResolver.ProfileSearchResult.from(
                                WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(thumbnail)),
                        false).status());
        assertEquals(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_UNSUPPORTED,
                WechatImageResultMapper.mediaResult(
                        WechatImageFileResolver.CandidateResolution.unsupported(unsupported),
                        true).status());
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
