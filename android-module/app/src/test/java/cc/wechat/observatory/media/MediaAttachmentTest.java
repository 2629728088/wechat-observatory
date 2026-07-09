package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentTest {
    @Test
    public void fromFileDetectsMimeAndAddsExtensionWhenMissing() throws Exception {
        File dir = Files.createTempDirectory("wxo-attachment").toFile();
        File image = new File(dir, "photo");
        writeJpeg(image, 512);

        MediaAttachment attachment = MediaAttachment.fromFile(image, 3, "message-id", 1024L);

        assertEquals("image/jpeg", attachment.mime());
        assertEquals("photo.jpg", attachment.name());
        assertEquals(512, attachment.size());
        assertEquals(512, attachment.bytes().length);
    }

    @Test
    public void fromFileKeepsExistingExtension() throws Exception {
        File dir = Files.createTempDirectory("wxo-attachment-ext").toFile();
        File image = new File(dir, "photo.jpeg");
        writeJpeg(image, 512);

        MediaAttachment attachment = MediaAttachment.fromFile(image, 3, "message-id", 1024L);

        assertEquals("image/jpeg", attachment.mime());
        assertEquals("photo.jpeg", attachment.name());
    }

    @Test
    public void bytesReturnsDefensiveCopy() throws Exception {
        File dir = Files.createTempDirectory("wxo-attachment-copy").toFile();
        File image = new File(dir, "photo");
        writeJpeg(image, 512);
        MediaAttachment attachment = MediaAttachment.fromFile(image, 3, "message-id", 1024L);

        byte[] first = attachment.bytes();
        first[0] = 0;

        assertEquals((byte) 0xff, attachment.bytes()[0]);
    }

    @Test
    public void fromFileFailsWhenLimitExceeded() throws Exception {
        File dir = Files.createTempDirectory("wxo-attachment-limit").toFile();
        File image = new File(dir, "photo");
        writeJpeg(image, 512);

        try {
            MediaAttachment.fromFile(image, 3, "message-id", 128L);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("media file exceeds limit"));
            return;
        }
        throw new AssertionError("expected media file exceeds limit");
    }

    private static void writeJpeg(File file, int size) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xd8;
        bytes[2] = (byte) 0xff;
        bytes[3] = (byte) 0xe0;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
    }
}
