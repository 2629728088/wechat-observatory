package cc.wechat.observatory.media;

final class MediaAttachmentSelectionRequestMapper {
    private MediaAttachmentSelectionRequestMapper() {
    }

    static MediaFileSelector.Request from(MediaAttachmentProcessor.Request request) {
        if (request == null) {
            return null;
        }
        return new MediaFileSelector.Request(
                request.type(),
                request.mediaHint(),
                request.msgId(),
                request.msgSvrId(),
                request.createTime(),
                request.talker(),
                request.content(),
                request.emojiMd5(),
                request.chatRecordId());
    }
}
