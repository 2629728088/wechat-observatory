package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageResolvedCandidateTest {
    @Test
    public void candidateResolutionKeepsRefPointerSourceAndTargetFileDistinct() throws Exception {
        File pointer = Files.createTempFile("wxo-resolved-pointer", ".ref").toFile();
        File target = Files.createTempFile("wxo-resolved-target", ".jpg").toFile();
        writeJpeg(target);

        WechatImageResolvedCandidate result = WechatImageResolvedCandidate.from(
                WechatImageFileResolver.CandidateResolution.refTarget(pointer, target));

        assertTrue(result.isReferenceTarget());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(target.getCanonicalFile(), result.file().getCanonicalFile());
        assertTrue(result.hasResolvedFile());
        assertFalse(result.isMissing());
    }

    @Test
    public void profileSearchResultKeepsThumbnailSourceWithoutAFile() throws Exception {
        File thumbnail = Files.createTempFile("wxo-resolved-thumb", ".jpg").toFile();
        writeJpeg(thumbnail);

        WechatImageResolvedCandidate result = WechatImageResolvedCandidate.from(
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(thumbnail)));

        assertTrue(result.isLowQualityThumbnail());
        assertEquals(thumbnail.getCanonicalFile(), result.source().getCanonicalFile());
        assertNull(result.file());
        assertFalse(result.hasResolvedFile());
        assertFalse(result.isMissing());
    }

    @Test
    public void nullInputsBecomeMissingCandidate() {
        WechatImageResolvedCandidate candidate = WechatImageResolvedCandidate.from(
                (WechatImageFileResolver.CandidateResult) null);

        assertTrue(candidate.isMissing());
        assertNull(candidate.source());
        assertNull(candidate.file());
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
