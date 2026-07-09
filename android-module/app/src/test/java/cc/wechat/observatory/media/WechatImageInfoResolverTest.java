package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageInfoResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void missingInputReturnsMissing() {
        MediaResolver.ImageInfoResult nullRoot =
                WechatImageInfoResolver.resolve(null, new FakeImageInfo(), null);
        MediaResolver.ImageInfoResult nullInfo =
                WechatImageInfoResolver.resolve(new File("app"), null, null);

        assertEquals(MediaResolver.ImageInfoStatus.MISSING_INPUT, nullRoot.status());
        assertEquals(MediaResolver.ImageInfoStatus.MISSING_INPUT, nullInfo.status());
    }

    @Test
    public void directPathWinsBeforeCandidateLookup() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-resolver-direct").toFile();
        File direct = new File(appRoot, "direct-image.jpg");
        writeJpeg(direct);
        FakeImageInfo info = new FakeImageInfo();
        info.a = 11L;
        info.path = direct.getAbsolutePath();

        MediaResolver.ImageInfoResult result = WechatImageInfoResolver.resolve(appRoot, info, null);

        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_FILE, result.status());
        assertEquals(11L, result.localInfoId());
        assertEquals(direct.getCanonicalFile(), result.file().getCanonicalFile());
        assertTrue(result.candidateNames().isEmpty());
    }

    @Test
    public void profileReferenceTargetKeepsCandidateNames() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-resolver-profile").toFile();
        File pointer = image2File(appRoot, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2Root(appRoot), ".ref"), "d"), REF_ID);
        writeJpeg(real);
        FakeImageInfo info = new FakeImageInfo();
        info.a = 12L;
        info.path = BASE + ".jpg";

        MediaResolver.ImageInfoResult result = WechatImageInfoResolver.resolve(appRoot, info, null);

        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_REF_TARGET, result.status());
        assertEquals(12L, result.localInfoId());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
        assertTrue(result.candidateNames().contains(BASE + ".jpg"));
    }

    @Test
    public void lookupMissIncludesFieldDiagnostics() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-resolver-miss").toFile();
        FakeImageInfo info = new FakeImageInfo();
        info.a = 13L;
        info.path = BASE + ".jpg";

        MediaResolver.ImageInfoResult result = WechatImageInfoResolver.resolve(appRoot, info, null);

        assertEquals(MediaResolver.ImageInfoStatus.CANDIDATES_NOT_FOUND, result.status());
        assertEquals(13L, result.localInfoId());
        assertNull(result.file());
        assertTrue(result.candidateNames().contains(BASE + ".jpg"));
        assertTrue(result.fieldDebug().contains("path=" + BASE + ".jpg"));
    }

    @Test
    public void valuesWithoutImageNamesReportNoCandidates() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-resolver-no-name").toFile();

        MediaResolver.ImageInfoResult result = WechatImageInfoResolver.resolveValues(
                appRoot,
                14L,
                Collections.singletonList("<msg><img /></msg>"),
                null);

        assertEquals(MediaResolver.ImageInfoStatus.NO_CANDIDATE_NAMES, result.status());
        assertEquals(14L, result.localInfoId());
        assertTrue(result.candidateNames().isEmpty());
        assertTrue(result.fieldDebug().isEmpty());
    }

    private static File image2File(File appRoot, String name) {
        File bucket = new File(new File(image2Root(appRoot), BASE.substring(0, 2)), BASE.substring(2, 4));
        return new File(bucket, name);
    }

    private static File image2Root(File appRoot) {
        return new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
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

    private static final class FakeImageInfo {
        long a;
        String path;
    }
}
