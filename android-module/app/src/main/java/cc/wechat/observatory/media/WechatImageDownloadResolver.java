package cc.wechat.observatory.media;

public final class WechatImageDownloadResolver implements MediaFileSelector.ImageDownloadResolver {
    public interface DownloadRequester {
        boolean request(long localId, long serverId, String talker);
    }

    public interface CandidateFallbackResolver {
        ImageDownloadResolution.Candidate resolve(String mediaHint, long createTime);
    }

    public interface CandidateImageInfoResolver {
        ImageDownloadResolution.Candidate resolve(long localId, long serverId, String talker);
    }

    public interface Logger {
        void log(String message);
    }

    private final ImageDownloadCoordinator coordinator;

    public WechatImageDownloadResolver(
            ImageDownloadRequestTracker tracker,
            DownloadRequester downloadRequester,
            CandidateImageInfoResolver imageInfoResolver,
            CandidateFallbackResolver fallbackResolver,
            Logger logger) {
        this.coordinator = new ImageDownloadCoordinator(
                tracker,
                WechatImageDownloadCoordinatorAdapters.downloader(downloadRequester),
                WechatImageDownloadCoordinatorAdapters.imageInfoResolver(imageInfoResolver),
                WechatImageDownloadCoordinatorAdapters.fallbackResolver(fallbackResolver),
                WechatImageDownloadCoordinatorAdapters.logger(logger));
    }

    WechatImageDownloadResolver(
            ImageDownloadRequestTracker tracker,
            WechatImageDownloadResolverDependencies dependencies) {
        this(
                tracker,
                dependencies == null ? null : dependencies.downloadRequester(),
                dependencies == null ? null : dependencies.imageInfoResolver(),
                dependencies == null ? null : dependencies.fallbackResolver(),
                dependencies == null ? null : dependencies.logger());
    }

    @Override
    public ImageDownloadResolution resolve(String mediaHint, Long msgId, Long msgSvrId, long createTime, String talker) {
        return coordinator.requestAndResolve(mediaHint, msgId, msgSvrId, createTime, talker);
    }
}
