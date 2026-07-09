package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatImageFilesTest {
    @Test
    public void thumbnailDetectionTreatsHdPreviewAsThumbnailButKeepsRefSeparate() throws Exception {
        File root = Files.createTempDirectory("wxo-thumb").toFile();
        File image2 = new File(root, "image2");
        File low = new File(image2, "th_abcd1234");
        File hd = new File(image2, "th_abcd1234hd");
        File ref = new File(image2, "th_abcd1234" + WechatImageFiles.REF_SUFFIX);
        writeJpeg(low);
        writeJpeg(hd);
        writeJpeg(ref);

        assertTrue(WechatImageFiles.isLowQualityThumbnailFile(low));
        assertTrue(WechatImageFiles.isLowQualityThumbnailFile(hd));
        assertFalse(WechatImageFiles.isLowQualityThumbnailFile(ref));
    }

    @Test
    public void referencePointerHelpersStayScopedToWechatImageFiles() throws Exception {
        File root = Files.createTempDirectory("wxo-ref").toFile();
        File image2 = new File(root, "image2");
        File pointer = new File(image2, "abcd" + WechatImageFiles.REF_SUFFIX);
        writeJpeg(pointer);

        assertTrue(WechatImageFiles.isReferencePointerName(pointer.getName()));
        assertTrue(WechatImageFiles.isReferencePointerFile(pointer));
        assertEquals("abcd", WechatImageFiles.stripReferencePointerSuffix(pointer.getName()));
        assertEquals("plain.jpg", WechatImageFiles.stripReferencePointerSuffix("plain.jpg"));
        assertEquals("", WechatImageFiles.stripReferencePointerSuffix(null));
        assertEquals(image2.getCanonicalFile(), WechatImageFiles.image2RootFor(pointer).getCanonicalFile());
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
