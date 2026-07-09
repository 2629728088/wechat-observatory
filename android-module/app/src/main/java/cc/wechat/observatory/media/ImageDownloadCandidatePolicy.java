package cc.wechat.observatory.media;

final class ImageDownloadCandidatePolicy {
    private ImageDownloadCandidatePolicy() {
    }

    static boolean isUsable(ImageDownloadResolution.Candidate candidate) {
        return candidate != null && candidate.hasExistingFile();
    }

    static boolean isImmediateImageInfoCandidate(ImageDownloadResolution.Candidate candidate) {
        return isUsable(candidate)
                && !candidate.isLowQualityThumbnail()
                && !candidate.isUnsupported();
    }

    static boolean isDeferredImageInfoCandidate(ImageDownloadResolution.Candidate candidate) {
        return isUsable(candidate)
                && (candidate.isLowQualityThumbnail() || candidate.isUnsupported());
    }

    static boolean shouldResolveDownloadedFallback(ImageDownloadResolution.Candidate imageInfoCandidate) {
        return !isImmediateImageInfoCandidate(imageInfoCandidate);
    }
}
