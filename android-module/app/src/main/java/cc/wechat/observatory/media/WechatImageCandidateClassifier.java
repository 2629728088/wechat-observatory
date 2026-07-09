package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageCandidateClassifier {
    enum Kind {
        MISSING,
        REFERENCE_POINTER,
        LOW_QUALITY_THUMBNAIL,
        REAL_IMAGE,
        UNSUPPORTED
    }

    private WechatImageCandidateClassifier() {
    }

    static Kind classify(File file) {
        if (!MediaFiles.isExistingFile(file)) {
            return Kind.MISSING;
        }
        if (WechatImageFiles.isReferencePointerFile(file)) {
            return Kind.REFERENCE_POINTER;
        }
        if (WechatImageFiles.isLowQualityThumbnailFile(file)) {
            return Kind.LOW_QUALITY_THUMBNAIL;
        }
        if (MediaFiles.isLikelyImageMediaFile(file)) {
            return Kind.REAL_IMAGE;
        }
        return Kind.UNSUPPORTED;
    }
}
