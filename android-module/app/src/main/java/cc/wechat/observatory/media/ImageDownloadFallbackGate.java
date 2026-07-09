package cc.wechat.observatory.media;

final class ImageDownloadFallbackGate {
    private ImageDownloadFallbackGate() {
    }

    static boolean shouldResolveFallback(ImageDownloadResolution.Candidate imageInfoCandidate) {
        return ImageDownloadCandidatePolicy.shouldResolveDownloadedFallback(imageInfoCandidate);
    }
}
