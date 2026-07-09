package cc.wechat.observatory.media;

import org.junit.Test;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class MediaAttachmentRequestFactoryTest {
    private static final String MD5_UPPER = "ABCDEF1234567890ABCDEF1234567890";
    private static final String MD5_LOWER = "abcdef1234567890abcdef1234567890";

    @Test
    public void fromCopiesAttachmentRequestFields() {
        MessagePayload payload = new MessagePayload();
        payload.chatRecordId = 123L;

        MediaAttachmentProcessor.Request request = MediaAttachmentRequestFactory.from(
                payload,
                MediaFiles.MESSAGE_TYPE_IMAGE,
                "image-hint",
                Long.valueOf(11L),
                Long.valueOf(22L),
                333L,
                "talker",
                "content",
                true,
                4096L);

        assertSame(payload, request.payload());
        assertEquals(MediaFiles.MESSAGE_TYPE_IMAGE, request.type());
        assertEquals("image-hint", request.mediaHint());
        assertEquals(Long.valueOf(11L), request.msgId());
        assertEquals(Long.valueOf(22L), request.msgSvrId());
        assertEquals(333L, request.createTime());
        assertEquals("talker", request.talker());
        assertEquals("content", request.content());
        assertEquals("", request.emojiMd5());
        assertEquals(123L, request.chatRecordId());
        assertEquals(true, request.mediaUploadEnabled());
        assertEquals(4096L, request.mediaUploadLimitBytes());
    }

    @Test
    public void fromExtractsEmojiMd5ForEmojiMessageOnly() {
        MessagePayload payload = new MessagePayload();
        String content = "wxid_sender:\n<msg><emoji md5=\"" + MD5_UPPER + "\" /></msg>";

        MediaAttachmentProcessor.Request emoji = MediaAttachmentRequestFactory.from(
                payload,
                MediaFiles.MESSAGE_TYPE_EMOJI,
                "",
                null,
                null,
                0L,
                "room@chatroom",
                content,
                true,
                1024L);
        MediaAttachmentProcessor.Request image = MediaAttachmentRequestFactory.from(
                payload,
                MediaFiles.MESSAGE_TYPE_IMAGE,
                "",
                null,
                null,
                0L,
                "room@chatroom",
                content,
                true,
                1024L);

        assertEquals(MD5_LOWER, emoji.emojiMd5());
        assertEquals("", image.emojiMd5());
    }

    @Test
    public void nullPayloadUsesZeroChatRecordId() {
        MediaAttachmentProcessor.Request request = MediaAttachmentRequestFactory.from(
                null,
                MediaFiles.MESSAGE_TYPE_IMAGE,
                "",
                null,
                null,
                0L,
                "",
                "",
                false,
                0L);

        assertEquals(0L, request.chatRecordId());
    }
}
