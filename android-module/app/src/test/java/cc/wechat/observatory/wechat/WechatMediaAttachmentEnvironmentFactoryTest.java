package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import cc.wechat.observatory.media.ImageDownloadResolution;
import cc.wechat.observatory.media.MediaAttachmentEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatMediaAttachmentEnvironmentFactoryTest {
    @Test
    public void createAdaptsWechatEnvironmentToMediaAttachmentEnvironment() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-wechat-media-env-factory").toFile();
        File image = new File(appRoot, "media.jpg");
        writeJpeg(image);
        AtomicInteger logs = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();

        MediaAttachmentEnvironment environment = WechatMediaAttachmentEnvironmentFactory.create(
                new TestEnvironment(appRoot, image.getAbsolutePath(), logs),
                new WechatMediaAttachmentServices.ImageDownloadRequester() {
                    @Override
                    public boolean request(long localId, long serverId, String talker) {
                        downloads.incrementAndGet();
                        return localId == 10L && serverId == 20L && "talker".equals(talker);
                    }
                });

        ImageDownloadResolution.Candidate direct =
                environment.resolveMediaFileCandidate(3, image.getAbsolutePath(), 123L, "");
        ImageDownloadResolution.Candidate fromHint =
                environment.resolveImageInfoCandidate(10L, 20L, "talker");

        assertTrue(direct.isImageFile());
        assertTrue(fromHint.isImageFile());
        assertEquals(image.getCanonicalFile(), direct.file().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), fromHint.file().getCanonicalFile());
        assertTrue(environment.requestImageDownload(10L, 20L, "talker"));
        assertEquals(1, downloads.get());
        assertEquals("encoded-3", environment.encode(new byte[]{1, 2, 3}));
        environment.log("message");
        assertTrue(logs.get() > 0);
    }

    @Test
    public void createKeepsRequesterAvailableWhenWechatEnvironmentIsMissing() {
        AtomicInteger downloads = new AtomicInteger();

        MediaAttachmentEnvironment environment = WechatMediaAttachmentEnvironmentFactory.create(
                null,
                new WechatMediaAttachmentServices.ImageDownloadRequester() {
                    @Override
                    public boolean request(long localId, long serverId, String talker) {
                        downloads.incrementAndGet();
                        return localId == 7L && serverId == 8L && "talker".equals(talker);
                    }
                });

        assertTrue(environment.resolveMediaFileCandidate(3, "media.jpg", 0L, "").isMissing());
        assertTrue(environment.requestImageDownload(7L, 8L, "talker"));
        assertEquals(1, downloads.get());
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

    private static final class TestEnvironment implements WechatMediaAttachmentServices.Environment {
        private final File appRoot;
        private final String resolvedHint;
        private final AtomicInteger logs;

        TestEnvironment(File appRoot, String resolvedHint, AtomicInteger logs) {
            this.appRoot = appRoot;
            this.resolvedHint = resolvedHint;
            this.logs = logs;
        }

        @Override
        public File appRoot() {
            return appRoot;
        }

        @Override
        public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
            return resolvedHint;
        }

        @Override
        public ClassLoader classLoader() {
            return getClass().getClassLoader();
        }

        @Override
        public long resolveImageInfoId(long localId, long serverId) {
            return localId + serverId;
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
