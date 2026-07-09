package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadCandidateSetTest {
    @Test
    public void ofPreservesSemanticCandidates() throws Exception {
        File imageInfo = Files.createTempFile("wxo-candidate-set-info", ".jpg").toFile();
        File downloaded = Files.createTempFile("wxo-candidate-set-downloaded", ".jpg").toFile();
        writeJpeg(imageInfo);
        writeJpeg(downloaded);
        ImageDownloadResolution.Candidate imageInfoCandidate =
                ImageDownloadResolution.Candidate.fromFile(imageInfo);
        ImageDownloadResolution.Candidate downloadedCandidate =
                ImageDownloadResolution.Candidate.refTarget(downloaded);

        ImageDownloadCandidateSet result = ImageDownloadCandidateSet.of(
                true,
                imageInfoCandidate,
                downloadedCandidate);

        assertTrue(result.requestedDownload());
        assertSame(imageInfoCandidate, result.imageInfoCandidate());
        assertSame(downloadedCandidate, result.downloadedFallbackCandidate());
    }

    @Test
    public void nullCandidatesBecomeMissingCandidates() {
        ImageDownloadCandidateSet result = ImageDownloadCandidateSet.of(true, null, null);

        assertTrue(result.requestedDownload());
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
}
