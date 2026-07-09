package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

import static cc.wechat.observatory.util.Strings.isBlank;

final class WechatImageProfileRootFinder {
    private static final int DEFAULT_DEPTH = 6;

    private WechatImageProfileRootFinder() {
    }

    static WechatImageFileResolver.ProfileSearchResult find(
            File profileRoot,
            String[] roots,
            List<String> names,
            WechatImageFileResolver.Logger logger) {
        File[] profiles = MediaFileEntries.sortedChildren(profileRoot);
        if (profiles.length == 0 || roots == null || roots.length == 0) {
            return WechatImageFileResolver.ProfileSearchResult.missing();
        }
        WechatImageFileResolver.ProfileSearchResult bestMiss =
                WechatImageFileResolver.ProfileSearchResult.missing();
        for (File profile : profiles) {
            if (profile == null || !profile.isDirectory()) {
                continue;
            }
            for (String rootName : roots) {
                if (isBlank(rootName)) {
                    continue;
                }
                WechatImageFileResolver.ProfileSearchResult result =
                        WechatImageNamedFileFinder.find(new File(profile, rootName), names, DEFAULT_DEPTH, logger);
                if (result.hasResolvedFile()) {
                    return result;
                }
                bestMiss = WechatImageMissPriority.better(bestMiss, result);
            }
        }
        return bestMiss;
    }
}
