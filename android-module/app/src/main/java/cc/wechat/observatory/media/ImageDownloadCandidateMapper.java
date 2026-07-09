package cc.wechat.observatory.media;

final class ImageDownloadCandidateMapper {
    private ImageDownloadCandidateMapper() {
    }

    static ImageDownloadResolution.Candidate fromMediaResult(MediaResolver.Result result) {
        return fromSource(ImageDownloadCandidateSource.fromMediaResult(result));
    }

    static ImageDownloadResolution.Candidate fromImageInfoResult(MediaResolver.ImageInfoResult result) {
        return fromSource(ImageDownloadCandidateSource.fromImageInfoResult(result));
    }

    private static ImageDownloadResolution.Candidate fromSource(ImageDownloadCandidateSource source) {
        if (source == null || source.isMissing()) {
            return ImageDownloadResolution.Candidate.missing();
        }
        if (source.isThumbnail()) {
            return ImageDownloadResolution.Candidate.fromFile(source.source());
        }
        if (source.isUnsupported()) {
            return ImageDownloadResolution.Candidate.unsupported(source.source());
        }
        if (source.isReferenceTarget()) {
            return ImageDownloadResolution.Candidate.refTarget(source.file());
        }
        return ImageDownloadResolution.Candidate.fromFile(source.file());
    }
}
