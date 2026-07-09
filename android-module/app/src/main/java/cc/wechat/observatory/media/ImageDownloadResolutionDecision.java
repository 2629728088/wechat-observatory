package cc.wechat.observatory.media;

final class ImageDownloadResolutionDecision {
    private ImageDownloadResolutionDecision() {
    }

    static ImageDownloadResolution fromImmediateImageInfoCandidate(
            long localId,
            ImageDownloadResolution.Candidate candidate) {
        if (!ImageDownloadCandidatePolicy.isImmediateImageInfoCandidate(candidate)) {
            return null;
        }
        return ImageDownloadResolutionMapper.fromImageInfo(localId, candidate);
    }

    static ImageDownloadResolution fromDownloadedFallbackCandidate(
            long localId,
            ImageDownloadResolution.Candidate candidate) {
        if (!ImageDownloadCandidatePolicy.isUsable(candidate)) {
            return null;
        }
        return ImageDownloadResolutionMapper.fromDownloadedFallback(localId, candidate);
    }

    static ImageDownloadResolution fromDeferredImageInfoCandidate(
            long localId,
            ImageDownloadResolution.Candidate candidate) {
        if (!ImageDownloadCandidatePolicy.isDeferredImageInfoCandidate(candidate)) {
            return null;
        }
        return ImageDownloadResolutionMapper.fromImageInfo(localId, candidate);
    }
}
