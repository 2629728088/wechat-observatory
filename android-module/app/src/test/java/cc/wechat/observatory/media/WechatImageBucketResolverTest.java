package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageBucketResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void bucketResolverPrefersWechatReferenceTarget() throws Exception {
        File image2 = image2Root("wxo-bucket-ref");
        File bucket = bucket(image2);
        File pointer = new File(bucket, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File exact = new File(bucket, BASE);
        writeJpeg(exact);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageBucketResolver.find(image2, BASE, BASE, null);

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void bucketResolverFallsBackToExactImageWhenReferenceIsMissing() throws Exception {
        File image2 = image2Root("wxo-bucket-exact");
        File exact = new File(bucket(image2), BASE + ".jpg");
        writeJpeg(exact);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageBucketResolver.find(image2, BASE, BASE + ".jpg", null);

        assertTrue(result.isRealImage());
        assertEquals(exact.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void bucketResolverReportsUnsupportedReferenceWhenTargetMissing() throws Exception {
        File image2 = image2Root("wxo-bucket-unsupported");
        File pointer = new File(bucket(image2), BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageBucketResolver.find(image2, BASE, BASE, null);

        assertTrue(result.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
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
