package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentProcessorFactoryTest {
    @Test
    public void createWiresBaseCandidateAndEncoder() throws Exception {
        File image = Files.createTempFile("wxo-factory-base", ".jpg").toFile();
        writeJpeg(image);
        MessagePayload payload = new MessagePayload();
        AtomicInteger downloads = new AtomicInteger();

        MediaAttachmentProcessor.Result result = MediaAttachmentProcessorFactory.create(
                new ImageDownloadRequestTracker(1024),
                new TestEnvironment() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        return ImageDownloadResolution.Candidate.fromFile(image);
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloads.incrementAndGet();
                        return true;
                    }

                    @Override
                    public String encode(byte[] bytes) {
                        return "factory-" + bytes.length;
                    }
                })
                .attachDetailed(request(payload));

        assertTrue(result.isAttached());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, result.selectionStatus());
        assertEquals("factory-512", payload.mediaBase64);
        assertEquals(0, downloads.get());
    }

    @Test
    public void createWiresDownloadFallbackCandidate() throws Exception {
        File image = Files.createTempFile("wxo-factory-download", ".jpg").toFile();
        writeJpeg(image);
        MessagePayload payload = new MessagePayload();
        AtomicInteger baseResolves = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();

        MediaAttachmentProcessor.Result result = MediaAttachmentProcessorFactory.create(
                new ImageDownloadRequestTracker(1024),
                new TestEnvironment() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        return ImageDownloadResolution.Candidate.fromFile(
                                baseResolves.incrementAndGet() == 1 ? null : image);
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloads.incrementAndGet();
                        return true;
                    }

                    @Override
                    public String encode(byte[] bytes) {
                        return "download-" + bytes.length;
                    }
                })
                .attachDetailed(request(payload));

        assertTrue(result.isAttached());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE, result.selectionStatus());
        assertEquals("download-512", payload.mediaBase64);
        assertEquals(2, baseResolves.get());
        assertEquals(1, downloads.get());
    }

    private static MediaAttachmentProcessor.Request request(MessagePayload payload) {
        return new MediaAttachmentProcessor.Request(
                payload,
                3,
                "media.jpg",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "",
                12L,
                true,
                2048L);
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

    private static class TestEnvironment implements MediaAttachmentServices {
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
