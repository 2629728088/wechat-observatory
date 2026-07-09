package cc.wechat.observatory.media;

import java.io.File;

final class ImageDownloadResolutionEvaluator {
    private ImageDownloadResolutionEvaluator() {
    }

    static ImageDownloadResolution evaluateFiles(
            long localId,
            long serverId,
            boolean requestedDownload,
            File imageInfoFile,
            File downloadedFallbackFile) {
        return evaluateCandidates(
                localId,
                serverId,
                requestedDownload,
                ImageDownloadResolution.Candidate.fromFile(imageInfoFile),
                ImageDownloadResolution.Candidate.fromFile(downloadedFallbackFile));
    }

    static ImageDownloadResolution evaluateCandidates(
            long localId,
            long serverId,
            boolean requestedDownload,
            ImageDownloadResolution.Candidate imageInfoCandidate,
            ImageDownloadResolution.Candidate downloadedFallbackCandidate) {
        return ImageDownloadResolutionPriority.resolve(
                localId,
                serverId,
                requestedDownload,
                imageInfoCandidate,
                downloadedFallbackCandidate);
    }

    static ImageDownloadResolution evaluateCandidateSet(
            long localId,
            long serverId,
            ImageDownloadCandidateSet candidates) {
        ImageDownloadCandidateSet safeCandidates =
                candidates == null ? ImageDownloadCandidateSet.missing() : candidates;
        return evaluateCandidates(
                localId,
                serverId,
                safeCandidates.requestedDownload(),
                safeCandidates.imageInfoCandidate(),
                safeCandidates.downloadedFallbackCandidate());
    }
}
