package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class MediaAttachmentProcessorServiceAdaptersTest {
    @Test
    public void encoderReturnsEmptyWhenServicesMissing() {
        assertEquals("", MediaAttachmentProcessorServiceAdapters.encoder(null)
                .encode(new byte[]{1, 2, 3}));
    }

    @Test
    public void encoderDelegatesToServices() {
        byte[] expected = new byte[]{1, 2, 3};

        String encoded = MediaAttachmentProcessorServiceAdapters.encoder(new TestServices() {
            @Override
            public String encode(byte[] bytes) {
                assertArrayEquals(expected, bytes);
                return "encoded";
            }
        }).encode(expected);

        assertEquals("encoded", encoded);
    }

    @Test
    public void loggerIgnoresMissingServicesAndForwardsMessages() {
        MediaAttachmentProcessorServiceAdapters.logger(null).log("ignored");
        List<String> logs = new ArrayList<>();

        MediaAttachmentProcessorServiceAdapters.logger(new TestServices() {
            @Override
            public void log(String message) {
                logs.add(message);
            }
        }).log("message");

        assertEquals(1, logs.size());
        assertEquals("message", logs.get(0));
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
