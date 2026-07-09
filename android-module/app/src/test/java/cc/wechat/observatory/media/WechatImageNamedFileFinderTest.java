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

public final class WechatImageNamedFileFinderTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void findChecksBucketedReferenceBeforeRecursiveImage() throws Exception {
        File microMsgRoot = Files.createTempDirectory("wxo-named-bucket").toFile();
        File image2 = image2Root(microMsgRoot);
        File pointer = image2BucketFile(image2, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);
        File recursiveImage = new File(image2, BASE + ".jpg");
        writeJpeg(recursiveImage);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageNamedFileFinder.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList(BASE),
                        null);

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void findChecksBucketedExactImageBeforeRecursiveImage() throws Exception {
        File microMsgRoot = Files.createTempDirectory("wxo-named-bucket-exact").toFile();
        File image2 = image2Root(microMsgRoot);
        File bucketed = image2BucketFile(image2, BASE + ".jpg");
        writeJpeg(bucketed);
        File recursiveImage = new File(new File(image2, "nested"), BASE + ".jpg");
        writeJpeg(recursiveImage);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageNamedFileFinder.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList(BASE + ".jpg"),
                        null);

        assertTrue(result.isRealImage());
        assertEquals(bucketed.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(bucketed.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void findFallsBackToRecursiveScanWhenBucketNameIsNotHex() throws Exception {
        File microMsgRoot = Files.createTempDirectory("wxo-named-scan").toFile();
        File image2 = image2Root(microMsgRoot);
        File scanned = new File(new File(image2, "nested"), "plain-image.jpg");
        writeJpeg(scanned);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageNamedFileFinder.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList("plain-image.jpg"),
                        null);

        assertTrue(result.isRealImage());
        assertEquals(scanned.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(scanned.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void findPreservesUnsupportedReferenceFromRecursiveScan() throws Exception {
        File microMsgRoot = Files.createTempDirectory("wxo-named-unsupported").toFile();
        File brokenPointer = new File(image2Root(microMsgRoot), BASE + WechatImageFiles.REF_SUFFIX);
        writeText(brokenPointer, REF_ID);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageNamedFileFinder.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList(BASE),
                        null);

        assertTrue(result.isUnsupported());
        assertEquals(brokenPointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
    }

    private static File image2Root(File microMsgRoot) {
        return new File(new File(microMsgRoot, "profile"), "image2");
    }

    private static File image2BucketFile(File image2, String name) {
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        return new File(bucket, name);
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
