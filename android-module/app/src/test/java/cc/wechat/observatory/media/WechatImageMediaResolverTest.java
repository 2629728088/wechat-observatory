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

public final class WechatImageMediaResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String OTHER = "eeee1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void directHintWinsBeforeProfileCandidates() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-image-media-direct").toFile();
        File direct = new File(appRoot, "direct.jpg");
        writeJpeg(direct);
        File pointer = new File(imageBucket(appRoot, BASE), BASE + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File refTarget = refTarget(appRoot, REF_ID);
        writeJpeg(refTarget);

        MediaResolver.Result result = WechatImageMediaResolver.resolve(
                appRoot,
                direct.getAbsolutePath(),
                Collections.singletonList(BASE),
                null);

        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_FILE, result.status());
        assertEquals(direct.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(direct.getCanonicalFile(), result.source().getCanonicalFile());
    }

    @Test
    public void profileThumbnailIsReportedWithoutReturningAFile() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-image-media-thumb").toFile();
        File thumbnail = new File(imageBucket(appRoot, BASE), "th_" + BASE);
        writeJpeg(thumbnail);

        MediaResolver.Result result = WechatImageMediaResolver.resolve(
                appRoot,
                "",
                MediaSearchPlan.imageCandidateNames(BASE + ".jpg"),
                null);

        assertEquals(MediaResolver.ResolutionStatus.PROFILE_IMAGE_THUMBNAIL, result.status());
        assertEquals(thumbnail.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
    }

    @Test
    public void namedLookupMissDoesNotFallBackToUnrelatedRecentImage() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-image-media-no-recent").toFile();
        File unrelated = new File(imageBucket(appRoot, OTHER), OTHER + ".jpg");
        writeJpeg(unrelated);
        List<String> logs = new ArrayList<>();

        MediaResolver.Result result = WechatImageMediaResolver.resolve(
                appRoot,
                "",
                MediaSearchPlan.imageCandidateNames(BASE + ".jpg"),
                logs::add);

        assertEquals(MediaResolver.ResolutionStatus.NOT_FOUND, result.status());
        assertNull(result.file());
        assertTrue(logsContain(logs, "image named lookup missed"));
    }

    private static File imageBucket(File appRoot, String base) {
        File bucket = new File(new File(image2Root(appRoot), base.substring(0, 2)), base.substring(2, 4));
        assertTrue(bucket.mkdirs() || bucket.isDirectory());
        return bucket;
    }

    private static File image2Root(File appRoot) {
        return new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
    }

    private static File refTarget(File appRoot, String refId) {
        return new File(new File(new File(image2Root(appRoot), ".ref"), "d"), refId);
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

    private static boolean logsContain(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
