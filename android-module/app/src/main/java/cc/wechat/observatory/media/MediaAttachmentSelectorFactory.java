package cc.wechat.observatory.media;

final class MediaAttachmentSelectorFactory {
    private MediaAttachmentSelectorFactory() {
    }

    static MediaFileSelector create(
            ImageDownloadRequestTracker imageDownloadTracker,
            final MediaAttachmentServices services) {
        return new MediaFileSelector(
                MediaAttachmentSelectorServiceAdapters.baseResolver(services),
                WechatImageDownloadResolverFactory.create(imageDownloadTracker, services),
                null,
                null,
                MediaAttachmentSelectorServiceAdapters.logger(services));
    }
}
