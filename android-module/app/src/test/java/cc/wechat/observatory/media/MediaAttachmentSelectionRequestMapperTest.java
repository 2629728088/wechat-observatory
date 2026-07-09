package cc.wechat.observatory.media;

import org.junit.Test;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class MediaAttachmentSelectionRequestMapperTest {
    @Test
    public void fromCopiesSelectorFields() {
        MediaAttachmentProcessor.Request source = new MediaAttachmentProcessor.Request(
                new MessagePayload(),
                47,
                "media-hint",
                Long.valueOf(11L),
                Long.valueOf(22L),
                333L,
                "talker",
                "<content/>",
                "abcdefabcdefabcdefabcdefabcdefab",
                444L,
                true,
                555L);

        MediaFileSelector.Request request = MediaAttachmentSelectionRequestMapper.from(source);

        assertEquals(47, request.type());
        assertEquals("media-hint", request.mediaHint());
        assertEquals(Long.valueOf(11L), request.msgId());
        assertEquals(Long.valueOf(22L), request.msgSvrId());
        assertEquals(333L, request.createTime());
        assertEquals("talker", request.talker());
        assertEquals("<content/>", request.content());
        assertEquals("abcdefabcdefabcdefabcdefabcdefab", request.emojiMd5());
        assertEquals(444L, request.chatRecordId());
    }

    @Test
    public void nullRequestMapsToNull() {
        assertNull(MediaAttachmentSelectionRequestMapper.from(null));
    }
}
