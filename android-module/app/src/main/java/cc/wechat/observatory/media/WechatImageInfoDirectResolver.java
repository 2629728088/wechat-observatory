package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageInfoDirectResolver {
    private WechatImageInfoDirectResolver() {
    }

    static WechatImageFileResolver.CandidateResolution resolve(
            File appRoot,
            List<String> imageInfoValues,
            WechatImageFileResolver.Logger logger) {
        if (imageInfoValues == null) {
            return WechatImageFileResolver.CandidateResolution.missing(null);
        }
        WechatImageFileResolver.CandidateResolution bestMiss =
                WechatImageFileResolver.CandidateResolution.missing(null);
        for (String value : imageInfoValues) {
            if (!WechatImageInfoCandidateNames.isSearchableValue(value)) {
                continue;
            }
            WechatImageFileResolver.CandidateResolution resolved =
                    WechatImageDirectCandidateResolver.resolve(appRoot, value, logger);
            if (resolved.hasResolvedFile()) {
                return resolved;
            }
            bestMiss = WechatImageMissPriority.firstNonMissing(bestMiss, resolved);
        }
        return bestMiss;
    }
}
