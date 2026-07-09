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

public final class WechatImageProfileResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void emptyInputReturnsMissing() {
        WechatImageFileResolver.ProfileSearchResult nullRoot =
                WechatImageProfileResolver.find(null, Collections.singletonList(BASE), null);
        WechatImageFileResolver.ProfileSearchResult emptyNames =
                WechatImageProfileResolver.find(new File("app"), Collections.emptyList(), null);

        assertTrue(nullRoot.isMissing());
        assertNull(nullRoot.source());
        assertNull(nullRoot.file());
        assertTrue(emptyNames.isMissing());
        assertNull(emptyNames.source());
        assertNull(emptyNames.file());
    }

    @Test
    public void findsRealImageUnderWechatProfileRoot() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-profile-real").toFile();
        File image = image2File(appRoot, BASE + ".jpg");
        writeJpeg(image);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageProfileResolver.find(appRoot, Collections.singletonList(BASE + ".jpg"), null);

        assertTrue(result.isRealImage());
        assertEquals(image.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void followsReferencePointerUnderWechatProfileRoot() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-profile-ref").toFile();
        File pointer = image2File(appRoot, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2Root(appRoot), ".ref"), "d"), REF_ID);
        writeJpeg(real);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageProfileResolver.find(appRoot, Collections.singletonList(BASE), null);

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void preservesUnsupportedReferenceMiss() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-profile-unsupported").toFile();
        File pointer = image2File(appRoot, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageProfileResolver.find(appRoot, Collections.singletonList(BASE), null);

        assertTrue(result.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
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
