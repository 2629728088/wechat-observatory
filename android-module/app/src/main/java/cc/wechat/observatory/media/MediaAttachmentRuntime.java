package cc.wechat.observatory.media;

public final class MediaAttachmentRuntime {
    private final ImageDownloadRequestTracker imageDownloadTracker;
    private final MediaAttachmentServices services;

    public MediaAttachmentRuntime(ImageDownloadRequestTracker imageDownloadTracker, MediaAttachmentServices services) {
        this.imageDownloadTracker = imageDownloadTracker;
        this.services = services;
    }

    public boolean attach(MediaAttachmentProcessor.Request request) {
        return attachDetailed(request).isAttached();
    }

    public MediaAttachmentProcessor.Result attachDetailed(MediaAttachmentProcessor.Request request) {
        if (services == null) {
            return MediaAttachmentProcessor.Result.skipped(
                    MediaAttachmentProcessor.AttachmentStatus.RUNTIME_UNAVAILABLE,
                    "",
                    null,
                    null);
        }
        return MediaAttachmentProcessorFactory.create(imageDownloadTracker, services)
                .attachDetailed(request);
    }
}
