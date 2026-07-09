package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import cc.wechat.observatory.media.ImageDownloadResolution;
import cc.wechat.observatory.media.MediaHintRuntime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatMediaRuntimeFactoryTest {
    @Test
    public void mediaHintRuntimeIsSafeWithoutEnvironment() {
        MediaHintRuntime runtime = WechatMediaRuntimeFactory.mediaHintRuntime(null);

        assertEquals("fallback.jpg", runtime.resolve(3, Long.valueOf(1L), Long.valueOf(2L), "fallback.jpg"));
        assertEquals(0L, runtime.resolveImageInfoId(1L, 2L));
    }

    @Test
    public void mediaAttachmentServicesReturnsNullWithoutEnvironment() {
        assertNull(WechatMediaRuntimeFactory.mediaAttachmentServices(null, null));
    }

    @Test
    public void mediaAttachmentServicesUsesRuntimeHintResolverForImageInfoCandidate() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-factory").toFile();
        File image = new File(appRoot, "resolved.jpg");
        writeJpeg(image);
        AtomicInteger logs = new AtomicInteger();
        AtomicInteger hintCalls = new AtomicInteger();
        TestEnvironment environment = new TestEnvironment(appRoot, logs);

        WechatMediaAttachmentServices services = WechatMediaRuntimeFactory.mediaAttachmentServices(
                environment,
                new WechatMediaRuntimeFactory.HintResolver() {
                    @Override
                    public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
                        hintCalls.incrementAndGet();
                        assertEquals(3, type);
                        assertEquals(Long.valueOf(10L), msgId);
                        assertEquals(Long.valueOf(20L), msgSvrId);
                        return image.getAbsolutePath();
                    }

                    @Override
                    public long resolveImageInfoId(long localId, long serverId) {
                        return localId + serverId;
                    }
                });

        ImageDownloadResolution.Candidate candidate = services.resolveImageInfoCandidate(10L, 20L, "talker");

        assertTrue(candidate.isImageFile());
        assertEquals(image.getCanonicalFile(), candidate.file().getCanonicalFile());
        assertEquals(1, hintCalls.get());
        assertEquals("encoded-3", services.encode(new byte[]{1, 2, 3}));
        services.log("message");
        assertTrue(logs.get() > 0);
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
        private final AtomicInteger logs;

        TestEnvironment(File appRoot, AtomicInteger logs) {
            this.appRoot = appRoot;
            this.logs = logs;
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
            logs.incrementAndGet();
        }

        @Override
        public void onEmojiDiagnosticNeeded(String emojiMd5) {
            logs.incrementAndGet();
        }
    }
}
