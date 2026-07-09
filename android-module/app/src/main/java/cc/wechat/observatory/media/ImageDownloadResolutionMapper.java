package cc.wechat.observatory.media;

final class ImageDownloadResolutionMapper {
    private ImageDownloadResolutionMapper() {
    }

    static ImageDownloadResolution fromImageInfo(
            long localId,
            ImageDownloadResolution.Candidate candidate) {
        if (!ImageDownloadCandidatePolicy.isUsable(candidate)) {
            return null;
        }
        if (candidate.isReferenceTarget()) {
            return new ImageDownloadResolution(
                    ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET,
                    candidate.file(),
                    ImageDownloadResolutionLogLine.imageInfoRefTarget(localId, candidate.file()));
        }
        if (candidate.isLowQualityThumbnail()) {
            return new ImageDownloadResolution(
                    ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL,
                    null,
                    ImageDownloadResolutionLogLine.imageInfoThumbnail(localId, candidate.file()));
        }
        if (candidate.isUnsupported()) {
            return new ImageDownloadResolution(
                    ImageDownloadResolution.Status.IMAGE_INFO_UNSUPPORTED,
                    null,
                    ImageDownloadResolutionLogLine.imageInfoUnsupported(localId, candidate.file()));
        }
        return new ImageDownloadResolution(
                ImageDownloadResolution.Status.IMAGE_INFO_FILE,
                candidate.file(),
                ImageDownloadResolutionLogLine.imageInfoFile(localId, candidate.file()));
    }

    static ImageDownloadResolution fromDownloadedFallback(
            long localId,
            ImageDownloadResolution.Candidate candidate) {
        if (!ImageDownloadCandidatePolicy.isUsable(candidate)) {
            return null;
        }
        if (candidate.isReferenceTarget()) {
            return new ImageDownloadResolution(
                    ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET,
                    candidate.file(),
                    ImageDownloadResolutionLogLine.downloadedRefTarget(localId, candidate.file()));
        }
        if (candidate.isLowQualityThumbnail()) {
            return new ImageDownloadResolution(
                    ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL,
                    null,
                    ImageDownloadResolutionLogLine.downloadedThumbnail(localId, candidate.file()));
        }
        if (candidate.isUnsupported()) {
            return new ImageDownloadResolution(
                    ImageDownloadResolution.Status.DOWNLOADED_UNSUPPORTED,
                    null,
                    ImageDownloadResolutionLogLine.downloadedUnsupported(localId, candidate.file()));
        }
        return new ImageDownloadResolution(
                ImageDownloadResolution.Status.DOWNLOADED_FILE,
                candidate.file(),
                ImageDownloadResolutionLogLine.downloadedFile(localId, candidate.file()));
    }
}
