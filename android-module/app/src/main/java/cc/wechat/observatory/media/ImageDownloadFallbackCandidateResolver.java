package cc.wechat.observatory.media;

final class ImageDownloadFallbackCandidateResolver {
    private final ImageDownloadCoordinator.CandidateFallbackResolver resolver;

    ImageDownloadFallbackCandidateResolver(ImageDownloadCoordinator.CandidateFallbackResolver resolver) {
        this.resolver = resolver;
    }

    ImageDownloadResolution.Candidate resolve(
            ImageDownloadResolution.Candidate imageInfoCandidate,
            String mediaHint,
            long createTime) {
        if (!ImageDownloadFallbackGate.shouldResolveFallback(imageInfoCandidate)) {
            return ImageDownloadResolution.Candidate.missing();
        }
        if (resolver == null) {
            return ImageDownloadResolution.Candidate.missing();
        }
        return resolver.resolve(mediaHint, createTime);
    }
}
