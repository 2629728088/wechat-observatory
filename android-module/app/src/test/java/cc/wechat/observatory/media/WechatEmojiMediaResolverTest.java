package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatEmojiMediaResolverTest {
    @Test
    public void emojiInfoDirectoryFieldFindsMd5NamedMediaFileAndLogs() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-emoji-info-dir").toFile();
        String md5 = "abcdef0123456789abcdef0123456789";
        File emojiDir = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "emoji");
        File emoji = new File(emojiDir, md5 + ".gif");
        writeBytes(emoji, "GIF89a");
        FakeEmojiInfo info = new FakeEmojiInfo();
        info.C2 = emojiDir.getAbsolutePath();
        List<String> logs = new ArrayList<>();

        MediaResolver.Result result = WechatEmojiInfoMediaResolver.resolve(appRoot, info, md5, logs::add);

        assertEquals(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, result.status());
        assertEquals(emoji.getCanonicalFile(), result.file().getCanonicalFile());
        assertTrue(logsContain(logs, "emoji media selected from EmojiInfo file=" + md5 + ".gif"));
    }

    @Test
    public void emojiInfoDirectFileFieldWinsBeforeDirectorySearch() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-emoji-info-file").toFile();
        String md5 = "abcdef0123456789abcdef0123456789";
        File emoji = new File(appRoot, "direct-emoji.webp");
        writeBytes(emoji, "WEBP");
        FakeEmojiInfo info = new FakeEmojiInfo();
        info.field_tpurl = "https://example.invalid/emoji";
        info.path = emoji.getAbsolutePath();

        MediaResolver.Result result = WechatEmojiInfoMediaResolver.resolve(appRoot, info, md5, null);

        assertEquals(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, result.status());
        assertEquals(emoji.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void fallbackFindsEmojiOutsideProfileAndLogs() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-emoji-fallback").toFile();
        File emoji = new File(new File(appRoot, "files/public/emoji"), "fallback.dat");
        writeBytes(emoji, "emoji");
        List<String> logs = new ArrayList<>();

        MediaResolver.Result result = WechatEmojiMediaResolver.resolveFallback(
                appRoot,
                Collections.singletonList("fallback.dat"),
                logs::add);

        assertEquals(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, result.status());
        assertEquals(emoji.getCanonicalFile(), result.file().getCanonicalFile());
        assertTrue(logsContain(logs, "emoji media selected file=fallback.dat"));
    }

    @Test
    public void fallbackMissRequestsDiagnostic() throws Exception {
        MediaResolver.Result result = WechatEmojiMediaResolver.resolveFallback(
                Files.createTempDirectory("wxo-emoji-miss").toFile(),
                Collections.singletonList("missing.dat"),
                null);

        assertEquals(MediaResolver.ResolutionStatus.EMOJI_DIAGNOSTIC_NEEDED, result.status());
        assertNull(result.file());
        assertTrue(result.needsEmojiDiagnostic());
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

    private static final class FakeEmojiInfo {
        String C2;
        String path;
        String field_tpurl;
    }
}
