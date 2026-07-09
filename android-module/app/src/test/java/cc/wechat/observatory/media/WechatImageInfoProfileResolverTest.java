package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageInfoProfileResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void mapsProfileFileToImageInfoResult() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-profile-file").toFile();
        File image = image2File(appRoot, BASE + ".jpg");
        writeJpeg(image);
        WechatImageInfoCandidateNames names =
                WechatImageInfoCandidateNames.fromValues(Collections.singletonList(BASE + ".jpg"));

        WechatImageInfoProfileResolver.Selection selection =
                WechatImageInfoProfileResolver.resolve(appRoot, 31L, names, null);

        assertTrue(selection.hasFileResult());
        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_FILE, selection.fileResult().status());
        assertEquals(31L, selection.fileResult().localInfoId());
        assertEquals(image.getCanonicalFile(), selection.fileResult().file().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), selection.fileResult().source().getCanonicalFile());
        assertTrue(selection.fileResult().candidateNames().contains(BASE + ".jpg"));
    }

    @Test
    public void keepsUnsupportedProfileCandidateForMissSelection() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-profile-unsupported").toFile();
        File pointer = image2File(appRoot, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        WechatImageInfoCandidateNames names =
                WechatImageInfoCandidateNames.fromValues(Collections.singletonList(BASE + ".jpg"));

        WechatImageInfoProfileResolver.Selection selection =
                WechatImageInfoProfileResolver.resolve(appRoot, 32L, names, null);

        assertFalse(selection.hasFileResult());
        assertNull(selection.fileResult());
        assertTrue(selection.candidate().isUnsupported());
        assertEquals(pointer.getCanonicalFile(), selection.candidate().source().getCanonicalFile());
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
}
