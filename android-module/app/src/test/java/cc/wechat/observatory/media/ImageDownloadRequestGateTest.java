package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadRequestGateTest {
    @Test
    public void missingIdsCannotResolveOrRequestDownload() {
        ImageDownloadRequestGate.Decision decision =
                new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024))
                        .evaluate(0L, 0L);

        assertEquals(0L, decision.localId());
        assertEquals(0L, decision.serverId());
        assertFalse(decision.canResolve());
        assertFalse(decision.shouldRequestDownload());
    }

    @Test
    public void serverOnlyIdCanResolveButDoesNotRequestDownload() {
        ImageDownloadRequestGate.Decision decision =
                new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024))
                        .evaluate(0L, 20L);

        assertEquals(0L, decision.localId());
        assertEquals(20L, decision.serverId());
        assertTrue(decision.canResolve());
        assertFalse(decision.shouldRequestDownload());
    }

    @Test
    public void localIdRequestsOnlyOnce() {
        ImageDownloadRequestGate gate = new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024));

        ImageDownloadRequestGate.Decision first = gate.evaluate(10L, 20L);
        ImageDownloadRequestGate.Decision second = gate.evaluate(10L, 20L);

        assertTrue(first.canResolve());
        assertTrue(first.shouldRequestDownload());
        assertTrue(second.canResolve());
        assertFalse(second.shouldRequestDownload());
    }

    @Test
    public void missingTrackerStillCanResolveButDoesNotRequestDownload() {
        ImageDownloadRequestGate.Decision decision = new ImageDownloadRequestGate(null)
                .evaluate(10L, 20L);

        assertTrue(decision.canResolve());
        assertFalse(decision.shouldRequestDownload());
    }
}
