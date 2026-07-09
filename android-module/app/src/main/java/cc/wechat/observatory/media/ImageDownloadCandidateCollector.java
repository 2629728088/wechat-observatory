package cc.wechat.observatory.media;

final class ImageDownloadCandidateCollector {
    private final ImageDownloadRequestAttempt requestAttempt;
    private final ImageDownloadCoordinator.CandidateImageInfoResolver imageInfoResolver;
    private final ImageDownloadFallbackCandidateResolver fallbackCandidateResolver;

    ImageDownloadCandidateCollector(
            ImageDownloadCoordinator.Downloader downloader,
            ImageDownloadCoordinator.CandidateImageInfoResolver imageInfoResolver,
            ImageDownloadCoordinator.CandidateFallbackResolver fallbackResolver) {
        this.requestAttempt = new ImageDownloadRequestAttempt(downloader);
        this.imageInfoResolver = imageInfoResolver;
        this.fallbackCandidateResolver = new ImageDownloadFallbackCandidateResolver(fallbackResolver);
    }

    ImageDownloadCandidateSet collect(
            ImageDownloadRequestGate.Decision requestDecision,
            String mediaHint,
            long createTime,
            String talker) {
        if (requestDecision == null || !requestDecision.canResolve()) {
            return ImageDownloadCandidateSet.missing();
        }
        boolean requestedDownload = requestDownloadBeforeCandidateResolution(requestDecision, talker);
        return resolveCandidatesAfterDownloadAttempt(
                requestDecision,
                requestedDownload,
                mediaHint,
                createTime,
                talker);
    }

    private boolean requestDownloadBeforeCandidateResolution(
            ImageDownloadRequestGate.Decision requestDecision,
            String talker) {
        return requestAttempt.request(requestDecision, talker);
    }

    private ImageDownloadCandidateSet resolveCandidatesAfterDownloadAttempt(
            ImageDownloadRequestGate.Decision requestDecision,
            boolean requestedDownload,
            String mediaHint,
            long createTime,
            String talker) {
        ImageDownloadResolution.Candidate imageInfoCandidate =
                resolveImageInfoCandidate(requestDecision.localId(), requestDecision.serverId(), talker);
        ImageDownloadResolution.Candidate downloadedFallbackCandidate =
                resolveDownloadedFallbackCandidate(imageInfoCandidate, mediaHint, createTime);
        return ImageDownloadCandidateSet.of(
                requestedDownload,
                imageInfoCandidate,
                downloadedFallbackCandidate);
    }

    private ImageDownloadResolution.Candidate resolveImageInfoCandidate(
            long localId,
            long serverId,
            String talker) {
        return imageInfoResolver == null
                ? ImageDownloadResolution.Candidate.missing()
                : imageInfoResolver.resolve(localId, serverId, talker);
    }

    private ImageDownloadResolution.Candidate resolveDownloadedFallbackCandidate(
            ImageDownloadResolution.Candidate imageInfoCandidate,
            String mediaHint,
            long createTime) {
        return fallbackCandidateResolver.resolve(imageInfoCandidate, mediaHint, createTime);
    }
}
