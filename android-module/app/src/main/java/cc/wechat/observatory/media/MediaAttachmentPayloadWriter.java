package cc.wechat.observatory.media;

import java.io.File;

import cc.wechat.observatory.model.MessagePayload;

final class MediaAttachmentPayloadWriter {
    private final MediaPayloadWriter.Encoder encoder;
    private final MediaPayloadWriter.Logger logger;

    private MediaAttachmentPayloadWriter(MediaPayloadWriter.Encoder encoder, MediaPayloadWriter.Logger logger) {
        this.encoder = encoder;
        this.logger = logger;
    }

    static MediaAttachmentPayloadWriter from(
            final MediaAttachmentProcessor.Encoder encoder,
            final MediaAttachmentProcessor.Logger logger) {
        return new MediaAttachmentPayloadWriter(
                new MediaPayloadWriter.Encoder() {
                    @Override
                    public String encode(byte[] bytes) {
                        return encoder == null ? "" : encoder.encode(bytes);
                    }
                },
                new MediaPayloadWriter.Logger() {
                    @Override
                    public void log(String message) {
                        if (logger != null) {
                            logger.log(message);
                        }
                    }
                });
    }

    MediaPayloadWriter.Result write(MessagePayload payload, int type, File file, long limit) {
        return MediaPayloadWriter.writeDetailed(payload, type, file, limit, encoder, logger);
    }
}
