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

public final class WechatImageProfileRootFinderTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void findTraversesProfilesAndRootsUntilResolvedImageExists() throws Exception {
        File microMsg = Files.createTempDirectory("wxo-profile-root-hit").toFile();
        File firstProfileImage2 = new File(new File(microMsg, "profile-a"), "image2");
        File secondProfileOpenApi = new File(new File(microMsg, "profile-b"), "openapi");
        File image = new File(new File(secondProfileOpenApi, "nested"), BASE + ".jpg");
        assertTrue(firstProfileImage2.mkdirs() || firstProfileImage2.isDirectory());
        writeJpeg(image);

        WechatImageFileResolver.ProfileSearchResult result = WechatImageProfileRootFinder.find(
                microMsg,
                new String[]{"image2", "openapi"},
                Collections.singletonList(BASE + ".jpg"),
                null);

        assertTrue(result.isRealImage());
        assertEquals(image.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void findUsesStableProfileNameOrder() throws Exception {
        File microMsg = Files.createTempDirectory("wxo-profile-root-order").toFile();
        File laterByName = new File(new File(new File(microMsg, "profile-b"), "image2"), BASE + ".jpg");
        File earlierByName = new File(new File(new File(microMsg, "profile-a"), "image2"), BASE + ".jpg");
        writeJpeg(laterByName);
        writeJpeg(earlierByName);

        WechatImageFileResolver.ProfileSearchResult result = WechatImageProfileRootFinder.find(
                microMsg,
                new String[]{"image2"},
                Collections.singletonList(BASE + ".jpg"),
                null);

        assertTrue(result.isRealImage());
        assertEquals(earlierByName.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(earlierByName.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void findPreservesUnsupportedReferenceAsBestMissAcrossProfiles() throws Exception {
        File microMsg = Files.createTempDirectory("wxo-profile-root-miss").toFile();
        File image2 = new File(new File(microMsg, "profile-a"), "image2");
        File pointer = new File(image2, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File otherRoot = new File(new File(microMsg, "profile-b"), "image2");
        assertTrue(otherRoot.mkdirs() || otherRoot.isDirectory());

        WechatImageFileResolver.ProfileSearchResult result = WechatImageProfileRootFinder.find(
                microMsg,
                new String[]{"image2"},
                Collections.singletonList(BASE),
                null);

        assertTrue(result.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
    }

    @Test
    public void findReturnsMissingWhenRootsAreAbsent() throws Exception {
        WechatImageFileResolver.ProfileSearchResult result = WechatImageProfileRootFinder.find(
                Files.createTempDirectory("wxo-profile-root-empty").toFile(),
                new String[0],
                Collections.singletonList(BASE),
                null);

        assertTrue(result.isMissing());
        assertNull(result.file());
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
