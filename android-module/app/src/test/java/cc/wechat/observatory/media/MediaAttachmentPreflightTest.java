package cc.wechat.observatory.media;

import org.junit.Test;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentPreflightTest {
    @Test
    public void invalidRequestIsSkippedWithoutKind() {
        MediaAttachmentPreflight.Result result = MediaAttachmentPreflight.check(null);

        assertFalse(result.ready());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.INVALID_REQUEST, result.status());
        assertEquals("", result.mediaKind());
    }

    @Test
    public void unsupportedTypeDoesNotMutatePayloadKind() {
        MessagePayload payload = new MessagePayload();

        MediaAttachmentPreflight.Result result = MediaAttachmentPreflight.check(request(payload, 999, true));

        assertFalse(result.ready());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.UNSUPPORTED_TYPE, result.status());
        assertEquals("", result.mediaKind());
        assertNull(payload.mediaKind);
    }

    @Test
    public void uploadDisabledSetsKindButSkipsSelection() {
        MessagePayload payload = new MessagePayload();

        MediaAttachmentPreflight.Result result = MediaAttachmentPreflight.check(request(payload, 34, false));

        assertFalse(result.ready());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.UPLOAD_DISABLED, result.status());
        assertEquals("voice", result.mediaKind());
        assertEquals("voice", payload.mediaKind);
    }

    @Test
    public void supportedRequestIsReadyAndSetsKind() {
        MessagePayload payload = new MessagePayload();

        MediaAttachmentPreflight.Result result = MediaAttachmentPreflight.check(request(payload, 3, true));

        assertTrue(result.ready());
        assertEquals(MediaAttachmentProcessor.AttachmentStatus.ATTACHED, result.status());
        assertEquals("image", result.mediaKind());
        assertEquals("image", payload.mediaKind);
    }

    private static MediaAttachmentProcessor.Request request(
            MessagePayload payload,
            int type,
            boolean uploadEnabled) {
        return new MediaAttachmentProcessor.Request(
                payload,
                type,
                "media.jpg",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "",
                12L,
                uploadEnabled,
                2048L);
    }
}
