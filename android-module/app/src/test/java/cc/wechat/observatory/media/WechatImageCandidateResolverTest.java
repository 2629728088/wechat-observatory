package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageCandidateResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void lowQualityThumbnailIsRejectedWithLog() throws Exception {
        File thumbnail = new File(new File(new File(image2Root("wxo-candidate-thumb"), BASE.substring(0, 2)), BASE.substring(2, 4)), "th_" + BASE);
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageCandidateResolver.resolve(thumbnail, logs::add);

        assertTrue(resolution.isLowQualityThumbnail());
        assertNull(resolution.file());
        assertTrue(contains(logs, "skip thumbnail image candidate"));
    }

    @Test
    public void hdThumbnailIsNotTreatedAsFullImage() throws Exception {
        File thumbnail = new File(new File(new File(image2Root("wxo-candidate-hd-thumb"), BASE.substring(0, 2)), BASE.substring(2, 4)), "th_" + BASE + "hd");
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageCandidateResolver.resolve(thumbnail, logs::add);

        assertTrue(resolution.isLowQualityThumbnail());
        assertNull(resolution.file());
        assertTrue(contains(logs, "skip thumbnail image candidate"));
    }

    @Test
    public void realImageIsUsableCandidate() throws Exception {
        File image = Files.createTempFile("wxo-candidate-real", ".jpg").toFile();
        writeJpeg(image);

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageCandidateResolver.resolve(image, null);

        assertTrue(resolution.isRealImage());
        assertEquals(image.getCanonicalFile(), resolution.file().getCanonicalFile());
    }

    @Test
    public void referencePointerResolvesToRefTarget() throws Exception {
        File image2 = image2Root("wxo-candidate-ref");
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        File pointer = new File(bucket, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);
        List<String> logs = new ArrayList<>();

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageCandidateResolver.resolve(pointer, logs::add);

        assertTrue(resolution.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), resolution.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertTrue(contains(logs, "image ref resolved"));
    }

    @Test
    public void referencePointerNameIsNotTreatedAsRealImageEvenWhenHeaderLooksLikeImage() throws Exception {
        File pointer = new File(image2Root("wxo-candidate-ref-header"), BASE + WechatImageFiles.REF_SUFFIX);
        writeJpeg(pointer);
        List<String> logs = new ArrayList<>();

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageCandidateResolver.resolve(pointer, logs::add);

        assertTrue(resolution.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), resolution.source().getCanonicalFile());
        assertNull(resolution.file());
        assertTrue(contains(logs, "image ref unresolved status=INVALID_REFERENCE_ID"));
    }

    @Test
    public void referencePointerMissingTargetLogsReferenceStatus() throws Exception {
        File image2 = image2Root("wxo-candidate-ref-missing-target");
        File pointer = new File(image2, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        List<String> logs = new ArrayList<>();

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageCandidateResolver.resolve(pointer, logs::add);

        assertTrue(resolution.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), resolution.source().getCanonicalFile());
        assertNull(resolution.file());
        assertTrue(contains(logs, "image ref unresolved status=TARGET_MISSING"));
    }

    @Test
    public void nonImageNonReferenceCandidateIsUnsupported() throws Exception {
        File unsupported = Files.createTempFile("wxo-candidate-unsupported", ".bin").toFile();
        writeText(unsupported, "not an image");

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageCandidateResolver.resolve(unsupported, null);

        assertTrue(resolution.isUnsupported());
        assertEquals(unsupported.getCanonicalFile(), resolution.source().getCanonicalFile());
        assertNull(resolution.file());
    }

    private static File image2Root(String prefix) throws Exception {
        return new File(Files.createTempDirectory(prefix).toFile(), "image2");
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
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        Files.write(file.toPath(), text.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean contains(List<String> values, String expected) {
        for (String value : values) {
            if (value != null && value.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
