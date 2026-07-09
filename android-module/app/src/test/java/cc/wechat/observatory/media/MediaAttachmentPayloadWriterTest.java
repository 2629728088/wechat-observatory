package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentPayloadWriterTest {
    @Test
    public void writeDelegatesEncodingAndPayloadFields() throws Exception {
        MessagePayload payload = new MessagePayload();
        payload.id = "msg";
        File image = Files.createTempFile("wxo-payload-bridge", ".jpg").toFile();
        writeJpeg(image);

        MediaPayloadWriter.Result result = MediaAttachmentPayloadWriter.from(
                bytes -> "encoded-" + bytes.length,
                message -> {
                })
                .write(payload, 3, image, 2048L);

        assertTrue(result.isWritten());
        assertEquals(MediaPayloadWriter.Status.WRITTEN, result.status());
        assertEquals("image/jpeg", payload.mediaMime);
        assertEquals(image.getName(), payload.mediaName);
        assertEquals(512, payload.mediaSize);
        assertEquals("encoded-512", payload.mediaBase64);
    }

    @Test
    public void missingEncoderPreservesEncodedEmptyStatus() throws Exception {
        MessagePayload payload = new MessagePayload();
        File image = Files.createTempFile("wxo-payload-bridge-empty", ".jpg").toFile();
        writeJpeg(image);

        MediaPayloadWriter.Result result = MediaAttachmentPayloadWriter.from(null, null)
                .write(payload, 3, image, 2048L);

        assertEquals(MediaPayloadWriter.Status.ENCODED_EMPTY, result.status());
    }

    @Test
    public void writeForwardsLogsFromPayloadWriter() throws Exception {
        MessagePayload payload = new MessagePayload();
        File image = Files.createTempFile("wxo-payload-bridge-limit", ".jpg").toFile();
        writeJpeg(image);
        List<String> logs = new ArrayList<>();

        MediaPayloadWriter.Result result = MediaAttachmentPayloadWriter.from(
                bytes -> "encoded",
                logs::add)
                .write(payload, 3, image, 1L);

        assertEquals(MediaPayloadWriter.Status.FILE_SIZE_OUT_OF_RANGE, result.status());
        assertTrue(contains(logs, "skip media upload"));
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

    private static boolean contains(List<String> values, String expected) {
        for (String value : values) {
            if (value != null && value.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
