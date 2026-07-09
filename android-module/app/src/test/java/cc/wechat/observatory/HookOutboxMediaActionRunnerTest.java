package cc.wechat.observatory;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;

import cc.wechat.observatory.outbox.OutboxMediaFilePreparer;
import cc.wechat.observatory.outbox.OutboxMediaSpec;
import cc.wechat.observatory.wechat.SendResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HookOutboxMediaActionRunnerTest {
    @Test
    public void successfulSendRetainsDownloadedFile() throws Exception {
        File mediaFile = tempFile("photo.jpg", new byte[]{1, 2, 3});
        OutboxMediaFilePreparer preparer = new OutboxMediaFilePreparer(new FixedDownloader(mediaFile), null);
        CapturingSender sender = new CapturingSender(SendResult.sent(123L));

        SendResult result = HookOutboxMediaActionRunner.run(
                "image",
                () -> preparer.prepare(spec("/module/media/photo.jpg", "photo.jpg", 1000), "image", false),
                sender);

        assertTrue(result.ok);
        assertEquals(123L, result.chatRecordId);
        assertEquals(1, sender.calls);
        assertTrue(mediaFile.isFile());
    }

    @Test
    public void failedSendDeletesDownloadedFile() throws Exception {
        File mediaFile = tempFile("video.mp4", new byte[]{1, 2, 3});
        OutboxMediaFilePreparer preparer = new OutboxMediaFilePreparer(new FixedDownloader(mediaFile), null);

        SendResult result = HookOutboxMediaActionRunner.run(
                "video",
                () -> preparer.prepare(spec("/module/media/video.mp4", "video.mp4", 1000), "video", false),
                new CapturingSender(SendResult.failed("wechat rejected video")));

        assertFalse(result.ok);
        assertEquals("wechat rejected video", result.error);
        assertFalse(mediaFile.exists());
    }

    @Test
    public void prepareErrorDoesNotInvokeSender() {
        CapturingSender sender = new CapturingSender(SendResult.sent(1L));

        SendResult result = HookOutboxMediaActionRunner.run(
                "file",
                () -> new OutboxMediaFilePreparer(null, null).prepare((OutboxMediaSpec) null, "file", true),
                sender);

        assertFalse(result.ok);
        assertEquals("file media_url is required", result.error);
        assertEquals(0, sender.calls);
    }

    @Test
    public void senderExceptionDeletesDownloadedFileAndReportsKind() throws Exception {
        File mediaFile = tempFile("voice.silk", new byte[]{0x02, 0x23, 0x21, 0x53, 0x49, 0x4c, 0x4b});
        OutboxMediaFilePreparer preparer = new OutboxMediaFilePreparer(new FixedDownloader(mediaFile), null);

        SendResult result = HookOutboxMediaActionRunner.run(
                "voice",
                () -> preparer.prepare(spec("/module/media/voice.silk", "voice.silk", 1000), "voice", false),
                media -> {
                    throw new IllegalStateException("voice service missing");
                });

        assertFalse(result.ok);
        assertTrue(result.error.startsWith("voice send failed:"));
        assertFalse(mediaFile.exists());
    }

    private static OutboxMediaSpec spec(String mediaUrl, String mediaName, int durationMs) throws Exception {
        Constructor<OutboxMediaSpec> constructor = OutboxMediaSpec.class.getDeclaredConstructor(
                String.class,
                String.class,
                int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(mediaUrl, mediaName, durationMs);
    }

    private static File tempFile(String name, byte[] bytes) throws Exception {
        File dir = Files.createTempDirectory("wxo-runner").toFile();
        File file = new File(dir, name);
        Files.write(file.toPath(), bytes);
        return file;
    }

    private static final class FixedDownloader implements OutboxMediaFilePreparer.Downloader {
        private final File file;

        FixedDownloader(File file) {
            this.file = file;
        }

        @Override
        public File download(cc.wechat.observatory.outbox.OutboxMediaSpec media, boolean preserveName) {
            return file;
        }
    }

    private static final class CapturingSender implements HookOutboxMediaActionRunner.MediaSender {
        private final SendResult result;
        int calls;

        CapturingSender(SendResult result) {
            this.result = result;
        }

        @Override
        public SendResult send(OutboxMediaFilePreparer.PreparedMedia media) {
            calls++;
            return result;
        }
    }
}
