package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MediaSelectionBaseCandidateTest {
    @Test
    public void missingResolverOrRequestReturnsMissingCandidate() {
        assertTrue(new MediaSelectionBaseCandidate(null).resolve(request()).isMissing());

        AtomicInteger calls = new AtomicInteger();
        ImageDownloadResolution.Candidate candidate = new MediaSelectionBaseCandidate(
                (type, mediaHint, createTime, emojiMd5) -> {
                    calls.incrementAndGet();
                    return ImageDownloadResolution.Candidate.missing();
                })
                .resolve(null);

        assertTrue(candidate.isMissing());
        assertEquals(0, calls.get());
    }

    @Test
    public void nullResolverResultReturnsMissingCandidate() {
        ImageDownloadResolution.Candidate candidate = new MediaSelectionBaseCandidate(
                (type, mediaHint, createTime, emojiMd5) -> null)
                .resolve(request());

        assertTrue(candidate.isMissing());
    }

    @Test
    public void resolverReceivesRequestFields() throws Exception {
        File image = Files.createTempFile("wxo-base-candidate", ".jpg").toFile();
        writeJpeg(image);

        ImageDownloadResolution.Candidate candidate = new MediaSelectionBaseCandidate(
                (type, mediaHint, createTime, emojiMd5) -> {
                    assertEquals(3, type);
                    assertEquals("media.jpg", mediaHint);
                    assertEquals(123456L, createTime);
                    assertEquals("abcdefabcdefabcdefabcdefabcdefab", emojiMd5);
                    return ImageDownloadResolution.Candidate.fromFile(image);
                })
                .resolve(request());

        assertEquals(image.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    private static MediaFileSelector.Request request() {
        return new MediaFileSelector.Request(
                3,
                "media.jpg",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "abcdefabcdefabcdefabcdefabcdefab",
                12L);
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
