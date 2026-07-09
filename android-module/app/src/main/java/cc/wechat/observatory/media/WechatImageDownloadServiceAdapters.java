package cc.wechat.observatory.media;

final class WechatImageDownloadServiceAdapters {
    private static final int DOWNLOADED_IMAGE_FALLBACK_TYPE = MediaFiles.MESSAGE_TYPE_IMAGE;
    private static final String DOWNLOADED_IMAGE_FALLBACK_EMOJI_MD5 = "";

    private WechatImageDownloadServiceAdapters() {
    }

    static WechatImageDownloadResolver.DownloadRequester downloadRequester(
            final MediaAttachmentServices services) {
        return new WechatImageDownloadResolver.DownloadRequester() {
            @Override
            public boolean request(long localId, long serverId, String talker) {
                return services != null && services.requestImageDownload(localId, serverId, talker);
            }
        };
    }

    static WechatImageDownloadResolver.CandidateImageInfoResolver imageInfoResolver(
            final MediaAttachmentServices services) {
        return new WechatImageDownloadResolver.CandidateImageInfoResolver() {
            @Override
            public ImageDownloadResolution.Candidate resolve(long localId, long serverId, String talker) {
                return services == null
                        ? ImageDownloadResolution.Candidate.missing()
                        : services.resolveImageInfoCandidate(localId, serverId, talker);
            }
        };
    }

    static WechatImageDownloadResolver.CandidateFallbackResolver fallbackResolver(
            final MediaAttachmentServices services) {
        return new WechatImageDownloadResolver.CandidateFallbackResolver() {
            @Override
            public ImageDownloadResolution.Candidate resolve(String mediaHint, long createTime) {
                return resolveDownloadedImageFallbackCandidate(services, mediaHint, createTime);
            }
        };
    }

    static WechatImageDownloadResolver.Logger logger(final MediaAttachmentServices services) {
        return new WechatImageDownloadResolver.Logger() {
            @Override
            public void log(String message) {
                if (services != null) {
                    services.log(message);
                }
            }
        };
    }

    private static ImageDownloadResolution.Candidate resolveDownloadedImageFallbackCandidate(
            MediaAttachmentServices services,
            String mediaHint,
            long createTime) {
        return services == null
                ? ImageDownloadResolution.Candidate.missing()
                : services.resolveMediaFileCandidate(
                        DOWNLOADED_IMAGE_FALLBACK_TYPE,
                        mediaHint,
                        createTime,
                        DOWNLOADED_IMAGE_FALLBACK_EMOJI_MD5);
    }
}
