package cc.wechat.observatory.media;

import java.io.File;

import static cc.wechat.observatory.util.Strings.isBlank;

final class WechatImageBucketResolver {
    private WechatImageBucketResolver() {
    }

    static WechatImageFileResolver.ProfileSearchResult find(
            File root,
            String bucketKey,
            String fileName,
            WechatImageFileResolver.Logger logger) {
        if (root == null || !root.isDirectory() || bucketKey == null || bucketKey.length() < 4 || isBlank(fileName)) {
            return WechatImageFileResolver.ProfileSearchResult.missing();
        }
        File bucket = new File(new File(root, bucketKey.substring(0, 2)), bucketKey.substring(2, 4));
        return WechatImageFileResolver.ProfileSearchResult.from(
                WechatImageBucketCandidateResolver.resolve(bucket, fileName, logger));
    }
}
