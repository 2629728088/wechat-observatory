package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadFallbackCandidateResolverTest {
    @Test
    public void immediateImageInfoFileSkipsFallbackLookup() throws Exception {
        File image = Files.createTempFile("wxo-fallback-info", ".jpg").toFile();
        writeJpeg(image);
        CountingFallback fallback = new CountingFallback();

        ImageDownloadResolution.Candidate resolved = new ImageDownloadFallbackCandidateResolver(fallback)
                .resolve(ImageDownloadResolution.Candidate.fromFile(image), "hint", 123L);

        assertTrue(resolved.isMissing());
        assertEquals(0, fallback.count);
    }

    @Test
    public void immediateImageInfoRefTargetSkipsFallbackLookup() throws Exception {
        File image = Files.createTempFile("wxo-fallback-ref", ".jpg").toFile();
        writeJpeg(image);
        CountingFallback fallback = new CountingFallback();

        ImageDownloadResolution.Candidate resolved = new ImageDownloadFallbackCandidateResolver(fallback)
                .resolve(ImageDownloadResolution.Candidate.refTarget(image), "hint", 123L);

        assertTrue(resolved.isMissing());
        assertEquals(0, fallback.count);
    }

    @Test
    public void thumbnailImageInfoFallsBackToDownloadedCandidate() throws Exception {
        File thumbnail = thumbnailFile();
        File downloaded = Files.createTempFile("wxo-fallback-downloaded", ".jpg").toFile();
        writeJpeg(thumbnail);
        writeJpeg(downloaded);
        CountingFallback fallback = new CountingFallback();
        fallback.result = ImageDownloadResolution.Candidate.fromFile(downloaded);

        ImageDownloadResolution.Candidate resolved = new ImageDownloadFallbackCandidateResolver(fallback)
                .resolve(ImageDownloadResolution.Candidate.fromFile(thumbnail), "hint", 123L);

        assertSame(fallback.result, resolved);
        assertEquals(1, fallback.count);
    }

    @Test
    public void unsupportedImageInfoFallsBackToDownloadedCandidate() throws Exception {
        File unsupported = Files.createTempFile("wxo-fallback-unsupported", ".ref").toFile();
        CountingFallback fallback = new CountingFallback();

        ImageDownloadResolution.Candidate resolved = new ImageDownloadFallbackCandidateResolver(fallback)
                .resolve(ImageDownloadResolution.Candidate.unsupported(unsupported), "hint", 123L);

        assertSame(fallback.result, resolved);
        assertEquals(1, fallback.count);
    }

    @Test
    public void missingFallbackResolverReturnsMissingCandidate() {
        ImageDownloadResolution.Candidate resolved = new ImageDownloadFallbackCandidateResolver(null)
                .resolve(ImageDownloadResolution.Candidate.missing(), "hint", 123L);

        assertTrue(resolved.isMissing());
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-fallback-thumb").toFile(), "image2");
        return new File(image2, "th_abcd1234");
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
