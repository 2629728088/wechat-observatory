package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatVideoCacheMediaResolver {
    private WechatVideoCacheMediaResolver() {
    }

    static MediaResolver.Result resolve(
            File appRoot,
            List<String> names) {
        return MediaResolver.Result.found(
                MediaResolver.ResolutionStatus.VIDEO_CACHE_FILE,
                NamedMediaFileFinder.findInProfileRoots(
                        new File(appRoot, "cache"),
                        MediaSearchPlan.videoCacheSearchRoots(),
                        names));
    }
}
