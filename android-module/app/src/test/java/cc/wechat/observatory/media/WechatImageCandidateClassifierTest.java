package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatImageCandidateClassifierTest {
    private static final String BASE = "abcd1234ef567890abcd1234ef567890";

    @Test
    public void classifyMissingFile() {
        assertEquals(
                WechatImageCandidateClassifier.Kind.MISSING,
                WechatImageCandidateClassifier.classify(null));
        assertEquals(
                WechatImageCandidateClassifier.Kind.MISSING,
                WechatImageCandidateClassifier.classify(new File("missing")));
    }

    @Test
    public void classifyReferencePointerBeforeImageHeader() throws Exception {
        File pointer = new File(image2Root("wxo-classifier-ref"), BASE + WechatImageFiles.REF_SUFFIX);
        writeJpeg(pointer);

        assertEquals(
                WechatImageCandidateClassifier.Kind.REFERENCE_POINTER,
                WechatImageCandidateClassifier.classify(pointer));
    }

    @Test
    public void classifyLowQualityThumbnail() throws Exception {
        File thumbnail = new File(image2Root("wxo-classifier-thumb"), "th_" + BASE);
        writeJpeg(thumbnail);

        assertEquals(
                WechatImageCandidateClassifier.Kind.LOW_QUALITY_THUMBNAIL,
                WechatImageCandidateClassifier.classify(thumbnail));
    }

    @Test
    public void classifyRealImage() throws Exception {
        File image = Files.createTempFile("wxo-classifier-real", ".jpg").toFile();
        writeJpeg(image);

        assertEquals(
                WechatImageCandidateClassifier.Kind.REAL_IMAGE,
                WechatImageCandidateClassifier.classify(image));
    }

    @Test
    public void classifyUnsupportedFile() throws Exception {
        File unsupported = Files.createTempFile("wxo-classifier-unsupported", ".bin").toFile();
        Files.write(unsupported.toPath(), "not an image".getBytes(StandardCharsets.US_ASCII));

        assertEquals(
                WechatImageCandidateClassifier.Kind.UNSUPPORTED,
                WechatImageCandidateClassifier.classify(unsupported));
    }

    private static File image2Root(String prefix) throws Exception {
        File image2 = new File(Files.createTempDirectory(prefix).toFile(), "image2");
        assertTrue(image2.mkdirs() || image2.isDirectory());
        return image2;
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
