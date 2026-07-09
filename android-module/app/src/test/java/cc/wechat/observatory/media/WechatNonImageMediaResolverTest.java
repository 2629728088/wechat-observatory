package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatNonImageMediaResolverTest {
    @Test
    public void genericTypeFindsProfileMediaFile() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-non-image-profile").toFile();
        File media = new File(new File(new File(appRoot, "MicroMsg"), "profile/attachment"), "doc.bin");
        writeBytes(media, "payload");

        MediaResolver.Result result = WechatNonImageMediaResolver.resolve(
                appRoot,
                49,
                Collections.singletonList("doc.bin"),
                0L,
                null);

        assertEquals(MediaResolver.ResolutionStatus.PROFILE_MEDIA_FILE, result.status());
        assertEquals(media.getCanonicalFile(), result.file().getCanonicalFile());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void emojiProfileHitReportsEmojiStatusAndLogs() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-non-image-emoji-profile").toFile();
        File emoji = new File(new File(new File(appRoot, "MicroMsg"), "profile/emoji/custom"), "emoji.dat");
        writeBytes(emoji, "emoji");
        List<String> logs = new ArrayList<>();

        MediaResolver.Result result = WechatNonImageMediaResolver.resolve(
                appRoot,
                47,
                Collections.singletonList("emoji.dat"),
                0L,
                logs::add);

        assertEquals(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, result.status());
        assertEquals(emoji.getCanonicalFile(), result.file().getCanonicalFile());
        assertTrue(logsContain(logs, "emoji media selected file=emoji.dat"));
    }

    @Test
    public void emojiFallbackSearchesOutsideProfileBeforeDiagnostic() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-non-image-emoji-fallback").toFile();
        File emoji = new File(new File(appRoot, "files/public/emoji"), "fallback.dat");
        writeBytes(emoji, "emoji");

        MediaResolver.Result result = WechatNonImageMediaResolver.resolve(
                appRoot,
                47,
                Collections.singletonList("fallback.dat"),
                0L,
                null);

        assertEquals(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, result.status());
        assertEquals(emoji.getCanonicalFile(), result.file().getCanonicalFile());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void emojiMissRequestsDiagnostic() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-non-image-emoji-miss").toFile();

        MediaResolver.Result result = WechatNonImageMediaResolver.resolve(
                appRoot,
                47,
                Collections.singletonList("missing.dat"),
                0L,
                null);

        assertEquals(MediaResolver.ResolutionStatus.EMOJI_DIAGNOSTIC_NEEDED, result.status());
        assertNull(result.file());
        assertTrue(result.needsEmojiDiagnostic());
    }

    @Test
    public void voiceUsesRecentVoiceFallback() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-non-image-voice").toFile();
        long createTimeSeconds = 1700000000L;
        File voice = new File(new File(new File(appRoot, "MicroMsg"), "profile/voice2"), "voice.amr");
        writeBytes(voice, "#!AMR\n");
        assertTrue(voice.setLastModified(createTimeSeconds * 1000L));

        MediaResolver.Result result = WechatNonImageMediaResolver.resolve(
                appRoot,
                34,
                Collections.emptyList(),
                createTimeSeconds,
                null);

        assertEquals(MediaResolver.ResolutionStatus.VOICE_RECENT_FILE, result.status());
        assertEquals(voice.getCanonicalFile(), result.file().getCanonicalFile());
    }

    @Test
    public void videoFallsBackToCacheRoots() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-non-image-video-cache").toFile();
        File video = new File(new File(new File(appRoot, "cache"), "finder/video"), "video_abc.mp4");
        writeBytes(video, "video");

        MediaResolver.Result result = WechatNonImageMediaResolver.resolve(
                appRoot,
                43,
                MediaSearchPlan.candidateNames(43, "abc.mp4", ""),
                0L,
                null);

        assertEquals(MediaResolver.ResolutionStatus.VIDEO_CACHE_FILE, result.status());
        assertEquals(video.getCanonicalFile(), result.file().getCanonicalFile());
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
