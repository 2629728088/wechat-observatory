package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageMediaResolver {
    private WechatImageMediaResolver() {
    }

    static MediaResolver.Result resolve(
            File appRoot,
            String mediaHint,
            List<String> names,
            MediaResolver.Logger logger) {
        MediaResolver.Result directResult = WechatImageDirectHintResolver.resolve(appRoot, mediaHint, logger);
        if (!directResult.isNotFound()) {
            return directResult;
        }
        if (appRoot == null) {
            return MediaResolver.Result.notFound();
        }
        if (names != null && !names.isEmpty()) {
            MediaResolver.Result result = WechatImageProfileMediaResolver.resolve(appRoot, names, logger);
            if (!result.isNotFound()) {
                return result;
            }
            log(logger, "image named lookup missed; skip ambiguous recent fallback names=" + names.size());
        }
        return MediaResolver.Result.notFound();
    }

    private static void log(MediaResolver.Logger logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
