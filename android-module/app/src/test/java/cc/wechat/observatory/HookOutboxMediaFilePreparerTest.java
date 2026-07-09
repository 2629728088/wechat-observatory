package cc.wechat.observatory;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;

import cc.wechat.observatory.outbox.OutboxMediaFilePreparer;
import cc.wechat.observatory.outbox.OutboxMediaSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class HookOutboxMediaFilePreparerTest {
    @Test
    public void missingMediaUrlDoesNotDownload() throws Exception {
        CapturingDownloader downloader = new CapturingDownloader(null);
        OutboxMediaFilePreparer preparer = new OutboxMediaFilePreparer(downloader, null);

        OutboxMediaFilePreparer.PreparedMedia prepared =
                preparer.prepare((OutboxMediaSpec) null, "image", false);

        assertFalse(prepared.ok());
        assertEquals("image media_url is required", prepared.error);
        assertEquals(0, downloader.calls);
    }

    @Test
    public void prepareDownloadsMediaAndPreservesFileNameFlag() throws Exception {
        File mediaFile = tempFile("photo.jpg", new byte[]{1, 2, 3});
        CapturingDownloader downloader = new CapturingDownloader(mediaFile);
        OutboxMediaFilePreparer preparer = new OutboxMediaFilePreparer(downloader, null);

        OutboxMediaFilePreparer.PreparedMedia prepared =
                preparer.prepare(spec("/module/media/photo.jpg", "photo.jpg", 1000), "file", true);

        assertTrue(prepared.ok());
        assertEquals(mediaFile.getCanonicalFile(), prepared.file.getCanonicalFile());
        assertNotNull(downloader.media);
        assertEquals("/module/media/photo.jpg", downloader.media.mediaUrl);
        assertEquals("photo.jpg", downloader.media.mediaName);
        assertTrue(downloader.preserveName);
    }

    @Test
    public void emptyDownloadedFileReportsErrorAndCanBeCleanedUp() throws Exception {
        File mediaFile = tempFile("empty.jpg", new byte[0]);
        CapturingDownloader downloader = new CapturingDownloader(mediaFile);
        OutboxMediaFilePreparer preparer = new OutboxMediaFilePreparer(downloader, null);

        OutboxMediaFilePreparer.PreparedMedia prepared =
                preparer.prepare(spec("/module/media/empty.jpg", "empty.jpg", 1000), "image", false);

        assertFalse(prepared.ok());
        assertEquals("image media download produced empty file", prepared.error);
        prepared.cleanup("image");
        assertFalse(mediaFile.exists());
    }

    @Test
    public void retainedPreparedMediaIsNotDeletedDuringCleanup() throws Exception {
        File mediaFile = tempFile("video.mp4", new byte[]{1, 2, 3});
        OutboxMediaFilePreparer preparer =
                new OutboxMediaFilePreparer(new CapturingDownloader(mediaFile), null);

        OutboxMediaFilePreparer.PreparedMedia prepared =
                preparer.prepare(spec("/module/media/video.mp4", "video.mp4", 1000), "video", false);

        assertTrue(prepared.ok());
        prepared.retain();
        prepared.cleanup("video");
        assertTrue(mediaFile.isFile());
    }

    @Test
    public void voiceRejectsUnsupportedMediaAndCleansTempFile() throws Exception {
        File mediaFile = tempFile("voice.bin", new byte[]{1, 2, 3});
        OutboxMediaFilePreparer preparer =
                new OutboxMediaFilePreparer(new CapturingDownloader(mediaFile), null);

        OutboxMediaFilePreparer.PreparedMedia prepared =
                preparer.prepareVoice(spec("/module/media/voice.bin", "voice.bin", 1000));

        assertFalse(prepared.ok());
        assertEquals("voice media must be AMR or SILK", prepared.error);
        prepared.cleanup("voice");
        assertFalse(mediaFile.exists());
    }

    private static OutboxMediaSpec spec(String mediaUrl, String mediaName, int durationMs) throws Exception {
        Constructor<OutboxMediaSpec> constructor = OutboxMediaSpec.class.getDeclaredConstructor(
                String.class,
                String.class,
                int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(mediaUrl, mediaName, durationMs);
    }

    private static File tempFile(String name, byte[] bytes) throws Exception {
        File dir = Files.createTempDirectory("wxo-preparer").toFile();
        File file = new File(dir, name);
        Files.write(file.toPath(), bytes);
        return file;
    }

    private static final class CapturingDownloader implements OutboxMediaFilePreparer.Downloader {
        private final File file;
        int calls;
        boolean preserveName;
        OutboxMediaSpec media;

        CapturingDownloader(File file) {
            this.file = file;
        }

        @Override
        public File download(OutboxMediaSpec media, boolean preserveName) {
            calls++;
            this.media = media;
            this.preserveName = preserveName;
            return file;
        }
    }

}
