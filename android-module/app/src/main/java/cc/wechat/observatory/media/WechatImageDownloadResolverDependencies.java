package cc.wechat.observatory.media;

final class WechatImageDownloadResolverDependencies {
    private final WechatImageDownloadResolver.DownloadRequester downloadRequester;
    private final WechatImageDownloadResolver.CandidateImageInfoResolver imageInfoResolver;
    private final WechatImageDownloadResolver.CandidateFallbackResolver fallbackResolver;
    private final WechatImageDownloadResolver.Logger logger;

    private WechatImageDownloadResolverDependencies(
            WechatImageDownloadResolver.DownloadRequester downloadRequester,
            WechatImageDownloadResolver.CandidateImageInfoResolver imageInfoResolver,
            WechatImageDownloadResolver.CandidateFallbackResolver fallbackResolver,
            WechatImageDownloadResolver.Logger logger) {
        this.downloadRequester = downloadRequester;
        this.imageInfoResolver = imageInfoResolver;
        this.fallbackResolver = fallbackResolver;
        this.logger = logger;
    }

    static WechatImageDownloadResolverDependencies fromServices(MediaAttachmentServices services) {
        return new WechatImageDownloadResolverDependencies(
                WechatImageDownloadServiceAdapters.downloadRequester(services),
                WechatImageDownloadServiceAdapters.imageInfoResolver(services),
                WechatImageDownloadServiceAdapters.fallbackResolver(services),
                WechatImageDownloadServiceAdapters.logger(services));
    }

    WechatImageDownloadResolver.DownloadRequester downloadRequester() {
        return downloadRequester;
    }

    WechatImageDownloadResolver.CandidateImageInfoResolver imageInfoResolver() {
        return imageInfoResolver;
    }

    WechatImageDownloadResolver.CandidateFallbackResolver fallbackResolver() {
        return fallbackResolver;
    }

    WechatImageDownloadResolver.Logger logger() {
        return logger;
    }
}
