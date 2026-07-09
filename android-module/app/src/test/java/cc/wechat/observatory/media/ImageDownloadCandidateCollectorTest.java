package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadCandidateCollectorTest {
    @Test
    public void collectRequestsDownloadBeforeResolvingImageInfoAndFallback() throws Exception {
        File downloaded = Files.createTempFile("wxo-candidate-collector-order", ".jpg").toFile();
        writeJpeg(downloaded);
        List<String> events = new ArrayList<>();
        OrderedDownloader downloader = new OrderedDownloader(events);
        OrderedImageInfoResolver imageInfo = new OrderedImageInfoResolver(events);
        OrderedFallbackResolver fallback = new OrderedFallbackResolver(events);
        fallback.result = ImageDownloadResolution.Candidate.fromFile(downloaded);
        ImageDownloadRequestGate.Decision decision =
                new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024)).evaluate(10L, 20L);

        ImageDownloadCandidateSet result = new ImageDownloadCandidateCollector(
                downloader,
                imageInfo,
                fallback)
                .collect(decision, "hint.jpg", 123L, "talker");

        assertEquals(Arrays.asList("download", "imageInfo", "fallback"), events);
        assertTrue(result.requestedDownload());
        assertSame(fallback.result, result.downloadedFallbackCandidate());
    }

    @Test
    public void collectRequestsDownloadAndResolvesFallbackWhenInfoMissing() throws Exception {
        File downloaded = Files.createTempFile("wxo-candidate-collector-downloaded", ".jpg").toFile();
        writeJpeg(downloaded);
        CountingDownloader downloader = new CountingDownloader();
        CountingImageInfoResolver imageInfo = new CountingImageInfoResolver();
        CountingFallbackResolver fallback = new CountingFallbackResolver();
        fallback.result = ImageDownloadResolution.Candidate.fromFile(downloaded);
        ImageDownloadRequestGate.Decision decision =
                new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024)).evaluate(10L, 20L);

        ImageDownloadCandidateSet result = new ImageDownloadCandidateCollector(
                downloader,
                imageInfo,
                fallback)
                .collect(decision, "hint.jpg", 123L, "talker");

        assertTrue(result.requestedDownload());
        assertEquals(1, downloader.count);
        assertEquals(1, imageInfo.count);
        assertEquals(1, fallback.count);
        assertTrue(result.imageInfoCandidate().isMissing());
        assertSame(fallback.result, result.downloadedFallbackCandidate());
    }

    @Test
    public void collectSkipsDownloadButStillResolvesCandidatesForServerOnlyId() throws Exception {
        File downloaded = Files.createTempFile("wxo-candidate-collector-server", ".jpg").toFile();
        writeJpeg(downloaded);
        CountingDownloader downloader = new CountingDownloader();
        CountingImageInfoResolver imageInfo = new CountingImageInfoResolver();
        CountingFallbackResolver fallback = new CountingFallbackResolver();
        fallback.result = ImageDownloadResolution.Candidate.fromFile(downloaded);
        ImageDownloadRequestGate.Decision decision =
                new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024)).evaluate(0L, 20L);

        ImageDownloadCandidateSet result = new ImageDownloadCandidateCollector(
                downloader,
                imageInfo,
                fallback)
                .collect(decision, "hint.jpg", 123L, "talker");

        assertFalse(result.requestedDownload());
        assertEquals(0, downloader.count);
        assertEquals(1, imageInfo.count);
        assertEquals(1, fallback.count);
        assertSame(fallback.result, result.downloadedFallbackCandidate());
    }

    @Test
    public void collectSkipsFallbackWhenImageInfoHasImmediateFile() throws Exception {
        File imageInfoFile = Files.createTempFile("wxo-candidate-collector-info", ".jpg").toFile();
        writeJpeg(imageInfoFile);
        CountingFallbackResolver fallback = new CountingFallbackResolver();
        CountingImageInfoResolver imageInfo = new CountingImageInfoResolver();
        imageInfo.result = ImageDownloadResolution.Candidate.fromFile(imageInfoFile);
        ImageDownloadRequestGate.Decision decision =
                new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024)).evaluate(10L, 20L);

        ImageDownloadCandidateSet result = new ImageDownloadCandidateCollector(
                null,
                imageInfo,
                fallback)
                .collect(decision, "hint.jpg", 123L, "talker");

        assertFalse(result.requestedDownload());
        assertEquals(1, imageInfo.count);
        assertEquals(0, fallback.count);
        assertSame(imageInfo.result, result.imageInfoCandidate());
        assertTrue(result.downloadedFallbackCandidate().isMissing());
    }

    @Test
    public void collectSkipsFallbackWhenImageInfoHasRefTarget() throws Exception {
        File refTarget = Files.createTempFile("wxo-candidate-collector-ref", ".jpg").toFile();
        writeJpeg(refTarget);
        CountingFallbackResolver fallback = new CountingFallbackResolver();
        CountingImageInfoResolver imageInfo = new CountingImageInfoResolver();
        imageInfo.result = ImageDownloadResolution.Candidate.refTarget(refTarget);
        ImageDownloadRequestGate.Decision decision =
                new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024)).evaluate(10L, 20L);

        ImageDownloadCandidateSet result = new ImageDownloadCandidateCollector(
                null,
                imageInfo,
                fallback)
                .collect(decision, "hint.jpg", 123L, "talker");

        assertFalse(result.requestedDownload());
        assertEquals(1, imageInfo.count);
        assertEquals(0, fallback.count);
        assertSame(imageInfo.result, result.imageInfoCandidate());
        assertTrue(result.downloadedFallbackCandidate().isMissing());
    }

    @Test
    public void collectReturnsMissingCandidatesWhenDecisionCannotResolve() {
        ImageDownloadRequestGate.Decision decision =
                new ImageDownloadRequestGate(new ImageDownloadRequestTracker(1024)).evaluate(0L, 0L);
        CountingImageInfoResolver imageInfo = new CountingImageInfoResolver();
        CountingFallbackResolver fallback = new CountingFallbackResolver();

        ImageDownloadCandidateSet result = new ImageDownloadCandidateCollector(
                new CountingDownloader(),
                imageInfo,
                fallback)
                .collect(decision, "hint.jpg", 123L, "talker");

        assertFalse(result.requestedDownload());
        assertEquals(0, imageInfo.count);
        assertEquals(0, fallback.count);
        assertTrue(result.imageInfoCandidate().isMissing());
        assertTrue(result.downloadedFallbackCandidate().isMissing());
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

    private static final class CountingDownloader implements ImageDownloadCoordinator.Downloader {
        int count;

        @Override
        public boolean request(long localId, long serverId, String talker) {
            count++;
            return true;
        }
    }

    private static final class CountingImageInfoResolver implements ImageDownloadCoordinator.CandidateImageInfoResolver {
        int count;
        ImageDownloadResolution.Candidate result = ImageDownloadResolution.Candidate.missing();

        @Override
        public ImageDownloadResolution.Candidate resolve(long localId, long serverId, String talker) {
            count++;
            return result;
        }
    }

    private static final class CountingFallbackResolver implements ImageDownloadCoordinator.CandidateFallbackResolver {
        int count;
        ImageDownloadResolution.Candidate result = ImageDownloadResolution.Candidate.missing();

        @Override
        public ImageDownloadResolution.Candidate resolve(String mediaHint, long createTime) {
            count++;
            return result;
        }
    }

    private static final class OrderedDownloader implements ImageDownloadCoordinator.Downloader {
        private final List<String> events;

        OrderedDownloader(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean request(long localId, long serverId, String talker) {
            events.add("download");
            return true;
        }
    }

    private static final class OrderedImageInfoResolver implements ImageDownloadCoordinator.CandidateImageInfoResolver {
        private final List<String> events;

        OrderedImageInfoResolver(List<String> events) {
            this.events = events;
        }

        @Override
        public ImageDownloadResolution.Candidate resolve(long localId, long serverId, String talker) {
            events.add("imageInfo");
            return ImageDownloadResolution.Candidate.missing();
        }
    }

    private static final class OrderedFallbackResolver implements ImageDownloadCoordinator.CandidateFallbackResolver {
        private final List<String> events;
        ImageDownloadResolution.Candidate result = ImageDownloadResolution.Candidate.missing();

        OrderedFallbackResolver(List<String> events) {
            this.events = events;
        }

        @Override
        public ImageDownloadResolution.Candidate resolve(String mediaHint, long createTime) {
            events.add("fallback");
            return result;
        }
    }
}
