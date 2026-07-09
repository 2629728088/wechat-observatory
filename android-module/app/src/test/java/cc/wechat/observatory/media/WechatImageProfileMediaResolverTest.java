package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageProfileMediaResolverTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";

    @Test
    public void mapsProfileFileAndLogsSelection() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-profile-media-file").toFile();
        File image = image2File(appRoot, BASE + ".jpg");
        writeJpeg(image);
        List<String> logs = new ArrayList<>();

        MediaResolver.Result result = WechatImageProfileMediaResolver.resolve(
                appRoot,
                Collections.singletonList(BASE + ".jpg"),
                logs::add);

        assertEquals(MediaResolver.ResolutionStatus.PROFILE_IMAGE_FILE, result.status());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), result.source().getCanonicalFile());
        assertTrue(logsContain(logs, "image media selected file=" + image.getName()));
    }

    @Test
    public void missingProfileInputReturnsNotFound() {
        MediaResolver.Result result = WechatImageProfileMediaResolver.resolve(
                null,
                Collections.singletonList(BASE + ".jpg"),
                null);

        assertEquals(MediaResolver.ResolutionStatus.NOT_FOUND, result.status());
        assertNull(result.file());
        assertNull(result.source());
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

    private static boolean logsContain(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
