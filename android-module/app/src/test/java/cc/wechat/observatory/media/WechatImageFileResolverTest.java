package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageFileResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void resolveCandidateRejectsLowQualityThumbnail() throws Exception {
        File root = Files.createTempDirectory("wxo-image-thumb").toFile();
        File thumbnail = new File(new File(new File(root, "image2"), BASE.substring(0, 2)), "th_" + BASE);
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageFileResolver.resolveCandidateDetails(thumbnail, logs::add);

        assertTrue(resolution.isLowQualityThumbnail());
        assertTrue(!resolution.hasResolvedFile());
        assertNull(resolution.file());
        assertTrue(contains(logs, "skip thumbnail image candidate"));
    }

    @Test
    public void resolveCandidateFollowsWechatReferencePointer() throws Exception {
        File root = Files.createTempDirectory("wxo-image-ref").toFile();
        File image2 = new File(root, "image2");
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        File pointer = new File(bucket, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);
        List<String> logs = new ArrayList<>();

        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageFileResolver.resolveCandidateDetails(pointer, logs::add);

        assertTrue(resolution.isReferenceTarget());
        assertTrue(resolution.hasResolvedFile());
        assertEquals(real.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertTrue(contains(logs, "image ref resolved"));
    }

    @Test
    public void resolveCandidateDistinguishesRealImageFromUnsupportedFile() throws Exception {
        File root = Files.createTempDirectory("wxo-image-kind").toFile();
        File image = new File(root, "real.jpg");
        writeJpeg(image);
        File unsupported = new File(root, "not-image.bin");
        writeText(unsupported, "not an image");

        WechatImageFileResolver.CandidateResolution imageResolution =
                WechatImageFileResolver.resolveCandidateDetails(image, null);
        WechatImageFileResolver.CandidateResolution unsupportedResolution =
                WechatImageFileResolver.resolveCandidateDetails(unsupported, null);

        assertTrue(imageResolution.isRealImage());
        assertTrue(imageResolution.hasResolvedFile());
        assertEquals(image.getCanonicalFile(), imageResolution.file().getCanonicalFile());
        assertTrue(unsupportedResolution.isUnsupported());
        assertTrue(!unsupportedResolution.hasResolvedFile());
        assertNull(unsupportedResolution.file());
    }

    @Test
    public void findInProfileRootsResolvesBucketedRefAfterThumbnailMiss() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        File image2 = new File(new File(microMsgRoot, "profile"), "image2");
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        File thumbnail = new File(bucket, "th_" + BASE);
        writeJpeg(thumbnail);
        File pointer = new File(bucket, "th_" + BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageFileResolver.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList("th_" + BASE),
                        null);

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void findInProfileRootsDetailsReportsRefTarget() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        File image2 = new File(new File(microMsgRoot, "profile"), "image2");
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        File pointer = new File(bucket, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageFileResolver.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList(BASE),
                        null);

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void profileSearchResultKeepsResolutionSemantics() throws Exception {
        File root = Files.createTempDirectory("wxo-image-result").toFile();
        File pointer = new File(root, BASE + WechatImageFiles.REF_SUFFIX);
        File real = new File(root, BASE + ".jpg");
        WechatImageFileResolver.CandidateResolution resolution =
                WechatImageFileResolver.CandidateResolution.refTarget(pointer, real);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageFileResolver.ProfileSearchResult.from(resolution);

        assertTrue(result.hasResolvedFile());
        assertTrue(result.isReferenceTarget());
        assertEquals(resolution.kind(), result.kind());
        assertEquals(pointer, result.source());
        assertEquals(real, result.file());
    }

    @Test
    public void profileSearchResultHandlesNullResolutionAsMissing() {
        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageFileResolver.ProfileSearchResult.from(null);

        assertTrue(result.isMissing());
        assertTrue(!result.hasResolvedFile());
        assertNull(result.source());
        assertNull(result.file());
    }

    @Test
    public void findInProfileRootsDetailsReportsUnsupportedRefCandidate() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        File image2 = new File(new File(microMsgRoot, "profile"), "image2");
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        File pointer = new File(bucket, BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageFileResolver.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList(BASE),
                        null);

        assertTrue(result.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
    }

    @Test
    public void findInProfileRootsDetailsReportsRealImage() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        File image2 = new File(new File(microMsgRoot, "profile"), "image2");
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        File real = new File(bucket, BASE + ".jpg");
        writeJpeg(real);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageFileResolver.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList(BASE + ".jpg"),
                        null);

        assertTrue(result.isRealImage());
        assertEquals(real.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void findInProfileRootsPrefersCanonicalRefBeforeHdThumbnail() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        File image2 = new File(new File(microMsgRoot, "profile"), "image2");
        File bucket = new File(new File(image2, BASE.substring(0, 2)), BASE.substring(2, 4));
        File hdThumbnail = new File(bucket, "th_" + BASE + "hd");
        writeJpeg(hdThumbnail);
        File pointer = new File(bucket, "th_" + BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File real = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(real);

        WechatImageFileResolver.ProfileSearchResult result =
                WechatImageFileResolver.findInProfileRootsDetails(
                        microMsgRoot,
                        new String[]{"image2"},
                        Collections.singletonList("th_" + BASE + "hd"),
                        null);

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
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

    private static boolean contains(List<String> values, String needle) {
        for (String value : values) {
            if (value != null && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
