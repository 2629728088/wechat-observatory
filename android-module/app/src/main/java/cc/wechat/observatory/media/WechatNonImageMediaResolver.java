package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatNonImageMediaResolver {
    private WechatNonImageMediaResolver() {
    }

    static MediaResolver.Result resolve(
            File appRoot,
            int type,
            List<String> names,
            long createTime,
            MediaResolver.Logger logger) {
        if (appRoot == null) {
            return MediaResolver.Result.notFound();
        }
        File microMsgRoot = new File(appRoot, "MicroMsg");
        MediaResolver.Result profile = WechatProfileMediaResolver.resolve(microMsgRoot, type, names, logger);
        if (!profile.isNotFound()) {
            return profile;
        }
        if (MediaFiles.isEmojiMessageType(type)) {
            return WechatEmojiMediaResolver.resolveFallback(appRoot, names, logger);
        }
        if (MediaFiles.isVoiceMessageType(type)) {
            return WechatVoiceMediaResolver.resolveRecent(microMsgRoot, createTime, logger);
        }
        if (MediaFiles.isVideoMessageType(type)) {
            return WechatVideoCacheMediaResolver.resolve(appRoot, names);
        }
        return MediaResolver.Result.notFound();
    }
}
