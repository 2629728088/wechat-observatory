package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentRuntimeTest {
    @Test
    public void attachUsesEnvironmentResolverAndEncoder() throws Exception {
        MessagePayload payload = new MessagePayload();
        payload.id = "runtime-image";
        File image = Files.createTempFile("wxo-runtime", ".jpg").toFile();
        writeJpeg(image);
        AtomicInteger resolveCalls = new AtomicInteger();
        AtomicInteger downloadRequests = new AtomicInteger();

        boolean attached = new MediaAttachmentRuntime(
                new ImageDownloadRequestTracker(1024),
                new MediaAttachmentServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        resolveCalls.incrementAndGet();
                        return ImageDownloadResolution.Candidate.fromFile(image);
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloadRequests.incrementAndGet();
                        return true;
                    }

                    @Override
                    public String encode(byte[] bytes) {
                        return "runtime-" + bytes.length;
                    }

                    @Override
                    public void log(String message) {
                    }
                })
                .attach(request(payload, 3, true));

        assertTrue(attached);
        assertEquals("image", payload.mediaKind);
        assertEquals("image/jpeg", payload.mediaMime);
        assertEquals(image.getName(), payload.mediaName);
        assertEquals(512, payload.mediaSize);
        assertEquals("runtime-512", payload.mediaBase64);
        assertEquals(1, resolveCalls.get());
        assertEquals(0, downloadRequests.get());
    }

    @Test
    public void attachDetailedReturnsProcessorResult() throws Exception {
        MessagePayload payload = new MessagePayload();
        File image = Files.createTempFile("wxo-runtime-detailed", ".jpg").toFile();
        writeJpeg(image);

        MediaAttachmentProcessor.Result result = new MediaAttachmentRuntime(
                new ImageDownloadRequestTracker(1024),
                new MediaAttachmentServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        return ImageDownloadResolution.Candidate.fromFile(image);
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        return true;
                    }

                    @Override
                    public String encode(byte[] bytes) {
                        return "runtime-detailed-" + bytes.length;
                    }

                    @Override
                    public void log(String message) {
                    }
                })
                .attachDetailed(request(payload, 3, true));

        assertTrue(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.ATTACHED, result.status());
        assertEquals("image", result.mediaKind());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, result.selectionStatus());
        assertNull(result.writerStatus());
        assertEquals("runtime-detailed-512", payload.mediaBase64);
    }

    @Test
    public void attachRequestsImageDownloadAndUsesFallbackResolver() throws Exception {
        MessagePayload payload = new MessagePayload();
        payload.id = "runtime-download";
        File image = Files.createTempFile("wxo-runtime-download", ".jpg").toFile();
        writeJpeg(image);
        AtomicInteger resolveCalls = new AtomicInteger();
        AtomicInteger downloadRequests = new AtomicInteger();

        boolean attached = new MediaAttachmentRuntime(
                new ImageDownloadRequestTracker(1024),
                new MediaAttachmentServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        return ImageDownloadResolution.Candidate.fromFile(
                                resolveCalls.incrementAndGet() == 1 ? null : image);
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloadRequests.incrementAndGet();
                        return true;
                    }

                    @Override
                    public String encode(byte[] bytes) {
                        return "downloaded-" + bytes.length;
                    }

                    @Override
                    public void log(String message) {
                    }
                })
                .attach(request(payload, 3, true));

        assertTrue(attached);
        assertEquals("image", payload.mediaKind);
        assertEquals("downloaded-512", payload.mediaBase64);
        assertEquals(2, resolveCalls.get());
        assertEquals(1, downloadRequests.get());
    }

    @Test
    public void attachUsesImageInfoResolverBeforeFallbackAfterDownload() throws Exception {
        MessagePayload payload = new MessagePayload();
        payload.id = "runtime-image-info";
        File image = Files.createTempFile("wxo-runtime-info", ".jpg").toFile();
        writeJpeg(image);
        AtomicInteger baseResolveCalls = new AtomicInteger();
        AtomicInteger imageInfoResolveCalls = new AtomicInteger();
        AtomicInteger downloadRequests = new AtomicInteger();

        boolean attached = new MediaAttachmentRuntime(
                new ImageDownloadRequestTracker(1024),
                new MediaAttachmentServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        baseResolveCalls.incrementAndGet();
                        return ImageDownloadResolution.Candidate.missing();
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloadRequests.incrementAndGet();
                        return true;
                    }

                    @Override
                    public ImageDownloadResolution.Candidate resolveImageInfoCandidate(long localId, long serverId, String talker) {
                        imageInfoResolveCalls.incrementAndGet();
                        return ImageDownloadResolution.Candidate.fromFile(image);
                    }

                    @Override
                    public String encode(byte[] bytes) {
                        return "info-" + bytes.length;
                    }

                    @Override
                    public void log(String message) {
                    }
                })
                .attach(request(payload, 3, true));

        assertTrue(attached);
        assertEquals("image", payload.mediaKind);
        assertEquals("info-512", payload.mediaBase64);
        assertEquals(1, baseResolveCalls.get());
        assertEquals(1, imageInfoResolveCalls.get());
        assertEquals(1, downloadRequests.get());
    }

    @Test
    public void attachReturnsFalseWithoutEnvironment() {
        MessagePayload payload = new MessagePayload();

        boolean attached = new MediaAttachmentRuntime(new ImageDownloadRequestTracker(1024), null)
                .attach(request(payload, 3, true));

        assertFalse(attached);
        assertNull(payload.mediaKind);
    }

    @Test
    public void attachDetailedReportsRuntimeUnavailable() {
        MessagePayload payload = new MessagePayload();

        MediaAttachmentProcessor.Result result = new MediaAttachmentRuntime(new ImageDownloadRequestTracker(1024), null)
                .attachDetailed(request(payload, 3, true));

        assertFalse(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.RUNTIME_UNAVAILABLE, result.status());
        assertEquals("", result.mediaKind());
        assertNull(result.selectionStatus());
        assertNull(result.writerStatus());
        assertNull(payload.mediaKind);
    }

    private static MediaAttachmentProcessor.Request request(MessagePayload payload, int type, boolean uploadEnabled) {
        return new MediaAttachmentProcessor.Request(
                payload,
                type,
                "media.jpg",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "",
                12L,
                uploadEnabled,
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
}
