package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageBaseCandidateDecisionTest {
    @Test
    public void normalImageIsUsableBaseFile() throws Exception {
        File image = Files.createTempFile("wxo-base-image", ".jpg").toFile();
        writeJpeg(image);

        WechatImageBaseCandidateDecision decision =
                WechatImageBaseCandidateDecision.from(ImageDownloadResolution.Candidate.fromFile(image));

        assertTrue(decision.hasUsableFile());
        assertEquals(image.getCanonicalFile(), decision.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, decision.status());
        assertFalse(decision.isThumbnail());
        assertFalse(decision.isUnsupported());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, decision.selection().status());
    }

    @Test
    public void refTargetIsUsableButKeepsRefStatus() throws Exception {
        File image = Files.createTempFile("wxo-base-ref", ".jpg").toFile();
        writeJpeg(image);

        WechatImageBaseCandidateDecision decision =
                WechatImageBaseCandidateDecision.from(ImageDownloadResolution.Candidate.refTarget(image));

        assertTrue(decision.hasUsableFile());
        assertEquals(image.getCanonicalFile(), decision.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_REF_TARGET, decision.status());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_REF_TARGET, decision.selection().status());
    }

    @Test
    public void lowQualityThumbnailRequestsFullImageInsteadOfSelectingBase() throws Exception {
        File thumbnail = thumbnailFile();
        writeJpeg(thumbnail);

        WechatImageBaseCandidateDecision decision =
                WechatImageBaseCandidateDecision.from(ImageDownloadResolution.Candidate.fromFile(thumbnail));

        assertFalse(decision.hasUsableFile());
        assertEquals(thumbnail.getCanonicalFile(), decision.file().getCanonicalFile());
        assertTrue(decision.isThumbnail());
        assertFalse(decision.isUnsupported());
        assertNull(decision.status());
        assertNull(decision.selection());
    }

    @Test
    public void unsupportedCandidateRequestsFullImageAndKeepsUnsupportedFallbackSignal() throws Exception {
        File unsupported = Files.createTempFile("wxo-base-unsupported", ".ref").toFile();

        WechatImageBaseCandidateDecision decision =
                WechatImageBaseCandidateDecision.from(ImageDownloadResolution.Candidate.unsupported(unsupported));

        assertFalse(decision.hasUsableFile());
        assertEquals(unsupported.getCanonicalFile(), decision.file().getCanonicalFile());
        assertFalse(decision.isThumbnail());
        assertTrue(decision.isUnsupported());
        assertNull(decision.status());
        assertNull(decision.selection());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_UNSUPPORTED, decision.unsupportedFallbackSelection().status());
    }

    @Test
    public void missingCandidateHasNoSelectionOrFallbackSignal() {
        WechatImageBaseCandidateDecision decision =
                WechatImageBaseCandidateDecision.from(ImageDownloadResolution.Candidate.missing());

        assertFalse(decision.hasUsableFile());
        assertNull(decision.file());
        assertNull(decision.status());
        assertFalse(decision.isThumbnail());
        assertFalse(decision.isUnsupported());
        assertNull(decision.selection());
        assertNull(decision.unsupportedFallbackSelection());
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-base-thumb").toFile(), "image2");
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
}
