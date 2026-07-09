package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentSelectorFactoryTest {
    @Test
    public void createWiresBaseResolver() throws Exception {
        File image = Files.createTempFile("wxo-selector-factory-base", ".jpg").toFile();
        writeJpeg(image);

        MediaFileSelector.Selection selection = MediaAttachmentSelectorFactory.create(
                new ImageDownloadRequestTracker(1024),
                new TestServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        return ImageDownloadResolution.Candidate.fromFile(image);
                    }
                })
                .selectDetailed(request());

        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, selection.status());
        assertEquals(image.getCanonicalFile(), selection.file().getCanonicalFile());
    }

    @Test
    public void createWiresImageDownloadFallbackResolver() throws Exception {
        File image = Files.createTempFile("wxo-selector-factory-download", ".jpg").toFile();
        writeJpeg(image);
        AtomicInteger resolves = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();

        MediaFileSelector.Selection selection = MediaAttachmentSelectorFactory.create(
                new ImageDownloadRequestTracker(1024),
                new TestServices() {
                    @Override
                    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
                            int type, String mediaHint, long createTime, String emojiMd5) {
                        return ImageDownloadResolution.Candidate.fromFile(
                                resolves.incrementAndGet() == 1 ? null : image);
                    }

                    @Override
                    public boolean requestImageDownload(long localId, long serverId, String talker) {
                        downloads.incrementAndGet();
                        return true;
                    }
                })
                .selectDetailed(request());

        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE, selection.status());
        assertEquals(image.getCanonicalFile(), selection.file().getCanonicalFile());
        assertEquals(2, resolves.get());
        assertEquals(1, downloads.get());
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
                "",
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
