package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatProfileMediaResolverTest {
    @Test
    public void resolvesGenericProfileMedia() throws Exception {
        File microMsgRoot = Files.createTempDirectory("wxo-profile-media").toFile();
        File media = new File(new File(new File(microMsgRoot, "profile"), "attachment"), "doc.bin");
        writeBytes(media, "payload");

        MediaResolver.Result result = WechatProfileMediaResolver.resolve(
                microMsgRoot,
                MediaFiles.MESSAGE_TYPE_FILE,
                Collections.singletonList("doc.bin"),
                null);

        assertEquals(MediaResolver.ResolutionStatus.PROFILE_MEDIA_FILE, result.status());
        assertEquals(media.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void resolvesEmojiProfileMediaAndLogs() throws Exception {
        File microMsgRoot = Files.createTempDirectory("wxo-profile-emoji").toFile();
        File emoji = new File(new File(new File(microMsgRoot, "profile"), "emoji/custom"), "emoji.dat");
        writeBytes(emoji, "emoji");
        List<String> logs = new ArrayList<>();

        MediaResolver.Result result = WechatProfileMediaResolver.resolve(
                microMsgRoot,
                MediaFiles.MESSAGE_TYPE_EMOJI,
                Collections.singletonList("emoji.dat"),
                logs::add);

        assertEquals(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, result.status());
        assertEquals(emoji.getCanonicalFile(), result.file().getCanonicalFile());
        assertTrue(logsContain(logs, "emoji media selected file=emoji.dat"));
    }

    private static void writeBytes(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        Files.write(file.toPath(), value.getBytes(StandardCharsets.US_ASCII));
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
