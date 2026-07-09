package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadRequestAttemptTest {
    @Test
    public void requestReturnsFalseForNullDecisionOrNullDownloader() {
        ImageDownloadRequestGate.Decision decision = new ImageDownloadRequestGate(
                new ImageDownloadRequestTracker(1024))
                .evaluate(Long.valueOf(10L), Long.valueOf(20L));

        assertFalse(new ImageDownloadRequestAttempt(null).request(decision, "talker"));
        assertFalse(new ImageDownloadRequestAttempt((localId, serverId, talker) -> true)
                .request(null, "talker"));
    }

    @Test
    public void requestSkipsWhenGateDoesNotAllowDownloadRequest() {
        CountingDownloader downloader = new CountingDownloader();
        ImageDownloadRequestGate.Decision missingLocalId = new ImageDownloadRequestGate(
                new ImageDownloadRequestTracker(1024))
                .evaluate(Long.valueOf(0L), Long.valueOf(20L));

        assertFalse(new ImageDownloadRequestAttempt(downloader).request(missingLocalId, "talker"));
        assertEquals(0, downloader.count);
    }

    @Test
    public void requestPassesIdsAndTalkerToDownloader() {
        CountingDownloader downloader = new CountingDownloader();
        ImageDownloadRequestGate.Decision decision = new ImageDownloadRequestGate(
                new ImageDownloadRequestTracker(1024))
                .evaluate(Long.valueOf(10L), Long.valueOf(20L));

        assertTrue(new ImageDownloadRequestAttempt(downloader).request(decision, "talker"));
        assertEquals(1, downloader.count);
        assertEquals(10L, downloader.localId);
        assertEquals(20L, downloader.serverId);
        assertEquals("talker", downloader.talker);
    }

    @Test
    public void requestReturnsDownloaderFailure() {
        CountingDownloader downloader = new CountingDownloader();
        downloader.result = false;
        ImageDownloadRequestGate.Decision decision = new ImageDownloadRequestGate(
                new ImageDownloadRequestTracker(1024))
                .evaluate(Long.valueOf(10L), Long.valueOf(20L));

        assertFalse(new ImageDownloadRequestAttempt(downloader).request(decision, "talker"));
        assertEquals(1, downloader.count);
    }

    private static final class CountingDownloader implements ImageDownloadCoordinator.Downloader {
        int count;
        long localId;
        long serverId;
        String talker;
        boolean result = true;

        @Override
        public boolean request(long localId, long serverId, String talker) {
            count++;
            this.localId = localId;
            this.serverId = serverId;
            this.talker = talker;
            return result;
        }
    }
}
