package cc.wechat.observatory.media;

final class MediaSelectionBaseCandidate {
    private final MediaFileSelector.BaseResolver resolver;

    MediaSelectionBaseCandidate(MediaFileSelector.BaseResolver resolver) {
        this.resolver = resolver;
    }

    ImageDownloadResolution.Candidate resolve(MediaFileSelector.Request request) {
        if (resolver == null || request == null) {
            return ImageDownloadResolution.Candidate.missing();
        }
        ImageDownloadResolution.Candidate candidate = resolver.resolve(
                request.type(),
                request.mediaHint(),
                request.createTime(),
                request.emojiMd5());
        return candidate == null ? ImageDownloadResolution.Candidate.missing() : candidate;
    }
}
