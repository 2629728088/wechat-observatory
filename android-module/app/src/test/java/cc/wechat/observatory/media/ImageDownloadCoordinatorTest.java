package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadCoordinatorTest {
    @Test
    public void requestAndResolveEnqueuesOnlyFirstRequest() throws Exception {
        File downloaded = Files.createTempFile("wxo-coordinator-downloaded", ".jpg").toFile();
        writeJpeg(downloaded);
        CountingDownloader downloader = new CountingDownloader();
        List<String> logs = new ArrayList<>();
        ImageDownloadCoordinator coordinator = new ImageDownloadCoordinator(
                new ImageDownloadRequestTracker(1024),
                downloader,
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.missing(),
                (mediaHint, createTime) -> ImageDownloadResolution.Candidate.fromFile(downloaded),
                logs::add);

        ImageDownloadResolution first = coordinator.requestAndResolve("hint", 10L, 20L, 123L, "talker");
        ImageDownloadResolution second = coordinator.requestAndResolve("hint", 10L, 20L, 123L, "talker");

        assertEquals(downloaded.getCanonicalFile(), first.file().getCanonicalFile());
        assertEquals(downloaded.getCanonicalFile(), second.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, first.status());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, second.status());
        assertEquals(1, downloader.count);
        assertTrue(logs.get(0).contains("after NetSceneGetMsgImg"));
        assertTrue(logs.get(1).contains("after NetSceneGetMsgImg"));
    }

    @Test
    public void requestAndResolveDoesNotFallbackWhenImageInfoFileExists() throws Exception {
        File imageInfo = Files.createTempFile("wxo-coordinator-info", ".jpg").toFile();
        writeJpeg(imageInfo);
        CountingFallback fallback = new CountingFallback();
        ImageDownloadCoordinator coordinator = new ImageDownloadCoordinator(
                new ImageDownloadRequestTracker(1024),
                new CountingDownloader(),
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.fromFile(imageInfo),
                fallback,
                null);

        ImageDownloadResolution resolved = coordinator.requestAndResolve("hint", 10L, 20L, 123L, "talker");

        assertEquals(imageInfo.getCanonicalFile(), resolved.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_FILE, resolved.status());
        assertEquals(0, fallback.count);
    }

    @Test
    public void requestAndResolveFallsBackWhenImageInfoIsThumbnail() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-coordinator-thumb-info").toFile();
        File thumbnail = new File(new File(new File(new File(appRoot, "MicroMsg/profile/image2"), "ab"), "cd"), "th_abcd1234");
        File downloaded = Files.createTempFile("wxo-coordinator-thumb-downloaded", ".jpg").toFile();
        writeJpeg(thumbnail);
        writeJpeg(downloaded);
        CountingFallback fallback = new CountingFallback();
        fallback.result = ImageDownloadResolution.Candidate.fromFile(downloaded);
        ImageDownloadCoordinator coordinator = new ImageDownloadCoordinator(
                new ImageDownloadRequestTracker(1024),
                new CountingDownloader(),
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.fromFile(thumbnail),
                fallback,
                null);

        ImageDownloadResolution resolved = coordinator.requestAndResolve("hint", 10L, 20L, 123L, "talker");

        assertEquals(downloaded.getCanonicalFile(), resolved.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, resolved.status());
        assertEquals(1, fallback.count);
    }

    @Test
    public void requestAndResolveFallsBackWhenImageInfoIsUnsupported() throws Exception {
        File unsupported = Files.createTempFile("wxo-coordinator-unsupported-info", ".ref").toFile();
        File downloaded = Files.createTempFile("wxo-coordinator-unsupported-downloaded", ".jpg").toFile();
        writeJpeg(downloaded);
        CountingFallback fallback = new CountingFallback();
        fallback.result = ImageDownloadResolution.Candidate.fromFile(downloaded);
        ImageDownloadCoordinator coordinator = new ImageDownloadCoordinator(
                new ImageDownloadRequestTracker(1024),
                new CountingDownloader(),
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.unsupported(unsupported),
                fallback,
                null);

        ImageDownloadResolution resolved = coordinator.requestAndResolve("hint", 10L, 20L, 123L, "talker");

        assertEquals(downloaded.getCanonicalFile(), resolved.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, resolved.status());
        assertEquals(1, fallback.count);
    }

    @Test
    public void requestAndResolveReportsDownloadedRefTarget() throws Exception {
        File downloaded = Files.createTempFile("wxo-coordinator-downloaded-ref", ".jpg").toFile();
        writeJpeg(downloaded);
        List<String> logs = new ArrayList<>();
        ImageDownloadCoordinator coordinator = new ImageDownloadCoordinator(
                new ImageDownloadRequestTracker(1024),
                new CountingDownloader(),
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.missing(),
                (mediaHint, createTime) -> ImageDownloadResolution.Candidate.refTarget(downloaded),
                logs::add);

        ImageDownloadResolution resolved = coordinator.requestAndResolve("hint", 10L, 20L, 123L, "talker");

        assertEquals(downloaded.getCanonicalFile(), resolved.file().getCanonicalFile());
        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET, resolved.status());
        assertTrue(logs.get(0).contains("ref target after NetSceneGetMsgImg"));
    }

    @Test
    public void requestAndResolveReturnsNullForMissingIds() {
        CountingDownloader downloader = new CountingDownloader();
        ImageDownloadCoordinator coordinator = new ImageDownloadCoordinator(
                new ImageDownloadRequestTracker(1024),
                downloader,
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.missing(),
                (mediaHint, createTime) -> ImageDownloadResolution.Candidate.missing(),
                null);

        ImageDownloadResolution resolved = coordinator.requestAndResolve("hint", 0L, 0L, 123L, "talker");

        assertNull(resolved.file());
        assertEquals(ImageDownloadResolution.Status.NOT_READY, resolved.status());
        assertEquals(0, downloader.count);
    }

    @Test
    public void requestAndResolveDoesNotRequestWhenLocalIdIsMissing() {
        CountingDownloader downloader = new CountingDownloader();
        List<String> logs = new ArrayList<>();
        ImageDownloadCoordinator coordinator = new ImageDownloadCoordinator(
                new ImageDownloadRequestTracker(1024),
                downloader,
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.missing(),
                (mediaHint, createTime) -> ImageDownloadResolution.Candidate.missing(),
                logs::add);

        ImageDownloadResolution resolved = coordinator.requestAndResolve("hint", 0L, 20L, 123L, "talker");

        assertNull(resolved.file());
        assertEquals(ImageDownloadResolution.Status.NOT_READY, resolved.status());
        assertEquals(0, downloader.count);
        assertTrue(logs.get(0).contains("image local retry"));
    }

    @Test
    public void requestAndResolveReportsRetryWhenDownloaderFails() {
        CountingDownloader downloader = new CountingDownloader();
        downloader.result = false;
        List<String> logs = new ArrayList<>();
        ImageDownloadCoordinator coordinator = new ImageDownloadCoordinator(
                new ImageDownloadRequestTracker(1024),
                downloader,
                (localId, serverId, talker) -> ImageDownloadResolution.Candidate.missing(),
                (mediaHint, createTime) -> ImageDownloadResolution.Candidate.missing(),
                logs::add);

        ImageDownloadResolution resolved = coordinator.requestAndResolve("hint", 10L, 20L, 123L, "talker");

        assertNull(resolved.file());
        assertEquals(ImageDownloadResolution.Status.NOT_READY, resolved.status());
        assertEquals(1, downloader.count);
        assertTrue(logs.get(0).contains("image local retry"));
    }

    private static void writeJpeg(File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        byte[] bytes = new byte[512];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xd8;
        bytes[2] = (byte) 0xff;
        bytes[3] = (byte) 0xe0;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
    }

    private static final class CountingDownloader implements ImageDownloadCoordinator.Downloader {
        int count;
        boolean result = true;

        @Override
        public boolean request(long localId, long serverId, String talker) {
            count++;
            return result;
        }
    }

    private static final class CountingFallback implements ImageDownloadCoordinator.CandidateFallbackResolver {
        int count;
        ImageDownloadResolution.Candidate result = ImageDownloadResolution.Candidate.missing();

        @Override
        public ImageDownloadResolution.Candidate resolve(String mediaHint, long createTime) {
            count++;
            return result;
        }
    }
}
