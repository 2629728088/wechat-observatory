package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageRecursiveScanner {
    private static final int VISIT_LIMIT = 6000;

    private WechatImageRecursiveScanner() {
    }

    static WechatImageFileResolver.ProfileSearchResult scan(
            File root,
            List<String> names,
            int depth,
            WechatImageFileResolver.Logger logger) {
        return scan(root, names, depth, new int[]{0}, logger);
    }

    private static WechatImageFileResolver.ProfileSearchResult scan(
            File root,
            List<String> names,
            int depth,
            int[] visited,
            WechatImageFileResolver.Logger logger) {
        if (root == null || !root.isDirectory() || depth < 0 || visited[0] > VISIT_LIMIT) {
            return WechatImageFileResolver.ProfileSearchResult.missing();
        }
        File[] files = MediaFileEntries.sortedChildren(root);
        if (files.length == 0) {
            return WechatImageFileResolver.ProfileSearchResult.missing();
        }
        WechatImageFileResolver.ProfileSearchResult bestMiss =
                WechatImageFileResolver.ProfileSearchResult.missing();
        for (File file : files) {
            if (file == null) {
                continue;
            }
            visited[0]++;
            if (MediaFiles.isExistingFile(file) && WechatImageSearchPlan.matchesCandidateName(file.getName(), names)) {
                WechatImageFileResolver.CandidateResolution resolution =
                        WechatImageFileResolver.resolveCandidateDetails(file, logger);
                if (resolution.hasResolvedFile()) {
                    return WechatImageFileResolver.ProfileSearchResult.from(resolution);
                }
                bestMiss = WechatImageMissPriority.better(
                        bestMiss,
                        WechatImageFileResolver.ProfileSearchResult.from(resolution));
            }
        }
        for (File file : files) {
            if (file != null && file.isDirectory()) {
                WechatImageFileResolver.ProfileSearchResult found =
                        scan(file, names, depth - 1, visited, logger);
                if (found.hasResolvedFile()) {
                    return found;
                }
                bestMiss = WechatImageMissPriority.better(bestMiss, found);
            }
        }
        return bestMiss;
    }
}
