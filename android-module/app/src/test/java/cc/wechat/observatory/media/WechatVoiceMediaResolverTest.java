package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatVoiceMediaResolverTest {
    @Test
    public void resolvesRecentVoiceFile() throws Exception {
        File microMsgRoot = Files.createTempDirectory("wxo-voice-recent").toFile();
        long createTimeSeconds = 1700000000L;
        File voice = new File(new File(new File(microMsgRoot, "profile"), "voice2"), "voice.amr");
        writeBytes(voice, "#!AMR\n");
        assertTrue(voice.setLastModified(createTimeSeconds * 1000L));

        MediaResolver.Result result = WechatVoiceMediaResolver.resolveRecent(
                microMsgRoot,
                createTimeSeconds,
                null);

        assertEquals(MediaResolver.ResolutionStatus.VOICE_RECENT_FILE, result.status());
        assertEquals(voice.getCanonicalFile(), result.file().getCanonicalFile());
    }

    private static void writeBytes(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        Files.write(file.toPath(), value.getBytes(StandardCharsets.US_ASCII));
    }
}
