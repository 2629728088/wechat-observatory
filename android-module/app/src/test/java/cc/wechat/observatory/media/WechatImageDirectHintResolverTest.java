package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageDirectHintResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void directRealImageHintBecomesDirectImageFile() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-direct-real").toFile();
        File image = new File(appRoot, "direct.jpg");
        writeJpeg(image);

        MediaResolver.Result result = WechatImageDirectHintResolver.resolve(
                appRoot,
                image.getAbsolutePath(),
                null);

        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_FILE, result.status());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), result.source().getCanonicalFile());
    }

    @Test
    public void directReferencePointerHintResolvesToRefTarget() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-direct-ref").toFile();
        File pointer = new File(imageBucket(appRoot), BASE + WechatImageFiles.REF_SUFFIX);
        File target = refTarget(appRoot, REF_ID);
        writeText(pointer, REF_ID);
        writeJpeg(target);
        List<String> logs = new ArrayList<>();

        MediaResolver.Result result = WechatImageDirectHintResolver.resolve(
                appRoot,
                pointer.getAbsolutePath(),
                logs::add);

        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_REF_TARGET, result.status());
        assertEquals(target.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertTrue(contains(logs, "image ref resolved"));
    }

    @Test
    public void directThumbnailHintReportsThumbnailWithoutFile() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-direct-thumb").toFile();
        File thumbnail = new File(imageBucket(appRoot), "th_" + BASE);
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        MediaResolver.Result result = WechatImageDirectHintResolver.resolve(
                appRoot,
                thumbnail.getAbsolutePath(),
                logs::add);

        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_THUMBNAIL, result.status());
        assertNull(result.file());
        assertEquals(thumbnail.getCanonicalFile(), result.source().getCanonicalFile());
        assertTrue(contains(logs, "skip thumbnail image candidate"));
    }

    @Test
    public void missingDirectHintReturnsNotFound() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-direct-missing").toFile();

        MediaResolver.Result result = WechatImageDirectHintResolver.resolve(
                appRoot,
                "missing.jpg",
                null);

        assertEquals(MediaResolver.ResolutionStatus.NOT_FOUND, result.status());
        assertNull(result.file());
    }

    private static File imageBucket(File appRoot) {
        File bucket = new File(new File(image2Root(appRoot), BASE.substring(0, 2)), BASE.substring(2, 4));
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

    private static boolean contains(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
