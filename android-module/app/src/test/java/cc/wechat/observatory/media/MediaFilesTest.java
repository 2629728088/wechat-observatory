package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaFilesTest {
    @Test
    public void kindForMessageTypeMapsKnownWechatTypes() {
        assertEquals("image", MediaFiles.kindForMessageType(3));
        assertEquals("voice", MediaFiles.kindForMessageType(34));
        assertEquals("video", MediaFiles.kindForMessageType(43));
        assertEquals("video", MediaFiles.kindForMessageType(62));
        assertEquals("emoji", MediaFiles.kindForMessageType(47));
        assertEquals("location", MediaFiles.kindForMessageType(48));
        assertEquals("file", MediaFiles.kindForMessageType(49));
        assertEquals("file", MediaFiles.kindForMessageType(1090519089));
        assertEquals("", MediaFiles.kindForMessageType(999));
    }

    @Test
    public void messageTypeHelpersKeepSelectorRulesCentralized() {
        assertTrue(MediaFiles.isSupportedMessageType(3));
        assertTrue(MediaFiles.isSupportedMessageType(1090519089));
        assertFalse(MediaFiles.isSupportedMessageType(999));

        assertTrue(MediaFiles.isImageMessageType(3));
        assertFalse(MediaFiles.isImageMessageType(43));

        assertTrue(MediaFiles.isVoiceMessageType(34));
        assertFalse(MediaFiles.isVoiceMessageType(3));

        assertTrue(MediaFiles.isVideoMessageType(43));
        assertTrue(MediaFiles.isVideoMessageType(62));
        assertFalse(MediaFiles.isVideoMessageType(3));

        assertTrue(MediaFiles.isEmojiMessageType(47));
        assertFalse(MediaFiles.isEmojiMessageType(3));

        assertTrue(MediaFiles.shouldLogMissingMedia(3));
        assertTrue(MediaFiles.shouldLogMissingMedia(34));
        assertTrue(MediaFiles.shouldLogMissingMedia(43));
        assertTrue(MediaFiles.shouldLogMissingMedia(47));
        assertTrue(MediaFiles.shouldLogMissingMedia(62));
        assertFalse(MediaFiles.shouldLogMissingMedia(49));
        assertFalse(MediaFiles.shouldLogMissingMedia(999));
    }

    @Test
    public void existingFileRequiresRegularFile() throws Exception {
        File file = Files.createTempFile("wxo-existing", ".bin").toFile();
        File directory = Files.createTempDirectory("wxo-existing-dir").toFile();
        File missing = new File(directory, "missing.bin");

        assertTrue(MediaFiles.isExistingFile(file));
        assertFalse(MediaFiles.isExistingFile(directory));
        assertFalse(MediaFiles.isExistingFile(missing));
        assertFalse(MediaFiles.isExistingFile(null));
    }

    @Test
    public void detectMimeUsesHeaderBeforeMessageTypeFallback() throws Exception {
        File noExtension = Files.createTempFile("wxo-media", "").toFile();
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0, 0, 0, 0};

        assertEquals("image/png", MediaFiles.detectMime(3, noExtension, png));
        assertEquals("audio/amr", MediaFiles.detectMime(34, noExtension, new byte[0]));
        assertEquals("video/mp4", MediaFiles.detectMime(43, noExtension, new byte[0]));
    }

    @Test
    public void detectMimeRecognizesSilkHeader() throws Exception {
        File voice = Files.createTempFile("wxo-voice", "").toFile();

        assertEquals("audio/silk", MediaFiles.detectMime(
                34,
                voice,
                "#!SILK_V3".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void supportedVoiceMediaAcceptsMediaNameExtension() throws Exception {
        File voice = Files.createTempFile("wxo-voice", ".bin").toFile();
        try (FileOutputStream output = new FileOutputStream(voice, false)) {
            output.write(new byte[]{1, 2, 3});
        }

        assertTrue(MediaFiles.isSupportedVoiceMediaFile(voice, "message.silk"));
    }

    @Test
    public void supportedVoiceMediaReadsAmrAndSilkHeaders() throws Exception {
        File amr = Files.createTempFile("wxo-voice-amr", ".bin").toFile();
        File silk = Files.createTempFile("wxo-voice-silk", ".bin").toFile();
        try (FileOutputStream output = new FileOutputStream(amr, false)) {
            output.write("#!AMR\n".getBytes(StandardCharsets.US_ASCII));
        }
        try (FileOutputStream output = new FileOutputStream(silk, false)) {
            output.write("#!SILK_V3".getBytes(StandardCharsets.US_ASCII));
        }

        assertTrue(MediaFiles.isSupportedVoiceMediaFile(amr, ""));
        assertTrue(MediaFiles.isSupportedVoiceMediaFile(silk, ""));
    }

    @Test
    public void supportedVoiceMediaRejectsUnknownPayload() throws Exception {
        File file = Files.createTempFile("wxo-voice", ".bin").toFile();
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write("not voice".getBytes(StandardCharsets.US_ASCII));
        }

        assertFalse(MediaFiles.isSupportedVoiceMediaFile(file, "message.bin"));
    }

    @Test
    public void uploadNameAddsExpectedExtension() {
        assertEquals("media-abc.jpg", MediaFiles.uploadName(null, "image/jpeg", "abc"));
        assertEquals("photo.webp", MediaFiles.uploadName(new File("photo"), "image/webp", "abc"));
        assertEquals("photo.png", MediaFiles.uploadName(new File("photo.png"), "image/jpeg", "abc"));
    }

}
