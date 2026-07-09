package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageProfileMediaResolver {
    private WechatImageProfileMediaResolver() {
    }

    static MediaResolver.Result resolve(
            File appRoot,
            List<String> names,
            MediaResolver.Logger logger) {
        WechatImageFileResolver.ProfileSearchResult imageSearch =
                WechatImageProfileResolver.find(
                        appRoot,
                        names,
                        WechatImageLoggerAdapters.fileLogger(logger));
        logSelectedFile(logger, imageSearch);
        return WechatImageResultMapper.mediaResult(imageSearch, false);
    }

    private static void logSelectedFile(
            MediaResolver.Logger logger,
            WechatImageFileResolver.ProfileSearchResult imageSearch) {
        if (logger != null && imageSearch != null && imageSearch.hasResolvedFile()) {
            logger.log("image media selected file=" + imageSearch.file().getName() + " size=" + imageSearch.file().length());
        }
    }
}
