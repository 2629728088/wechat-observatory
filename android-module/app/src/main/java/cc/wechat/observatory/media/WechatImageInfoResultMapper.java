package cc.wechat.observatory.media;

import java.util.List;

final class WechatImageInfoResultMapper {
    private WechatImageInfoResultMapper() {
    }

    static MediaResolver.ImageInfoResult file(
            WechatImageFileResolver.CandidateResult candidate,
            boolean direct,
            long localInfoId,
            List<String> candidateNames,
            List<String> fieldDebug) {
        return file(
                WechatImageResolvedCandidate.from(candidate),
                direct,
                localInfoId,
                candidateNames,
                fieldDebug);
    }

    static MediaResolver.ImageInfoResult miss(
            WechatImageFileResolver.CandidateResult candidate,
            boolean direct,
            long localInfoId,
            List<String> candidateNames,
            List<String> fieldDebug) {
        return miss(
                WechatImageResolvedCandidate.from(candidate),
                direct,
                localInfoId,
                candidateNames,
                fieldDebug);
    }

    private static MediaResolver.ImageInfoResult file(
            WechatImageResolvedCandidate candidate,
            boolean direct,
            long localInfoId,
            List<String> candidateNames,
            List<String> fieldDebug) {
        if (candidate == null || !candidate.hasResolvedFile()) {
            return null;
        }
        return MediaResolver.ImageInfoResult.of(
                WechatImageStatusMapper.imageInfoFileStatus(candidate, direct),
                candidate.file(),
                candidate.source(),
                localInfoId,
                candidateNames,
                fieldDebug);
    }

    private static MediaResolver.ImageInfoResult miss(
            WechatImageResolvedCandidate candidate,
            boolean direct,
            long localInfoId,
            List<String> candidateNames,
            List<String> fieldDebug) {
        if (candidate == null || candidate.isMissing()) {
            return null;
        }
        if (candidate.isLowQualityThumbnail()) {
            return MediaResolver.ImageInfoResult.of(
                    WechatImageStatusMapper.imageInfoThumbnailStatus(direct),
                    null,
                    candidate.source(),
                    localInfoId,
                    candidateNames,
                    fieldDebug);
        }
        if (candidate.isUnsupported()) {
            return MediaResolver.ImageInfoResult.of(
                    WechatImageStatusMapper.imageInfoUnsupportedStatus(direct),
                    null,
                    candidate.source(),
                    localInfoId,
                    candidateNames,
                    fieldDebug);
        }
        return null;
    }
}
