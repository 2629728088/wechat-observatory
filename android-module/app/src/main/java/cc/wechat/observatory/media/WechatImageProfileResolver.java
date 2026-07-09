package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageProfileResolver {
    private WechatImageProfileResolver() {
    }

    static WechatImageFileResolver.ProfileSearchResult find(
            File appRoot,
            List<String> names,
            WechatImageFileResolver.Logger logger) {
        if (appRoot == null || names == null || names.isEmpty()) {
            return WechatImageFileResolver.ProfileSearchResult.missing();
        }
        return WechatImageNamedFileFinder.findInProfileRootsDetails(
                new File(appRoot, "MicroMsg"),
                MediaSearchPlan.imageSearchRoots(),
                names,
                logger);
    }
}
