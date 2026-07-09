package cc.wechat.observatory.media;

import cc.wechat.observatory.model.MessagePayload;

final class MediaPayloadWritePlan {
    private final MediaAttachment attachment;
    private final String encoded;

    private MediaPayloadWritePlan(MediaAttachment attachment, String encoded) {
        this.attachment = attachment;
        this.encoded = encoded;
    }

    static MediaPayloadWritePlan from(
            MediaAttachment attachment,
            MediaPayloadWriter.Encoder encoder) {
        if (attachment == null) {
            return null;
        }
        String encoded = encoder == null ? "" : encoder.encode(attachment.bytes());
        if (encoded == null || encoded.length() <= 0) {
            return null;
        }
        return new MediaPayloadWritePlan(attachment, encoded);
    }

    void applyTo(MessagePayload payload) {
        if (payload == null) {
            return;
        }
        payload.mediaMime = attachment.mime();
        payload.mediaName = attachment.name();
        payload.mediaSize = attachment.size();
        payload.mediaBase64 = encoded;
    }
}
