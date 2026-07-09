package cc.wechat.observatory.media;

import java.io.File;

final class WechatVoiceMediaResolver {
    private WechatVoiceMediaResolver() {
    }

    static MediaResolver.Result resolveRecent(
            File microMsgRoot,
            long createTime,
            MediaResolver.Logger logger) {
        return MediaResolver.Result.found(
                MediaResolver.ResolutionStatus.VOICE_RECENT_FILE,
                WechatVoiceFileResolver.findRecent(microMsgRoot, createTime, adaptLogger(logger)));
    }

    private static WechatVoiceFileResolver.Logger adaptLogger(final MediaResolver.Logger logger) {
        if (logger == null) {
            return null;
        }
        return new WechatVoiceFileResolver.Logger() {
            @Override
            public void log(String message) {
                logger.log(message);
            }
        };
    }
}
