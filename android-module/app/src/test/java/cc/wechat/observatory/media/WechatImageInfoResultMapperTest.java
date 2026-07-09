package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageInfoResultMapperTest {
    @Test
    public void fileStatusKeepsDirectReferenceTargetKind() throws Exception {
        File image = Files.createTempFile("wxo-info-result-ref", ".jpg").toFile();
        File pointer = Files.createTempFile("wxo-info-result-pointer", ".ref").toFile();
        writeJpeg(image);

        MediaResolver.ImageInfoResult result = WechatImageInfoResultMapper.file(
                WechatImageFileResolver.CandidateResolution.refTarget(pointer, image),
                true,
                12L,
                Collections.singletonList("name"),
                null);

        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_REF_TARGET, result.status());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void missStatusKeepsProfileThumbnailAndUnsupportedKinds() throws Exception {
        File thumbnail = Files.createTempFile("wxo-info-result-thumb", ".jpg").toFile();
        File unsupported = Files.createTempFile("wxo-info-result-unsupported", ".ref").toFile();
        writeJpeg(thumbnail);

        MediaResolver.ImageInfoResult thumbnailResult = WechatImageInfoResultMapper.miss(
                WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(thumbnail),
                false,
                13L,
                Collections.singletonList("thumb"),
                Collections.singletonList("path=thumb"));
        MediaResolver.ImageInfoResult unsupportedResult = WechatImageInfoResultMapper.miss(
                WechatImageFileResolver.CandidateResolution.unsupported(unsupported),
                false,
                14L,
                Collections.singletonList("unsupported"),
                null);

        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_THUMBNAIL, thumbnailResult.status());
        assertEquals(thumbnail.getCanonicalFile(), thumbnailResult.source().getCanonicalFile());
        assertNull(thumbnailResult.file());
        assertTrue(thumbnailResult.fieldDebug().contains("path=thumb"));
        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_UNSUPPORTED, unsupportedResult.status());
        assertEquals(unsupported.getCanonicalFile(), unsupportedResult.source().getCanonicalFile());
        assertNull(unsupportedResult.file());
    }

    @Test
    public void missingOrFileCandidateDoesNotBecomeMissDiagnostic() throws Exception {
        File image = Files.createTempFile("wxo-info-result-file", ".jpg").toFile();
        writeJpeg(image);

        assertNull(WechatImageInfoResultMapper.miss(
                WechatImageFileResolver.CandidateResolution.missing(null),
                true,
                15L,
                null,
                null));
        assertNull(WechatImageInfoResultMapper.miss(
                WechatImageFileResolver.CandidateResolution.realImage(image),
                true,
                16L,
                null,
                null));
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
