package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageInfoResolutionFlow {
    private WechatImageInfoResolutionFlow() {
    }

    static MediaResolver.ImageInfoResult resolve(
            File appRoot,
            WechatImageInfoSnapshot imageInfo,
            MediaResolver.Logger logger) {
        long localInfoId = imageInfo.localInfoId();
        List<String> values = imageInfo.values();
        WechatImageFileResolver.CandidateResolution direct =
                resolveDirectCandidate(appRoot, values, logger);
        MediaResolver.ImageInfoResult directFile =
                WechatImageInfoResultMapper.file(direct, true, localInfoId, null, null);
        if (directFile != null) {
            return directFile;
        }

        WechatImageInfoCandidateNames names = WechatImageInfoCandidateNames.fromValues(values);
        if (names.isEmpty()) {
            return WechatImageInfoResultSelector.withoutCandidateNames(direct, localInfoId, names);
        }

        WechatImageInfoProfileResolver.Selection profile =
                WechatImageInfoProfileResolver.resolve(appRoot, localInfoId, names, logger);
        if (profile.hasFileResult()) {
            return profile.fileResult();
        }
        return WechatImageInfoResultSelector.miss(
                direct,
                profile.candidate(),
                localInfoId,
                names,
                imageInfo.fieldDebug());
    }

    private static WechatImageFileResolver.CandidateResolution resolveDirectCandidate(
            File appRoot,
            List<String> values,
            MediaResolver.Logger logger) {
        return WechatImageInfoDirectResolver.resolve(
                appRoot,
                values,
                WechatImageLoggerAdapters.fileLogger(logger));
    }
}
