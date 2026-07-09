package cc.wechat.observatory.media;

final class WechatImageStatusMapper {
    private WechatImageStatusMapper() {
    }

    static MediaResolver.ResolutionStatus mediaFileStatus(
            boolean referenceTarget,
            boolean direct) {
        if (referenceTarget) {
            return direct
                    ? MediaResolver.ResolutionStatus.DIRECT_IMAGE_REF_TARGET
                    : MediaResolver.ResolutionStatus.PROFILE_IMAGE_REF_TARGET;
        }
        return direct
                ? MediaResolver.ResolutionStatus.DIRECT_IMAGE_FILE
                : MediaResolver.ResolutionStatus.PROFILE_IMAGE_FILE;
    }

    static MediaResolver.ResolutionStatus mediaFileStatus(
            WechatImageResolvedCandidate candidate,
            boolean direct) {
        return mediaFileStatus(candidate != null && candidate.isReferenceTarget(), direct);
    }

    static MediaResolver.ResolutionStatus mediaThumbnailStatus(boolean direct) {
        return direct
                ? MediaResolver.ResolutionStatus.DIRECT_IMAGE_THUMBNAIL
                : MediaResolver.ResolutionStatus.PROFILE_IMAGE_THUMBNAIL;
    }

    static MediaResolver.ResolutionStatus mediaUnsupportedStatus(boolean direct) {
        return direct
                ? MediaResolver.ResolutionStatus.DIRECT_IMAGE_UNSUPPORTED
                : MediaResolver.ResolutionStatus.PROFILE_IMAGE_UNSUPPORTED;
    }

    static MediaResolver.ImageInfoStatus imageInfoFileStatus(
            boolean referenceTarget,
            boolean direct) {
        if (referenceTarget) {
            return direct
                    ? MediaResolver.ImageInfoStatus.DIRECT_IMAGE_REF_TARGET
                    : MediaResolver.ImageInfoStatus.PROFILE_IMAGE_REF_TARGET;
        }
        return direct
                ? MediaResolver.ImageInfoStatus.DIRECT_IMAGE_FILE
                : MediaResolver.ImageInfoStatus.PROFILE_IMAGE_FILE;
    }

    static MediaResolver.ImageInfoStatus imageInfoFileStatus(
            WechatImageResolvedCandidate candidate,
            boolean direct) {
        return imageInfoFileStatus(candidate != null && candidate.isReferenceTarget(), direct);
    }

    static MediaResolver.ImageInfoStatus imageInfoThumbnailStatus(boolean direct) {
        return direct
                ? MediaResolver.ImageInfoStatus.DIRECT_IMAGE_THUMBNAIL
                : MediaResolver.ImageInfoStatus.PROFILE_IMAGE_THUMBNAIL;
    }

    static MediaResolver.ImageInfoStatus imageInfoUnsupportedStatus(boolean direct) {
        return direct
                ? MediaResolver.ImageInfoStatus.DIRECT_IMAGE_UNSUPPORTED
                : MediaResolver.ImageInfoStatus.PROFILE_IMAGE_UNSUPPORTED;
    }
}
