package cc.wechat.observatory.media;

final class WechatImageResultMapper {
    private WechatImageResultMapper() {
    }

    static MediaResolver.Result mediaResult(
            WechatImageFileResolver.CandidateResult candidate,
            boolean direct) {
        return mediaResult(WechatImageResolvedCandidate.from(candidate), direct);
    }

    private static MediaResolver.Result mediaResult(
            WechatImageResolvedCandidate candidate,
            boolean direct) {
        if (candidate == null || candidate.isMissing()) {
            return MediaResolver.Result.notFound();
        }
        if (candidate.hasResolvedFile()) {
            return MediaResolver.Result.found(
                    WechatImageStatusMapper.mediaFileStatus(candidate, direct),
                    candidate.file(),
                    candidate.source());
        }
        if (candidate.isLowQualityThumbnail()) {
            return MediaResolver.Result.thumbnail(
                    WechatImageStatusMapper.mediaThumbnailStatus(direct),
                    candidate.source());
        }
        if (candidate.isUnsupported()) {
            return MediaResolver.Result.unsupported(
                    WechatImageStatusMapper.mediaUnsupportedStatus(direct),
                    candidate.source());
        }
        return MediaResolver.Result.notFound();
    }
}
