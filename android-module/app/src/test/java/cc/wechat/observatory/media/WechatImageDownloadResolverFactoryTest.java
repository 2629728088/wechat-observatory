package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadResolverFactoryTest {
    @Test
    public void createWiresImageInfoCandidateResolver() throws Exception {
        File image = Files.createTempFile("wxo-image-download-info", ".jpg").toFile();
        writeJpeg(image);
        AtomicInteger downloads = new AtomicInteger();
        List<String> logs = new ArrayList<>();

        ImageDownloadResolution resolution = WechatImageDownloadResolverFactory.create(
                new ImageDownloadRequestTracker(1024),
                new TestServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveImageInfoCandidate(
                            long localId, long serverId, String talker) {
                        return ImageDownloadResolution.Candidate.fromFile(image);
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloads.incrementAndGet();
                        return true;
                    }

                    @Override
                    public void log(String message) {
                        logs.add(message);
                    }
                })
                .resolve("media.jpg", Long.valueOf(12L), Long.valueOf(34L), 123456L, "talker");

        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_FILE, resolution.status());
        assertEquals(image.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(1, downloads.get());
        assertTrue(logsContain(logs, "image media resolved from ImgInfo"));
    }

    @Test
    public void createWiresFallbackResolverAfterDownloadRequest() throws Exception {
        File image = Files.createTempFile("wxo-image-download-fallback", ".jpg").toFile();
        writeJpeg(image);
        AtomicInteger fallbackResolves = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();

        ImageDownloadResolution resolution = WechatImageDownloadResolverFactory.create(
                new ImageDownloadRequestTracker(1024),
                new TestServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        fallbackResolves.incrementAndGet();
                        assertEquals(MediaFiles.MESSAGE_TYPE_IMAGE, type);
                        assertEquals("media.jpg", mediaHint);
                        assertEquals(123456L, createTime);
                        assertEquals("", emojiMd5);
                        return ImageDownloadResolution.Candidate.fromFile(image);
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloads.incrementAndGet();
                        return true;
                    }
                })
                .resolve("media.jpg", Long.valueOf(12L), Long.valueOf(34L), 123456L, "talker");

        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, resolution.status());
        assertEquals(image.getCanonicalFile(), resolution.file().getCanonicalFile());
        assertEquals(1, fallbackResolves.get());
        assertEquals(1, downloads.get());
    }

    @Test
    public void nullServicesReturnNotReadyWithoutFile() {
        ImageDownloadResolution resolution = WechatImageDownloadResolverFactory.create(
                new ImageDownloadRequestTracker(1024),
                null)
                .resolve("media.jpg", Long.valueOf(12L), Long.valueOf(34L), 123456L, "talker");

        assertEquals(ImageDownloadResolution.Status.NOT_READY, resolution.status());
        assertNull(resolution.file());
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

    private static boolean logsContain(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
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
