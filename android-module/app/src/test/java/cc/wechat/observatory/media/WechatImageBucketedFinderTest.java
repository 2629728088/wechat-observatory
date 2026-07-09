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

public final class WechatImageBucketedFinderTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void findReturnsFirstResolvedBucketCandidateAcrossNames() throws Exception {
        File image2 = image2Root("wxo-bucketed-names");
        File image = new File(bucket(image2), BASE + ".jpg");
        writeJpeg(image);

        WechatImageFileResolver.ProfileSearchResult result = WechatImageBucketedFinder.find(
                image2,
                Arrays.asList("not-a-bucket-name.jpg", BASE + ".jpg"),
                null);

        assertTrue(result.isRealImage());
        assertEquals(image.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void findKeepsUnsupportedReferenceAsBestMiss() throws Exception {
        File image2 = image2Root("wxo-bucketed-best-miss");
        File pointer = new File(bucket(image2), BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        WechatImageFileResolver.ProfileSearchResult result = WechatImageBucketedFinder.find(
                image2,
                Arrays.asList(BASE, "not-a-bucket-name.jpg"),
                null);

        assertTrue(result.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
    }

    @Test
    public void findReturnsMissingForEmptyNames() throws Exception {
        WechatImageFileResolver.ProfileSearchResult result = WechatImageBucketedFinder.find(
                image2Root("wxo-bucketed-empty"),
                Collections.emptyList(),
                null);

        assertTrue(result.isMissing());
        assertNull(result.file());
    }

    private static File bucket(File image2) {
        return new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
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
}
