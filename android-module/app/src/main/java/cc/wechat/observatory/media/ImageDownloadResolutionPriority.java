package cc.wechat.observatory.media;

final class ImageDownloadResolutionPriority {
    private ImageDownloadResolutionPriority() {
    }

    static ImageDownloadResolution resolve(
            long localId,
            long serverId,
            boolean requestedDownload,
            ImageDownloadResolution.Candidate imageInfoCandidate,
            ImageDownloadResolution.Candidate downloadedFallbackCandidate) {
        ImageDownloadResolution imageInfo =
                ImageDownloadResolutionDecision.fromImmediateImageInfoCandidate(
                        localId,
                        imageInfoCandidate);
        if (imageInfo != null) {
            return imageInfo;
        }
        ImageDownloadResolution downloaded =
                ImageDownloadResolutionDecision.fromDownloadedFallbackCandidate(
                        localId,
                        downloadedFallbackCandidate);
        if (downloaded != null) {
            return downloaded;
        }
        ImageDownloadResolution deferredImageInfo =
                ImageDownloadResolutionDecision.fromDeferredImageInfoCandidate(
                        localId,
                        imageInfoCandidate);
        if (deferredImageInfo != null) {
            return deferredImageInfo;
        }
        return notReady(localId, serverId, requestedDownload);
    }

    private static ImageDownloadResolution notReady(
            long localId,
            long serverId,
            boolean requestedDownload) {
        return new ImageDownloadResolution(
                ImageDownloadResolution.Status.NOT_READY,
                null,
                ImageDownloadResolutionLogLine.notReady(localId, serverId, requestedDownload));
    }
}
