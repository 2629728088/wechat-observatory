package cc.wechat.observatory.media;

final class MediaAttachmentProcessorServiceAdapters {
    private MediaAttachmentProcessorServiceAdapters() {
    }

    static MediaAttachmentProcessor.Encoder encoder(final MediaAttachmentServices services) {
        return new MediaAttachmentProcessor.Encoder() {
            @Override
            public String encode(byte[] bytes) {
                return services == null ? "" : services.encode(bytes);
            }
        };
    }

    static MediaAttachmentProcessor.Logger logger(final MediaAttachmentServices services) {
        return new MediaAttachmentProcessor.Logger() {
            @Override
            public void log(String message) {
                if (services != null) {
                    services.log(message);
                }
            }
        };
    }
}
