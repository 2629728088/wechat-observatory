package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageBucketCandidateResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void resolvePrefersReferenceTargetBeforeExactImage() throws Exception {
        File image2 = image2Root("wxo-bucket-candidate-ref");
        File bucket = bucket(image2);
        File pointer = new File(bucket, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File exact = new File(bucket, BASE);
        writeJpeg(exact);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);

        WechatImageFileResolver.CandidateResolution result =
                WechatImageBucketCandidateResolver.resolve(bucket, BASE, null);

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void resolveUsesExactImageWhenReferenceMissing() throws Exception {
        File image2 = image2Root("wxo-bucket-candidate-exact");
        File bucket = bucket(image2);
        File exact = new File(bucket, BASE + ".jpg");
        writeJpeg(exact);

        WechatImageFileResolver.CandidateResolution result =
                WechatImageBucketCandidateResolver.resolve(bucket, BASE + ".jpg", null);

        assertTrue(result.isRealImage());
        assertEquals(exact.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void resolveReportsUnsupportedReferenceWhenExactMissing() throws Exception {
        File image2 = image2Root("wxo-bucket-candidate-unsupported");
        File bucket = bucket(image2);
        File pointer = new File(bucket, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        WechatImageFileResolver.CandidateResolution result =
                WechatImageBucketCandidateResolver.resolve(bucket, BASE, null);

        assertTrue(result.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
    }

    @Test
    public void resolveReferenceKeepsCanonicalThumbnailReferenceMiss() throws Exception {
        File image2 = image2Root("wxo-bucket-candidate-thumb-ref-miss");
        File bucket = bucket(image2);
        File pointer = new File(bucket, "th_" + BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        WechatImageFileResolver.CandidateResolution result =
                WechatImageBucketCandidateResolver.resolveReference(bucket, "th_" + BASE + "hd", null);

        assertTrue(result.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
    }

    @Test
    public void resolveReportsMissingWhenReferenceAndExactAreMissing() throws Exception {
        File image2 = image2Root("wxo-bucket-candidate-all-missing");
        File bucket = bucket(image2);

        WechatImageFileResolver.CandidateResolution result =
                WechatImageBucketCandidateResolver.resolve(bucket, BASE, null);

        assertTrue(result.isMissing());
        assertNull(result.file());
    }

    @Test
    public void resolveReferenceIgnoresPlainMissingPointerFiles() throws Exception {
        File image2 = image2Root("wxo-bucket-candidate-missing");
        File bucket = bucket(image2);

        WechatImageFileResolver.CandidateResolution result =
                WechatImageBucketCandidateResolver.resolveReference(bucket, BASE, null);

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
