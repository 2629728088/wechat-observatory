package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatEmojiMediaResolver {
    private WechatEmojiMediaResolver() {
    }

    static MediaResolver.Result resolveFallback(
            File appRoot,
            List<String> names,
            MediaResolver.Logger logger) {
        if (names == null || names.isEmpty()) {
            return MediaResolver.Result.emojiDiagnosticNeeded();
        }
        for (String root : MediaSearchPlan.emojiFallbackSearchRoots()) {
            File found = NamedMediaFileFinder.findInRoot(
                    new File(appRoot, root),
                    names,
                    MediaSearchPlan.emojiFallbackSearchDepth(root));
            if (found != null) {
                logSelected(logger, found);
                return MediaResolver.Result.found(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, found);
            }
        }
        return MediaResolver.Result.emojiDiagnosticNeeded();
    }

    static void logSelected(MediaResolver.Logger logger, File file) {
        if (logger != null && file != null) {
            logger.log("emoji media selected file=" + file.getName() + " size=" + file.length());
        }
    }
}
