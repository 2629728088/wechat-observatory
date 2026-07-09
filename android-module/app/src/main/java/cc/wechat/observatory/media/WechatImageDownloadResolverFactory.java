package cc.wechat.observatory.media;

final class WechatImageDownloadResolverFactory {
    private WechatImageDownloadResolverFactory() {
    }

    static WechatImageDownloadResolver create(
            ImageDownloadRequestTracker imageDownloadTracker,
            final MediaAttachmentServices services) {
        return new WechatImageDownloadResolver(
                imageDownloadTracker,
                WechatImageDownloadResolverDependencies.fromServices(services));
    }
}
