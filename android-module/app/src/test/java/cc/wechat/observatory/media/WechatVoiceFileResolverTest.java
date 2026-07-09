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

public final class WechatVoiceFileResolverTest {
    @Test
    public void findRecentSelectsClosestVoiceFileInWindow() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        long targetSeconds = 1_700_000_000L;
        long targetMs = targetSeconds * 1000L;
        File far = voiceFile(microMsgRoot, "profile-a", "far.amr", targetMs - 120_000L);
        File close = voiceFile(microMsgRoot, "profile-b", "close.amr", targetMs + 1_000L);
        List<String> logs = new ArrayList<>();

        File resolved = WechatVoiceFileResolver.findRecent(microMsgRoot, targetSeconds, logs::add);

        assertEquals(close.getCanonicalFile(), resolved.getCanonicalFile());
        assertTrue(far.isFile());
        assertTrue(contains(logs, "voice media fallback selected"));
    }

    @Test
    public void findRecentIgnoresFilesOutsideTimeWindow() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        long targetSeconds = 1_700_000_000L;
        voiceFile(microMsgRoot, "profile", "old.amr", targetSeconds * 1000L - 20L * 60L * 1000L);

        File resolved = WechatVoiceFileResolver.findRecent(microMsgRoot, targetSeconds, null);

        assertNull(resolved);
    }

    @Test
    public void findRecentIgnoresEmptyVoiceFiles() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        long targetSeconds = 1_700_000_000L;
        File file = new File(new File(new File(microMsgRoot, "profile"), "voice2"), "empty.amr");
        writeBytes(file, targetSeconds * 1000L, new byte[0]);

        File resolved = WechatVoiceFileResolver.findRecent(microMsgRoot, targetSeconds, null);

        assertNull(resolved);
    }

    private static File voiceFile(File microMsgRoot, String profile, String name, long modifiedMs) throws Exception {
        File file = new File(new File(new File(microMsgRoot, profile), "voice2"), name);
        writeBytes(file, modifiedMs, "#!AMR\nvoice".getBytes(StandardCharsets.US_ASCII));
        return file;
    }

    private static void writeBytes(File file, long modifiedMs, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
        assertTrue(file.setLastModified(modifiedMs));
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
