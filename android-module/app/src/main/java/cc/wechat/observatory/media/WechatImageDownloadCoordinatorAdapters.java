package cc.wechat.observatory.media;

final class WechatImageDownloadCoordinatorAdapters {
    private WechatImageDownloadCoordinatorAdapters() {
    }

    static ImageDownloadCoordinator.Downloader downloader(
            final WechatImageDownloadResolver.DownloadRequester requester) {
        return new ImageDownloadCoordinator.Downloader() {
            @Override
            public boolean request(long localId, long serverId, String talker) {
                return requester != null && requester.request(localId, serverId, talker);
            }
        };
    }

    static ImageDownloadCoordinator.CandidateImageInfoResolver imageInfoResolver(
            final WechatImageDownloadResolver.CandidateImageInfoResolver resolver) {
        return new ImageDownloadCoordinator.CandidateImageInfoResolver() {
            @Override
            public ImageDownloadResolution.Candidate resolve(long localId, long serverId, String talker) {
                return resolver == null
                        ? ImageDownloadResolution.Candidate.missing()
                        : resolver.resolve(localId, serverId, talker);
            }
        };
    }

    static ImageDownloadCoordinator.CandidateFallbackResolver fallbackResolver(
            final WechatImageDownloadResolver.CandidateFallbackResolver resolver) {
        return new ImageDownloadCoordinator.CandidateFallbackResolver() {
            @Override
            public ImageDownloadResolution.Candidate resolve(String mediaHint, long createTime) {
                return resolver == null
                        ? ImageDownloadResolution.Candidate.missing()
                        : resolver.resolve(mediaHint, createTime);
            }
        };
    }

    static ImageDownloadCoordinator.Logger logger(final WechatImageDownloadResolver.Logger logger) {
        return new ImageDownloadCoordinator.Logger() {
            @Override
            public void log(String message) {
                if (logger != null) {
                    logger.log(message);
                }
            }
        };
    }
}
