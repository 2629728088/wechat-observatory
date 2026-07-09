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

public final class WechatImageDownloadServiceAdaptersTest {
    @Test
    public void downloadRequesterReturnsFalseWhenServicesMissing() {
        assertFalse(WechatImageDownloadServiceAdapters.downloadRequester(null)
                .request(10L, 20L, "talker"));
    }

    @Test
    public void downloadRequesterDelegatesToServices() {
        AtomicInteger calls = new AtomicInteger();

        boolean result = WechatImageDownloadServiceAdapters.downloadRequester(new TestServices() {
            @Override
            public boolean requestImageDownload(long localId, long serverId, String talker) {
                calls.incrementAndGet();
                assertEquals(10L, localId);
                assertEquals(20L, serverId);
                assertEquals("talker", talker);
                return true;
            }
        }).request(10L, 20L, "talker");

        assertTrue(result);
        assertEquals(1, calls.get());
    }

    @Test
    public void imageInfoResolverReturnsMissingWhenServicesMissing() {
        ImageDownloadResolution.Candidate candidate =
                WechatImageDownloadServiceAdapters.imageInfoResolver(null)
                        .resolve(10L, 20L, "talker");

        assertTrue(candidate.isMissing());
    }

    @Test
    public void imageInfoResolverDelegatesToServices() throws Exception {
        File image = Files.createTempFile("wxo-service-info", ".jpg").toFile();
        writeJpeg(image);
        ImageDownloadResolution.Candidate expected = ImageDownloadResolution.Candidate.fromFile(image);

        ImageDownloadResolution.Candidate candidate =
                WechatImageDownloadServiceAdapters.imageInfoResolver(new TestServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveImageInfoCandidate(
                            long localId, long serverId, String talker) {
                        assertEquals(10L, localId);
                        assertEquals(20L, serverId);
                        assertEquals("talker", talker);
                        return expected;
                    }
                }).resolve(10L, 20L, "talker");

        assertSame(expected, candidate);
    }

    @Test
    public void fallbackResolverReturnsMissingWhenServicesMissing() {
        ImageDownloadResolution.Candidate candidate =
                WechatImageDownloadServiceAdapters.fallbackResolver(null)
                        .resolve("media.jpg", 123L);

        assertTrue(candidate.isMissing());
    }

    @Test
    public void fallbackResolverDelegatesImageTypeToServices() {
        ImageDownloadResolution.Candidate expected = ImageDownloadResolution.Candidate.missing();

        ImageDownloadResolution.Candidate candidate =
                WechatImageDownloadServiceAdapters.fallbackResolver(new TestServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        assertEquals(MediaFiles.MESSAGE_TYPE_IMAGE, type);
                        assertEquals("media.jpg", mediaHint);
                        assertEquals(123L, createTime);
                        assertEquals("", emojiMd5);
                        return expected;
                    }
                }).resolve("media.jpg", 123L);

        assertSame(expected, candidate);
    }

    @Test
    public void fallbackResolverDoesNotUseEmojiMd5ForDownloadedImages() {
        WechatImageDownloadServiceAdapters.fallbackResolver(new TestServices() {
            @Override
            public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                    int type, String mediaHint, long createTime, String emojiMd5) {
                assertEquals(MediaFiles.MESSAGE_TYPE_IMAGE, type);
                assertEquals("", emojiMd5);
                return ImageDownloadResolution.Candidate.missing();
            }
        }).resolve("media.jpg", 123L);
    }

    @Test
    public void loggerIgnoresMissingServicesAndForwardsMessages() {
        WechatImageDownloadServiceAdapters.logger(null).log("ignored");
        List<String> logs = new ArrayList<>();

        WechatImageDownloadServiceAdapters.logger(new TestServices() {
            @Override
            public void log(String message) {
                logs.add(message);
            }
        }).log("message");

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
