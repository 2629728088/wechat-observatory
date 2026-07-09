package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentProcessorTest {
    @Test
    public void attachSetsKindAndWritesSelectedMedia() throws Exception {
        MessagePayload payload = new MessagePayload();
        payload.id = "abc";
        File image = Files.createTempFile("wxo-processor", ".jpg").toFile();
        writeJpeg(image);

        boolean attached = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> candidate(image),
                        null,
                        null,
                        null,
                        message -> {
                        }),
                bytes -> "encoded-" + bytes.length,
                message -> {
                })
                .attach(request(payload, 3, true, 2048L));

        assertTrue(attached);
        assertEquals("image", payload.mediaKind);
        assertEquals("image/jpeg", payload.mediaMime);
        assertEquals(image.getName(), payload.mediaName);
        assertEquals(512, payload.mediaSize);
        assertEquals("encoded-512", payload.mediaBase64);
    }

    @Test
    public void attachDetailedReportsAttachedStatus() throws Exception {
        MessagePayload payload = new MessagePayload();
        File image = Files.createTempFile("wxo-processor-detailed", ".jpg").toFile();
        writeJpeg(image);

        MediaAttachmentProcessor.Result result = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> candidate(image),
                        null,
                        null,
                        null,
                        message -> {
                        }),
                bytes -> "encoded-" + bytes.length,
                message -> {
                })
                .attachDetailed(request(payload, 3, true, 2048L));

        assertTrue(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.ATTACHED, result.status());
        assertEquals("image", result.mediaKind());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, result.selectionStatus());
        assertNull(result.writerStatus());
        assertEquals("encoded-512", payload.mediaBase64);
    }

    @Test
    public void attachSetsKindButDoesNotSelectWhenUploadDisabled() {
        MessagePayload payload = new MessagePayload();
        AtomicInteger calls = new AtomicInteger();

        boolean attached = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> {
                            calls.incrementAndGet();
                            return missingCandidate();
                        },
                        null,
                        null,
                        null,
                        message -> {
                        }),
                bytes -> "encoded",
                message -> {
                })
                .attach(request(payload, 34, false, 2048L));

        assertFalse(attached);
        assertEquals("voice", payload.mediaKind);
        assertNull(payload.mediaBase64);
        assertEquals(0, calls.get());
    }

    @Test
    public void attachDetailedReportsUploadDisabledStatus() {
        MessagePayload payload = new MessagePayload();
        AtomicInteger calls = new AtomicInteger();

        MediaAttachmentProcessor.Result result = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> {
                            calls.incrementAndGet();
                            return missingCandidate();
                        },
                        null,
                        null,
                        null,
                        message -> {
                        }),
                bytes -> "encoded",
                message -> {
                })
                .attachDetailed(request(payload, 34, false, 2048L));

        assertFalse(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.UPLOAD_DISABLED, result.status());
        assertEquals("voice", result.mediaKind());
        assertNull(result.selectionStatus());
        assertNull(result.writerStatus());
        assertEquals(0, calls.get());
    }

    @Test
    public void attachIgnoresUnknownMessageType() {
        MessagePayload payload = new MessagePayload();
        AtomicInteger calls = new AtomicInteger();

        boolean attached = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> {
                            calls.incrementAndGet();
                            return missingCandidate();
                        },
                        null,
                        null,
                        null,
                        message -> {
                        }),
                bytes -> "encoded",
                message -> {
                })
                .attach(request(payload, 999, true, 2048L));

        assertFalse(attached);
        assertNull(payload.mediaKind);
        assertNull(payload.mediaBase64);
        assertEquals(0, calls.get());
    }

    @Test
    public void attachLogsSelectionStatusWhenMediaIsMissing() {
        MessagePayload payload = new MessagePayload();
        List<String> logs = new ArrayList<>();

        boolean attached = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                        null,
                        null,
                        null,
                        logs::add),
                bytes -> "encoded",
                logs::add)
                .attach(request(payload, 34, true, 2048L));

        assertFalse(attached);
        assertEquals("voice", payload.mediaKind);
        assertTrue(contains(logs, "selectionStatus=NOT_FOUND"));
        assertFalse(contains(logs, "media.jpg"));
    }

    @Test
    public void attachDetailedReportsMediaNotSelectedStatus() {
        MessagePayload payload = new MessagePayload();

        MediaAttachmentProcessor.Result result = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                        null,
                        null,
                        null,
                        message -> {
                        }),
                bytes -> "encoded",
                message -> {
                })
                .attachDetailed(request(payload, 34, true, 2048L));

        assertFalse(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.MEDIA_NOT_SELECTED, result.status());
        assertEquals("voice", result.mediaKind());
        assertEquals(MediaFileSelector.SelectionStatus.NOT_FOUND, result.selectionStatus());
        assertNull(result.writerStatus());
    }

    @Test
    public void attachLogsSelectionStatusWhenImageDownloadReturnsThumbnail() throws Exception {
        MessagePayload payload = new MessagePayload();
        File thumbnail = new File(Files.createTempDirectory("wxo-processor-thumb").toFile(), "image2/th_abcd1234");
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        boolean attached = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                        (mediaHint, msgId, msgSvrId, createTime, talker) -> downloaded(thumbnail),
                        null,
                        null,
                        logs::add),
                bytes -> "encoded",
                logs::add)
                .attach(request(payload, 3, true, 2048L));

        assertFalse(attached);
        assertEquals("image", payload.mediaKind);
        assertTrue(contains(logs, "selectionStatus=IMAGE_DOWNLOAD_THUMBNAIL"));
        assertTrue(contains(logs, "skip thumbnail image upload"));
    }

    @Test
    public void attachUsesGchatFileWhenImageDownloadReturnsThumbnail() throws Exception {
        MessagePayload payload = new MessagePayload();
        File thumbnail = new File(Files.createTempDirectory("wxo-processor-gchat-thumb").toFile(), "image2/th_abcd1234");
        File image = Files.createTempFile("wxo-processor-gchat-full", ".jpg").toFile();
        writeJpeg(thumbnail);
        writeJpeg(image);
        List<String> logs = new ArrayList<>();

        MediaAttachmentProcessor.Result result = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                        (mediaHint, msgId, msgSvrId, createTime, talker) -> downloaded(thumbnail),
                        null,
                        content -> image,
                        logs::add),
                bytes -> "encoded-" + bytes.length,
                logs::add)
                .attachDetailed(request(payload, 3, true, 2048L));

        assertTrue(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.ATTACHED, result.status());
        assertEquals(MediaFileSelector.SelectionStatus.GCHAT_IMAGE_FILE, result.selectionStatus());
        assertEquals(image.getName(), payload.mediaName);
        assertEquals("encoded-512", payload.mediaBase64);
        assertTrue(contains(logs, "skip thumbnail image upload"));
    }

    @Test
    public void attachLogsWriterStatusWhenSelectedFileCannotBeEncoded() throws Exception {
        MessagePayload payload = new MessagePayload();
        File image = Files.createTempFile("wxo-processor-encoder-empty", ".jpg").toFile();
        writeJpeg(image);
        List<String> logs = new ArrayList<>();

        boolean attached = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> candidate(image),
                        null,
                        null,
                        null,
                        logs::add),
                bytes -> "",
                logs::add)
                .attach(request(payload, 3, true, 2048L));

        assertFalse(attached);
        assertEquals("image", payload.mediaKind);
        assertTrue(contains(logs, "selectionStatus=BASE_FILE"));
        assertTrue(contains(logs, "writerStatus=ENCODED_EMPTY"));
        assertFalse(contains(logs, "media.jpg"));
    }

    @Test
    public void attachDetailedReportsWriteFailedStatus() throws Exception {
        MessagePayload payload = new MessagePayload();
        File image = Files.createTempFile("wxo-processor-write-failed", ".jpg").toFile();
        writeJpeg(image);

        MediaAttachmentProcessor.Result result = new MediaAttachmentProcessor(
                new MediaFileSelector(
                        (type, mediaHint, createTime, emojiMd5) -> candidate(image),
                        null,
                        null,
                        null,
                        message -> {
                        }),
                bytes -> "",
                message -> {
                })
                .attachDetailed(request(payload, 3, true, 2048L));

        assertFalse(result.isAttached());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.WRITE_FAILED, result.status());
        assertEquals("image", result.mediaKind());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, result.selectionStatus());
        assertEquals(MediaPayloadWriter.Status.ENCODED_EMPTY, result.writerStatus());
    }

    private static MediaAttachmentProcessor.Request request(MessagePayload payload, int type, boolean uploadEnabled, long limit) {
        return new MediaAttachmentProcessor.Request(
                payload,
                type,
                "media.jpg",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "abcdefabcdefabcdefabcdefabcdefab",
                12L,
                uploadEnabled,
                limit);
    }

    private static ImageDownloadResolution.Candidate candidate(File file) {
        return ImageDownloadResolution.Candidate.fromFile(file);
    }

    private static ImageDownloadResolution.Candidate missingCandidate() {
        return ImageDownloadResolution.Candidate.missing();
    }

    private static ImageDownloadResolution downloaded(File file) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                true,
                missingCandidate(),
                candidate(file));
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

    private static boolean contains(List<String> values, String expected) {
        for (String value : values) {
            if (value != null && value.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
