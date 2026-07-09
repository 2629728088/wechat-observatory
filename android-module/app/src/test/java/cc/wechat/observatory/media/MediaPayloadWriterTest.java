package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaPayloadWriterTest {
    @Test
    public void writeSetsMediaFieldsFromAttachment() throws Exception {
        MessagePayload payload = new MessagePayload();
        payload.id = "abc";
        File image = Files.createTempFile("wxo-writer", ".jpg").toFile();
        writeJpeg(image);

        boolean written = MediaPayloadWriter.write(
                payload,
                3,
                image,
                2048L,
                bytes -> "encoded-" + bytes.length,
                message -> {
                });

        assertTrue(written);
        assertEquals("image/jpeg", payload.mediaMime);
        assertEquals(image.getName(), payload.mediaName);
        assertEquals(512, payload.mediaSize);
        assertEquals("encoded-512", payload.mediaBase64);
    }

    @Test
    public void writeDetailedReportsWrittenStatus() throws Exception {
        MessagePayload payload = new MessagePayload();
        payload.id = "abc";
        File image = Files.createTempFile("wxo-writer-detailed", ".jpg").toFile();
        writeJpeg(image);

        MediaPayloadWriter.Result result = MediaPayloadWriter.writeDetailed(
                payload,
                3,
                image,
                2048L,
                bytes -> "encoded-" + bytes.length,
                message -> {
                });

        assertTrue(result.isWritten());
        assertEquals(MediaPayloadWriter.Status.WRITTEN, result.status());
        assertEquals("encoded-512", payload.mediaBase64);
    }

    @Test
    public void writeSkipsFilesOverLimit() throws Exception {
        MessagePayload payload = new MessagePayload();
        File image = Files.createTempFile("wxo-writer-large", ".jpg").toFile();
        writeJpeg(image);
        List<String> logs = new ArrayList<>();

        boolean written = MediaPayloadWriter.write(
                payload,
                3,
                image,
                128L,
                bytes -> "encoded",
                logs::add);

        assertFalse(written);
        assertEquals(null, payload.mediaBase64);
        assertTrue(contains(logs, "skip media upload type=3"));
    }

    @Test
    public void writeDetailedReportsFileSizeOutOfRange() throws Exception {
        MessagePayload payload = new MessagePayload();
        File image = Files.createTempFile("wxo-writer-large-status", ".jpg").toFile();
        writeJpeg(image);

        MediaPayloadWriter.Result result = MediaPayloadWriter.writeDetailed(
                payload,
                3,
                image,
                128L,
                bytes -> "encoded",
                message -> {
                });

        assertFalse(result.isWritten());
        assertEquals(MediaPayloadWriter.Status.FILE_SIZE_OUT_OF_RANGE, result.status());
    }

    @Test
    public void writeSkipsEmptyFiles() throws Exception {
        MessagePayload payload = new MessagePayload();
        File empty = Files.createTempFile("wxo-writer-empty", ".jpg").toFile();

        boolean written = MediaPayloadWriter.write(
                payload,
                3,
                empty,
                2048L,
                bytes -> "encoded",
                message -> {
                });

        assertFalse(written);
    }

    @Test
    public void writeDetailedReportsEncodedEmpty() throws Exception {
        MessagePayload payload = new MessagePayload();
        payload.id = "abc";
        File image = Files.createTempFile("wxo-writer-empty-encoded", ".jpg").toFile();
        writeJpeg(image);

        MediaPayloadWriter.Result result = MediaPayloadWriter.writeDetailed(
                payload,
                3,
                image,
                2048L,
                bytes -> "",
                message -> {
                });

        assertFalse(result.isWritten());
        assertEquals(MediaPayloadWriter.Status.ENCODED_EMPTY, result.status());
        assertNull(payload.mediaMime);
        assertNull(payload.mediaName);
        assertEquals(0, payload.mediaSize);
        assertNull(payload.mediaBase64);
    }

    @Test
    public void writeDetailedReportsInvalidInput() {
        MediaPayloadWriter.Result result = MediaPayloadWriter.writeDetailed(
                new MessagePayload(),
                3,
                null,
                2048L,
                bytes -> "encoded",
                message -> {
                });

        assertFalse(result.isWritten());
        assertEquals(MediaPayloadWriter.Status.INVALID_INPUT, result.status());
    }

    @Test
    public void writeDetailedRejectsDirectoryInput() throws Exception {
        File directory = Files.createTempDirectory("wxo-writer-directory").toFile();

        MediaPayloadWriter.Result result = MediaPayloadWriter.writeDetailed(
                new MessagePayload(),
                3,
                directory,
                2048L,
                bytes -> "encoded",
                message -> {
                });

        assertFalse(result.isWritten());
        assertEquals(MediaPayloadWriter.Status.INVALID_INPUT, result.status());
    }

    @Test
    public void writeDetailedReportsReadFailed() {
        List<String> logs = new ArrayList<>();

        MediaPayloadWriter.Result result = MediaPayloadWriter.writeDetailed(
                new MessagePayload(),
                3,
                new UnreadableFile(),
                2048L,
                bytes -> "encoded",
                logs::add);

        assertFalse(result.isWritten());
        assertEquals(MediaPayloadWriter.Status.READ_FAILED, result.status());
        assertTrue(contains(logs, "read media failed type=3"));
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

    private static boolean contains(List<String> values, String needle) {
        for (String value : values) {
            if (value != null && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static final class UnreadableFile extends File {
        UnreadableFile() {
            super("wxo-unreadable-missing-file.jpg");
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public long length() {
            return 512L;
        }
    }
}
