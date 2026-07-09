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

public final class WechatImageInfoDirectResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void absoluteDirectImageWinsImmediately() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-direct-abs").toFile();
        File image = Files.createTempFile("wxo-info-direct", ".jpg").toFile();
        File later = Files.createTempFile("wxo-info-direct-later", ".jpg").toFile();
        writeJpeg(image);
        writeJpeg(later);

        WechatImageFileResolver.CandidateResolution result = WechatImageInfoDirectResolver.resolve(
                appRoot,
                Arrays.asList("", image.getAbsolutePath(), later.getAbsolutePath()),
                null);

        assertTrue(result.isRealImage());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void relativeDirectImageIsResolvedAgainstAppRoot() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-direct-relative").toFile();
        File image = new File(appRoot, "relative-image.jpg");
        writeJpeg(image);

        WechatImageFileResolver.CandidateResolution result = WechatImageInfoDirectResolver.resolve(
                appRoot,
                Collections.singletonList("relative-image.jpg"),
                null);

        assertTrue(result.isRealImage());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void referencePointerDirectValueResolvesToRefTarget() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-direct-ref").toFile();
        File image2 = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        File pointer = new File(bucket, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);

        WechatImageFileResolver.CandidateResolution result = WechatImageInfoDirectResolver.resolve(
                appRoot,
                Collections.singletonList(pointer.getAbsolutePath()),
                null);

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void firstNonMissingMissIsPreservedWhenNoUsableImageExists() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-direct-miss").toFile();
        File unsupported = new File(appRoot, "not-image.bin");
        writeText(unsupported, "not an image");

        WechatImageFileResolver.CandidateResolution result = WechatImageInfoDirectResolver.resolve(
                appRoot,
                Arrays.asList("missing.jpg", unsupported.getAbsolutePath()),
                null);

        assertTrue(result.isUnsupported());
        assertEquals(unsupported.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
    }

    @Test
    public void urlAndXmlFieldsAreSkippedBeforeDirectPathResolution() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-info-direct-skip-url").toFile();
        File urlNormalizedImage = new File(appRoot, "host/path/image.jpg");
        writeJpeg(urlNormalizedImage);

        WechatImageFileResolver.CandidateResolution result = WechatImageInfoDirectResolver.resolve(
                appRoot,
                Arrays.asList(
                        "https://host/path/image.jpg?token=secret",
                        "<msg><img aeskey=\"secret\"/></msg>"),
                null);

        assertTrue(result.isMissing());
        assertNull(result.source());
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
