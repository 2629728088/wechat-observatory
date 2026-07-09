package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageDirectCandidateResolver {
    private WechatImageDirectCandidateResolver() {
    }

    static WechatImageFileResolver.CandidateResolution resolve(
            File appRoot,
            String value,
            WechatImageFileResolver.Logger logger) {
        File direct = MediaDirectFileResolver.resolve(appRoot, value);
        return WechatImageFileResolver.resolveCandidateDetails(direct, logger);
    }
}
