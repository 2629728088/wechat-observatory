package cc.wechat.observatory.media;

final class MediaAttachmentSelectorServiceAdapters {
    private MediaAttachmentSelectorServiceAdapters() {
    }

    static MediaFileSelector.BaseResolver baseResolver(final MediaAttachmentServices services) {
        return new MediaFileSelector.BaseResolver() {
            @Override
            public ImageDownloadResolution.Candidate resolve(
                    int type, String mediaHint, long createTime, String emojiMd5) {
                return services == null
                        ? ImageDownloadResolution.Candidate.missing()
                        : services.resolveMediaFileCandidate(type, mediaHint, createTime, emojiMd5);
            }
        };
    }

    static MediaFileSelector.Logger logger(final MediaAttachmentServices services) {
        return new MediaFileSelector.Logger() {
            @Override
            public void log(String message) {
                if (services != null) {
                    services.log(message);
                }
            }
        };
    }
}
