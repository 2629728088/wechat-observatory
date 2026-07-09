package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageNamedFileFinder {
    private WechatImageNamedFileFinder() {
    }

    static WechatImageFileResolver.ProfileSearchResult findInProfileRootsDetails(
            File profileRoot,
            String[] roots,
            List<String> names,
            WechatImageFileResolver.Logger logger) {
        return WechatImageProfileRootFinder.find(profileRoot, roots, names, logger);
    }

    static WechatImageFileResolver.ProfileSearchResult find(
            File root,
            List<String> names,
            int depth,
            WechatImageFileResolver.Logger logger) {
        if (names == null || names.isEmpty()) {
            return WechatImageFileResolver.ProfileSearchResult.missing();
        }
        WechatImageFileResolver.ProfileSearchResult bucketed =
                WechatImageBucketedFinder.find(root, names, logger);
        if (bucketed.hasResolvedFile()) {
            return bucketed;
        }
        WechatImageFileResolver.ProfileSearchResult scanned =
                WechatImageRecursiveScanner.scan(root, names, depth, logger);
        return resolvedOrBestMiss(bucketed, scanned);
    }

    private static WechatImageFileResolver.ProfileSearchResult resolvedOrBestMiss(
            WechatImageFileResolver.ProfileSearchResult first,
            WechatImageFileResolver.ProfileSearchResult second) {
        if (first != null && first.hasResolvedFile()) {
            return first;
        }
        if (second != null && second.hasResolvedFile()) {
            return second;
        }
        return WechatImageMissPriority.better(first, second);
    }

}
