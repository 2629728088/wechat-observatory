package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageInfoValueResolver {
    private WechatImageInfoValueResolver() {
    }

    static MediaResolver.ImageInfoResult resolve(
            File appRoot,
            WechatImageInfoSnapshot imageInfo,
            MediaResolver.Logger logger) {
        if (appRoot == null || imageInfo == null) {
            return MediaResolver.ImageInfoResult.missing();
        }
        return WechatImageInfoResolutionFlow.resolve(appRoot, imageInfo, logger);
    }
}
