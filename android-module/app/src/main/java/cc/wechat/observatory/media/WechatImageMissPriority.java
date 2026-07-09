package cc.wechat.observatory.media;

final class WechatImageMissPriority {
    private WechatImageMissPriority() {
    }

    static WechatImageFileResolver.ProfileSearchResult better(
            WechatImageFileResolver.ProfileSearchResult current,
            WechatImageFileResolver.ProfileSearchResult candidate) {
        if (candidate == null || candidate.isMissing()) {
            return current == null ? WechatImageFileResolver.ProfileSearchResult.missing() : current;
        }
        if (current == null || current.isMissing()) {
            return candidate;
        }
        if (current.isUnsupported() && candidate.isLowQualityThumbnail()) {
            return candidate;
        }
        return current;
    }

    static WechatImageFileResolver.CandidateResolution firstNonMissing(
            WechatImageFileResolver.CandidateResolution current,
            WechatImageFileResolver.CandidateResolution candidate) {
        if (current != null && !current.isMissing()) {
            return current;
        }
        if (candidate != null && !candidate.isMissing()) {
            return candidate;
        }
        return current == null ? WechatImageFileResolver.CandidateResolution.missing(null) : current;
    }
}
