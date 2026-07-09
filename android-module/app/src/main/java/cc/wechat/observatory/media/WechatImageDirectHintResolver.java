package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageDirectHintResolver {
    private WechatImageDirectHintResolver() {
    }

    static MediaResolver.Result resolve(
            File appRoot,
            String mediaHint,
            MediaResolver.Logger logger) {
        return WechatImageResultMapper.mediaResult(
                WechatImageDirectCandidateResolver.resolve(
                        appRoot,
                        mediaHint,
                        WechatImageLoggerAdapters.fileLogger(logger)),
                true);
    }
}
