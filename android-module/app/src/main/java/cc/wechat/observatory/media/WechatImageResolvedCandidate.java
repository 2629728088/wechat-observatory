package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageResolvedCandidate {
    private final WechatImageFileResolver.CandidateKind kind;
    private final File source;
    private final File file;

    private WechatImageResolvedCandidate(
            WechatImageFileResolver.CandidateKind kind,
            File source,
            File file) {
        this.kind = kind == null ? WechatImageFileResolver.CandidateKind.MISSING : kind;
        this.source = source;
        this.file = file;
    }

    static WechatImageResolvedCandidate from(
            WechatImageFileResolver.CandidateResult result) {
        if (result == null) {
            return missing();
        }
        return new WechatImageResolvedCandidate(
                result.kind(),
                result.source(),
                result.file());
    }

    static WechatImageResolvedCandidate missing() {
        return new WechatImageResolvedCandidate(
                WechatImageFileResolver.CandidateKind.MISSING,
                null,
                null);
    }

    boolean isMissing() {
        return kind == WechatImageFileResolver.CandidateKind.MISSING;
    }

    boolean hasResolvedFile() {
        return file != null;
    }

    boolean isReferenceTarget() {
        return kind == WechatImageFileResolver.CandidateKind.REF_TARGET;
    }

    boolean isLowQualityThumbnail() {
        return kind == WechatImageFileResolver.CandidateKind.LOW_QUALITY_THUMBNAIL;
    }

    boolean isUnsupported() {
        return kind == WechatImageFileResolver.CandidateKind.UNSUPPORTED;
    }

    File source() {
        return source;
    }

    File file() {
        return file;
    }
}
