package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

import static cc.wechat.observatory.util.Strings.isBlank;

final class WechatImageBucketedFinder {
    private WechatImageBucketedFinder() {
    }

    static WechatImageFileResolver.ProfileSearchResult find(
            File root,
            List<String> names,
            WechatImageFileResolver.Logger logger) {
        WechatImageFileResolver.ProfileSearchResult bestMiss =
                WechatImageFileResolver.ProfileSearchResult.missing();
        if (names == null || names.isEmpty()) {
            return bestMiss;
        }
        for (String name : names) {
            WechatImageFileResolver.ProfileSearchResult direct =
                    findOne(root, name, logger);
            if (direct.hasResolvedFile()) {
                return direct;
            }
            bestMiss = WechatImageMissPriority.better(bestMiss, direct);
        }
        return bestMiss;
    }

    private static WechatImageFileResolver.ProfileSearchResult findOne(
            File root,
            String name,
            WechatImageFileResolver.Logger logger) {
        if (root == null || !root.isDirectory() || isBlank(name)) {
            return WechatImageFileResolver.ProfileSearchResult.missing();
        }
        String bucketKey = WechatImageSearchPlan.bucketKey(name);
        if (bucketKey.length() < 4) {
            return WechatImageFileResolver.ProfileSearchResult.missing();
        }
        return WechatImageBucketResolver.find(root, bucketKey, name.trim(), logger);
    }
}
