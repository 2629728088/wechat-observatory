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

public final class WechatImageDownloadCoordinatorAdaptersTest {
    @Test
    public void downloaderReturnsFalseWhenRequesterMissing() {
        assertFalse(WechatImageDownloadCoordinatorAdapters.downloader(null)
                .request(10L, 20L, "talker"));
    }

    @Test
    public void downloaderDelegatesRequest() {
        AtomicInteger calls = new AtomicInteger();

        boolean result = WechatImageDownloadCoordinatorAdapters.downloader((localId, serverId, talker) -> {
            calls.incrementAndGet();
            assertEquals(10L, localId);
            assertEquals(20L, serverId);
            assertEquals("talker", talker);
            return true;
        }).request(10L, 20L, "talker");

        assertTrue(result);
        assertEquals(1, calls.get());
    }

    @Test
    public void imageInfoResolverReturnsMissingWhenResolverMissing() {
        ImageDownloadResolution.Candidate candidate =
                WechatImageDownloadCoordinatorAdapters.imageInfoResolver(null)
                        .resolve(10L, 20L, "talker");

        assertTrue(candidate.isMissing());
    }

    @Test
    public void imageInfoResolverDelegatesCandidate() throws Exception {
        File image = Files.createTempFile("wxo-adapter-info", ".jpg").toFile();
        writeJpeg(image);
        ImageDownloadResolution.Candidate expected = ImageDownloadResolution.Candidate.fromFile(image);

        ImageDownloadResolution.Candidate candidate =
                WechatImageDownloadCoordinatorAdapters.imageInfoResolver((localId, serverId, talker) -> expected)
                        .resolve(10L, 20L, "talker");

        assertSame(expected, candidate);
    }

    @Test
    public void fallbackResolverReturnsMissingWhenResolverMissing() {
        ImageDownloadResolution.Candidate candidate =
                WechatImageDownloadCoordinatorAdapters.fallbackResolver(null)
                        .resolve("hint", 123L);

        assertTrue(candidate.isMissing());
    }

    @Test
    public void fallbackResolverDelegatesCandidate() {
        ImageDownloadResolution.Candidate expected = ImageDownloadResolution.Candidate.missing();

        ImageDownloadResolution.Candidate candidate =
                WechatImageDownloadCoordinatorAdapters.fallbackResolver((mediaHint, createTime) -> {
                    assertEquals("hint", mediaHint);
                    assertEquals(123L, createTime);
                    return expected;
                }).resolve("hint", 123L);

        assertSame(expected, candidate);
    }

    @Test
    public void loggerIgnoresMissingDelegateAndForwardsMessages() {
        WechatImageDownloadCoordinatorAdapters.logger(null).log("ignored");
        List<String> logs = new ArrayList<>();

        WechatImageDownloadCoordinatorAdapters.logger(logs::add).log("message");

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
}
