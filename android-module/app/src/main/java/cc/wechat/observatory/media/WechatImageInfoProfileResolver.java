package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageInfoProfileResolver {
    private WechatImageInfoProfileResolver() {
    }

    static Selection resolve(
            File appRoot,
            long localInfoId,
            WechatImageInfoCandidateNames names,
            MediaResolver.Logger logger) {
        WechatImageFileResolver.ProfileSearchResult candidate =
                WechatImageProfileResolver.find(
                        appRoot,
                        names.values(),
                        WechatImageLoggerAdapters.fileLogger(logger));
        return new Selection(
                candidate,
                WechatImageInfoResultMapper.file(
                        candidate,
                        false,
                        localInfoId,
                        names.values(),
                        null));
    }

    static final class Selection {
        private final WechatImageFileResolver.ProfileSearchResult candidate;
        private final MediaResolver.ImageInfoResult fileResult;

        private Selection(
                WechatImageFileResolver.ProfileSearchResult candidate,
                MediaResolver.ImageInfoResult fileResult) {
            this.candidate = candidate;
            this.fileResult = fileResult;
        }

        WechatImageFileResolver.ProfileSearchResult candidate() {
            return candidate;
        }

        MediaResolver.ImageInfoResult fileResult() {
            return fileResult;
        }

        boolean hasFileResult() {
            return fileResult != null;
        }
    }
}
