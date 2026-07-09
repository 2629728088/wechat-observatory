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

public final class WechatImageRecursiveScannerTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void scanFindsNestedRealImageCandidate() throws Exception {
        File root = Files.createTempDirectory("wxo-recursive-scan-real").toFile();
        File image = new File(new File(root, "nested"), BASE + ".jpg");
        writeJpeg(image);

        WechatImageFileResolver.ProfileSearchResult result = WechatImageRecursiveScanner.scan(
                root,
                Collections.singletonList(BASE + ".jpg"),
                3,
                null);

        assertTrue(result.isRealImage());
        assertEquals(image.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void scanUsesStableDirectoryNameOrder() throws Exception {
        File root = Files.createTempDirectory("wxo-recursive-scan-order").toFile();
        File laterByName = new File(new File(root, "z"), BASE + ".jpg");
        File earlierByName = new File(new File(root, "a"), BASE + ".jpg");
        writeJpeg(laterByName);
        writeJpeg(earlierByName);

        WechatImageFileResolver.ProfileSearchResult result = WechatImageRecursiveScanner.scan(
                root,
                Collections.singletonList(BASE + ".jpg"),
                2,
                null);

        assertTrue(result.isRealImage());
        assertEquals(earlierByName.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(earlierByName.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void scanPreservesUnsupportedReferenceCandidate() throws Exception {
        File root = Files.createTempDirectory("wxo-recursive-scan-ref").toFile();
        File pointer = new File(root, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        WechatImageFileResolver.ProfileSearchResult result = WechatImageRecursiveScanner.scan(
                root,
                Collections.singletonList(BASE),
                3,
                null);

        assertTrue(result.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
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
