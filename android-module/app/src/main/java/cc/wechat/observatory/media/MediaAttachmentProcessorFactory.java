package cc.wechat.observatory.media;

public final class MediaAttachmentProcessorFactory {
    private MediaAttachmentProcessorFactory() {
    }

    public static MediaAttachmentProcessor create(
            ImageDownloadRequestTracker imageDownloadTracker,
            final MediaAttachmentServices services) {
        return new MediaAttachmentProcessor(
                MediaAttachmentSelectorFactory.create(imageDownloadTracker, services),
                MediaAttachmentProcessorServiceAdapters.encoder(services),
                MediaAttachmentProcessorServiceAdapters.logger(services));
    }
}
