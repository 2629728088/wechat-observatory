package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadResolverDependenciesTest {
    @Test
    public void fromServicesReturnsSafeAdaptersWhenServicesMissing() {
        WechatImageDownloadResolverDependencies dependencies =
                WechatImageDownloadResolverDependencies.fromServices(null);

        assertFalse(dependencies.downloadRequester().request(10L, 20L, "talker"));
        assertTrue(dependencies.imageInfoResolver().resolve(10L, 20L, "talker").isMissing());
        assertTrue(dependencies.fallbackResolver().resolve("media.jpg", 123L).isMissing());
        dependencies.logger().log("ignored");
    }

    @Test
    public void fromServicesDelegatesAllResolverDependencies() throws Exception {
        File imageInfo = Files.createTempFile("wxo-deps-info", ".jpg").toFile();
        File fallback = Files.createTempFile("wxo-deps-fallback", ".jpg").toFile();
        writeJpeg(imageInfo);
        writeJpeg(fallback);
        ImageDownloadResolution.Candidate infoCandidate =
                ImageDownloadResolution.Candidate.fromFile(imageInfo);
        ImageDownloadResolution.Candidate fallbackCandidate =
                ImageDownloadResolution.Candidate.fromFile(fallback);
        AtomicInteger downloads = new AtomicInteger();
        List<String> logs = new ArrayList<>();

        WechatImageDownloadResolverDependencies dependencies =
                WechatImageDownloadResolverDependencies.fromServices(new TestServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        assertEquals(MediaFiles.MESSAGE_TYPE_IMAGE, type);
                        assertEquals("media.jpg", mediaHint);
                        assertEquals(123L, createTime);
                        assertEquals("", emojiMd5);
                        return fallbackCandidate;
                    }

                    @Override
                    public ImageDownloadResolution.Candidate resolveImageInfoCandidate(
                            long localId, long serverId, String talker) {
                        assertEquals(10L, localId);
                        assertEquals(20L, serverId);
                        assertEquals("talker", talker);
                        return infoCandidate;
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloads.incrementAndGet();
                        assertEquals(10L, localId);
                        assertEquals(20L, serverId);
                        assertEquals("talker", talker);
                        return true;
                    }

                    @Override
                    public void log(String message) {
                        logs.add(message);
                    }
                });

        assertTrue(dependencies.downloadRequester().request(10L, 20L, "talker"));
        assertSame(infoCandidate, dependencies.imageInfoResolver().resolve(10L, 20L, "talker"));
        assertSame(fallbackCandidate, dependencies.fallbackResolver().resolve("media.jpg", 123L));
        dependencies.logger().log("message");
        assertEquals(1, downloads.get());
        assertEquals(1, logs.size());
        assertEquals("message", logs.get(0));
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

    private static class TestServices implements MediaAttachmentServices {
        @Override
        public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                int type, String mediaHint, long createTime, String emojiMd5) {
            return ImageDownloadResolution.Candidate.missing();
        }

        @Override
        public boolean requestImageDownload(long localId, long serverId, String talker) {
            return false;
        }

        @Override
        public String encode(byte[] bytes) {
            return "";
        }

        @Override
        public void log(String message) {
        }
    }
}
