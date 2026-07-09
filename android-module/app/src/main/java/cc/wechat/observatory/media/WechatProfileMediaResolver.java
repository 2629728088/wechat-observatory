package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatProfileMediaResolver {
    private WechatProfileMediaResolver() {
    }

    static MediaResolver.Result resolve(
            File microMsgRoot,
            int type,
            List<String> names,
            MediaResolver.Logger logger) {
        if (names == null || names.isEmpty()) {
            return MediaResolver.Result.notFound();
        }
        File found = NamedMediaFileFinder.findInProfileRoots(
                microMsgRoot,
                MediaSearchPlan.searchRoots(type),
                names);
        if (found == null) {
            return MediaResolver.Result.notFound();
        }
        if (MediaFiles.isEmojiMessageType(type)) {
            WechatEmojiMediaResolver.logSelected(logger, found);
            return MediaResolver.Result.found(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, found);
        }
        return MediaResolver.Result.found(MediaResolver.ResolutionStatus.PROFILE_MEDIA_FILE, found);
    }
}
