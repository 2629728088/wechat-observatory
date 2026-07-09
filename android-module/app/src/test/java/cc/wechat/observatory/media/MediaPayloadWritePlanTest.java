package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaPayloadWritePlanTest {
    @Test
    public void appliesEncodedAttachmentFieldsAtomically() throws Exception {
        File image = Files.createTempFile("wxo-write-plan", ".jpg").toFile();
        writeJpeg(image);
        MediaAttachment attachment = MediaAttachment.fromFile(image, 3, "message-id", 2048L);
        MessagePayload payload = new MessagePayload();

        MediaPayloadWritePlan plan = MediaPayloadWritePlan.from(
                attachment,
                bytes -> "encoded-" + bytes.length);
        plan.applyTo(payload);

        assertEquals("image/jpeg", payload.mediaMime);
        assertEquals(image.getName(), payload.mediaName);
        assertEquals(512, payload.mediaSize);
        assertEquals("encoded-512", payload.mediaBase64);
    }

    @Test
    public void emptyEncodingDoesNotCreatePlan() throws Exception {
        File image = Files.createTempFile("wxo-write-plan-empty", ".jpg").toFile();
        writeJpeg(image);
        MediaAttachment attachment = MediaAttachment.fromFile(image, 3, "message-id", 2048L);

        MediaPayloadWritePlan plan = MediaPayloadWritePlan.from(attachment, bytes -> "");

        assertNull(plan);
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
        assertTrue(file.isFile());
    }
}
