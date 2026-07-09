package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageInfoResolver {
    private WechatImageInfoResolver() {
    }

    static MediaResolver.ImageInfoResult resolve(File appRoot, Object imageInfo, MediaResolver.Logger logger) {
        if (appRoot == null || imageInfo == null) {
            return MediaResolver.ImageInfoResult.missing();
        }
        return resolveSnapshot(appRoot, WechatImageInfoSnapshot.from(imageInfo), logger);
    }

    static MediaResolver.ImageInfoResult resolveValues(
            File appRoot,
            long localInfoId,
            List<String> values,
            MediaResolver.Logger logger) {
        return resolveSnapshot(
                appRoot,
                WechatImageInfoSnapshot.of(localInfoId, values, null),
                logger);
    }

    private static MediaResolver.ImageInfoResult resolveSnapshot(
            File appRoot,
            WechatImageInfoSnapshot imageInfo,
            MediaResolver.Logger logger) {
        return WechatImageInfoValueResolver.resolve(appRoot, imageInfo, logger);
    }
}
