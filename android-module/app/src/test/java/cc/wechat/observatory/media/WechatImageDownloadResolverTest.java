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

public final class WechatImageDownloadResolverTest {
    @Test
    public void resolveRequestsDownloadOnceAndUsesFallbackFile() throws Exception {
        File image = Files.createTempFile("wxo-download-resolver", ".jpg").toFile();
        writeJpeg(image);
        AtomicInteger requestCount = new AtomicInteger();
        AtomicInteger fallbackCount = new AtomicInteger();
        List<String> logs = new ArrayList<>();
        WechatImageDownloadResolver resolver = new WechatImageDownloadResolver(
                new ImageDownloadRequestTracker(1024),
                (localId, serverId, talker) -> {
                    requestCount.incrementAndGet();
                    return true;
                },
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.missing(),
                (mediaHint, createTime) -> {
                    fallbackCount.incrementAndGet();
                    return ImageDownloadResolution.Candidate.fromFile(image);
                },
                logs::add);

        ImageDownloadResolution first = resolver.resolve("hint.jpg", 10L, 20L, 123L, "talker");
        ImageDownloadResolution second = resolver.resolve("hint.jpg", 10L, 20L, 123L, "talker");

        assertEquals(image.getCanonicalFile(), first.file().getCanonicalFile());
        assertEquals(image.getCanonicalFile(), second.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, first.status());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, second.status());
        assertEquals(1, requestCount.get());
        assertEquals(2, fallbackCount.get());
        assertTrue(contains(logs, "after NetSceneGetMsgImg"));
    }

    @Test
    public void resolveSkipsRequestWhenIdsAreMissing() {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicInteger fallbackCount = new AtomicInteger();
        WechatImageDownloadResolver resolver = new WechatImageDownloadResolver(
                new ImageDownloadRequestTracker(1024),
                (localId, serverId, talker) -> {
                    requestCount.incrementAndGet();
                    return true;
                },
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.missing(),
                (mediaHint, createTime) -> {
                    fallbackCount.incrementAndGet();
                    return ImageDownloadResolution.Candidate.missing();
                },
                message -> {
                });

        ImageDownloadResolution resolution = resolver.resolve("hint.jpg", 0L, 0L, 123L, "talker");

        assertNull(resolution.file());
        assertEquals(ImageDownloadResolution.Status.NOT_READY, resolution.status());
        assertEquals(0, requestCount.get());
        assertEquals(0, fallbackCount.get());
    }

    @Test
    public void resolveUsesImageInfoBeforeFallback() throws Exception {
        File image = Files.createTempFile("wxo-download-info", ".jpg").toFile();
        writeJpeg(image);
        AtomicInteger fallbackCount = new AtomicInteger();
        WechatImageDownloadResolver resolver = new WechatImageDownloadResolver(
                new ImageDownloadRequestTracker(1024),
                (localId, serverId, talker) -> true,
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.fromFile(image),
                (mediaHint, createTime) -> {
                    fallbackCount.incrementAndGet();
                    return ImageDownloadResolution.Candidate.missing();
                },
                message -> {
                });

        ImageDownloadResolution resolved = resolver.resolve("hint.jpg", 10L, 20L, 123L, "talker");

        assertEquals(image.getCanonicalFile(), resolved.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_FILE, resolved.status());
        assertEquals(0, fallbackCount.get());
    }

    @Test
    public void resolveDoesNotRequestDownloadWhenLocalIdIsMissing() {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicInteger fallbackCount = new AtomicInteger();
        List<String> logs = new ArrayList<>();
        WechatImageDownloadResolver resolver = new WechatImageDownloadResolver(
                new ImageDownloadRequestTracker(1024),
                (localId, serverId, talker) -> {
                    requestCount.incrementAndGet();
                    return true;
                },
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.missing(),
                (mediaHint, createTime) -> {
                    fallbackCount.incrementAndGet();
                    return ImageDownloadResolution.Candidate.missing();
                },
                logs::add);

        ImageDownloadResolution resolution = resolver.resolve("hint.jpg", 0L, 20L, 123L, "talker");

        assertNull(resolution.file());
        assertEquals(ImageDownloadResolution.Status.NOT_READY, resolution.status());
        assertEquals(0, requestCount.get());
        assertEquals(1, fallbackCount.get());
        assertTrue(contains(logs, "image local retry"));
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

    private static boolean contains(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
