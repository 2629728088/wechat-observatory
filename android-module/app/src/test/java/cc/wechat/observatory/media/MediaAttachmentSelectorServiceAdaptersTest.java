package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentSelectorServiceAdaptersTest {
    @Test
    public void baseResolverReturnsMissingWhenServicesMissing() {
        ImageDownloadResolution.Candidate candidate =
                MediaAttachmentSelectorServiceAdapters.baseResolver(null)
                        .resolve(MediaFiles.MESSAGE_TYPE_IMAGE, "media.jpg", 123L, "emoji");

        assertTrue(candidate.isMissing());
    }

    @Test
    public void baseResolverDelegatesToServices() throws Exception {
        File image = Files.createTempFile("wxo-selector-service-base", ".jpg").toFile();
        writeJpeg(image);
        ImageDownloadResolution.Candidate expected = ImageDownloadResolution.Candidate.fromFile(image);

        ImageDownloadResolution.Candidate candidate =
                MediaAttachmentSelectorServiceAdapters.baseResolver(new TestServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        assertEquals(MediaFiles.MESSAGE_TYPE_IMAGE, type);
                        assertEquals("media.jpg", mediaHint);
                        assertEquals(123L, createTime);
                        assertEquals("emoji", emojiMd5);
                        return expected;
                    }
                }).resolve(MediaFiles.MESSAGE_TYPE_IMAGE, "media.jpg", 123L, "emoji");

        assertSame(expected, candidate);
    }

    @Test
    public void loggerIgnoresMissingServicesAndForwardsMessages() {
        MediaAttachmentSelectorServiceAdapters.logger(null).log("ignored");
        List<String> logs = new ArrayList<>();

        MediaAttachmentSelectorServiceAdapters.logger(new TestServices() {
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
