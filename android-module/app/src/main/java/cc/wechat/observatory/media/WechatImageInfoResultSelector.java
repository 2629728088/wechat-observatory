package cc.wechat.observatory.media;

import java.util.List;

final class WechatImageInfoResultSelector {
    private WechatImageInfoResultSelector() {
    }

    static MediaResolver.ImageInfoResult withoutCandidateNames(
            WechatImageFileResolver.CandidateResolution direct,
            long localInfoId,
            WechatImageInfoCandidateNames names) {
        MediaResolver.ImageInfoResult directMiss =
                WechatImageInfoResultMapper.miss(direct, true, localInfoId, names.values(), null);
        return directMiss == null
                ? MediaResolver.ImageInfoResult.of(
                        MediaResolver.ImageInfoStatus.NO_CANDIDATE_NAMES,
                        null,
                        null,
                        localInfoId,
                        names.values(),
                        null)
                : directMiss;
    }

    static MediaResolver.ImageInfoResult miss(
            WechatImageFileResolver.CandidateResolution direct,
            WechatImageFileResolver.ProfileSearchResult imageSearch,
            long localInfoId,
            WechatImageInfoCandidateNames names,
            List<String> fieldDebug) {
        MediaResolver.ImageInfoResult profileMiss =
                WechatImageInfoResultMapper.miss(
                        imageSearch,
                        false,
                        localInfoId,
                        names.values(),
                        fieldDebug);
        if (profileMiss != null) {
            return profileMiss;
        }
        MediaResolver.ImageInfoResult directMiss =
                WechatImageInfoResultMapper.miss(
                        direct,
                        true,
                        localInfoId,
                        names.values(),
                        fieldDebug);
        return directMiss == null
                ? MediaResolver.ImageInfoResult.of(
                        MediaResolver.ImageInfoStatus.CANDIDATES_NOT_FOUND,
                        null,
                        null,
                        localInfoId,
                        names.values(),
                        fieldDebug)
                : directMiss;
    }
}
