package cc.wechat.observatory.media;

import cc.wechat.observatory.model.MessagePayload;

public final class MediaAttachmentRequestFactory {
    private MediaAttachmentRequestFactory() {
    }

    public static MediaAttachmentProcessor.Request from(
            MessagePayload payload,
            int type,
            String mediaHint,
            Long msgId,
            Long msgSvrId,
            long createTime,
            String talker,
            String content,
            boolean mediaUploadEnabled,
            long mediaUploadLimitBytes) {
        return new MediaAttachmentProcessor.Request(
                payload,
                type,
                mediaHint,
                msgId,
                msgSvrId,
                createTime,
                talker,
                content,
                emojiMd5(type, talker, content),
                payload == null ? 0L : payload.chatRecordId,
                mediaUploadEnabled,
                mediaUploadLimitBytes);
    }

    static String emojiMd5(int type, String talker, String content) {
        return MediaFiles.isEmojiMessageType(type) ? EmojiMediaParser.md5FromWechatContent(talker, content) : "";
    }
}
