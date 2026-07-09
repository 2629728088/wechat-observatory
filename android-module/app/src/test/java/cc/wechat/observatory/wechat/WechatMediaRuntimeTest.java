package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import cc.wechat.observatory.media.ImageDownloadRequestTracker;
import cc.wechat.observatory.media.MediaAttachmentProcessor;
import cc.wechat.observatory.media.MediaFileSelector;
import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatMediaRuntimeTest {
    @Test
    public void attachDetailedUsesWechatMediaServicesBoundary() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-wechat-runtime").toFile();
        File image = new File(appRoot, "image.jpg");
        writeJpeg(image);
        TestEnvironment environment = new TestEnvironment(appRoot);
        MessagePayload payload = new MessagePayload();

        MediaAttachmentProcessor.Result result = new WechatMediaRuntime(
                new ImageDownloadRequestTracker(1024),
                environment)
                .attachDetailed(new MediaAttachmentProcessor.Request(
                        payload,
                        3,
                        image.getAbsolutePath(),
                        Long.valueOf(1L),
                        Long.valueOf(2L),
                        123L,
                        "talker",
                        "",
                        "",
                        1L,
                        true,
                        2048L));

        assertTrue(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.ATTACHED, result.status());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, result.selectionStatus());
        assertEquals("image", payload.mediaKind);
        assertEquals("image/jpeg", payload.mediaMime);
        assertEquals("image.jpg", payload.mediaName);
        assertEquals(512, payload.mediaSize);
        assertEquals("encoded-512", payload.mediaBase64);
        assertTrue(environment.logs.isEmpty());
    }

    @Test
    public void resolvesHintAndImageInfoIdThroughHintRuntime() {
        WechatMediaRuntime runtime = new WechatMediaRuntime(
                new ImageDownloadRequestTracker(1024),
                new TestEnvironment(null));

        assertEquals("fallback.jpg", runtime.resolveMediaHint(3, Long.valueOf(1L), Long.valueOf(2L), "fallback.jpg"));
        assertEquals(0L, runtime.resolveImageInfoId(1L, 2L));
    }

    @Test
    public void nullEnvironmentIsSafe() {
        MessagePayload payload = new MessagePayload();
        WechatMediaRuntime runtime = new WechatMediaRuntime(new ImageDownloadRequestTracker(1024), null);

        MediaAttachmentProcessor.Result result = runtime.attachDetailed(new MediaAttachmentProcessor.Request(
                payload,
                3,
                "image.jpg",
                Long.valueOf(1L),
                Long.valueOf(2L),
                123L,
                "talker",
                "",
                "",
                1L,
                true,
                2048L));

        assertFalse(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.RUNTIME_UNAVAILABLE, result.status());
        assertNull(payload.mediaKind);
        assertEquals("fallback.jpg", runtime.resolveMediaHint(3, Long.valueOf(1L), Long.valueOf(2L), "fallback.jpg"));
        assertEquals(0L, runtime.resolveImageInfoId(1L, 2L));
    }

    @Test
    public void nullEnvironmentRemainsSafeAcrossRepeatedCalls() {
        WechatMediaRuntime runtime = new WechatMediaRuntime(new ImageDownloadRequestTracker(1024), null);

        assertEquals("first.jpg", runtime.resolveMediaHint(3, Long.valueOf(1L), Long.valueOf(2L), "first.jpg"));
        assertEquals("second.jpg", runtime.resolveMediaHint(3, Long.valueOf(3L), Long.valueOf(4L), "second.jpg"));
        assertEquals(0L, runtime.resolveImageInfoId(1L, 2L));
        assertEquals(0L, runtime.resolveImageInfoId(3L, 4L));
    }

    private static void writeJpeg(File file) throws Exception {
        byte[] bytes = new byte[512];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xd8;
        bytes[2] = (byte) 0xff;
        bytes[3] = (byte) 0xe0;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
    }

    private static final class TestEnvironment implements WechatMediaRuntime.Environment {
        private final File appRoot;
        final List<String> logs = new ArrayList<>();

        TestEnvironment(File appRoot) {
            this.appRoot = appRoot;
        }

        @Override
        public Object database() {
            return null;
        }

        @Override
        public File appRoot() {
            return appRoot;
        }

        @Override
        public ClassLoader classLoader() {
            return getClass().getClassLoader();
        }

        @Override
        public void runOnMainThread(Callable<Void> callable) throws Exception {
            callable.call();
        }

        @Override
        public void sleep(long millis) {
        }

        @Override
        public String encode(byte[] bytes) {
            return "encoded-" + bytes.length;
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public void onEmojiDiagnosticNeeded(String emojiMd5) {
            logs.add("emoji:" + emojiMd5);
        }
    }
}
