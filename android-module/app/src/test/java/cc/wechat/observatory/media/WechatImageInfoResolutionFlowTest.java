package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageInfoResolutionFlowTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void directResolvedFileWinsBeforeProfileCandidates() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-flow-direct").toFile();
        File pointer = image2File(appRoot, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File directTarget = refTarget(appRoot, REF_ID);
        writeJpeg(directTarget);
        File profileImage = image2File(appRoot, BASE + ".jpg");
        writeJpeg(profileImage);

        MediaResolver.ImageInfoResult result = WechatImageInfoResolutionFlow.resolve(
                appRoot,
                WechatImageInfoSnapshot.of(
                        40L,
                        Arrays.asList(pointer.getAbsolutePath(), BASE + ".jpg"),
                        null),
                null);

        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_REF_TARGET, result.status());
        assertEquals(directTarget.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
    }

    @Test
    public void profileFileWinsWhenDirectCandidateIsOnlyThumbnail() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-flow-profile").toFile();
        File thumbnail = image2File(appRoot, "th_" + BASE);
        writeJpeg(thumbnail);
        File profileImage = image2File(appRoot, BASE + ".jpg");
        writeJpeg(profileImage);

        MediaResolver.ImageInfoResult result = WechatImageInfoResolutionFlow.resolve(
                appRoot,
                WechatImageInfoSnapshot.of(
                        41L,
                        Arrays.asList(thumbnail.getAbsolutePath(), BASE + ".jpg"),
                        null),
                null);

        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_FILE, result.status());
        assertEquals(profileImage.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void profileMissDiagnosticsWinBeforeDirectMissWhenNamesExist() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-flow-miss").toFile();
        File unsupported = new File(appRoot, "not-image.bin");
        writeText(unsupported, "not an image");
        File pointer = image2File(appRoot, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        MediaResolver.ImageInfoResult result = WechatImageInfoResolutionFlow.resolve(
                appRoot,
                WechatImageInfoSnapshot.of(
                        42L,
                        Arrays.asList(unsupported.getAbsolutePath(), BASE + ".jpg"),
                        Collections.singletonList("path=" + BASE + ".jpg")),
                null);

        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_UNSUPPORTED, result.status());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
        assertTrue(result.fieldDebug().contains("path=" + BASE + ".jpg"));
    }

    private static File image2File(File appRoot, String name) {
        File bucket = new File(new File(image2Root(appRoot), BASE.substring(0, 2)), BASE.substring(2, 4));
        return new File(bucket, name);
    }

    private static File image2Root(File appRoot) {
        return new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
    }

    private static File refTarget(File appRoot, String refId) {
        return new File(new File(new File(image2Root(appRoot), ".ref"), "d"), refId);
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
}
