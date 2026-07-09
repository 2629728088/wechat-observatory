package cc.wechat.observatory.media;

import java.io.File;

final class WechatGchatImageSelection {
    private final MediaFileSelector.GchatImageResolver resolver;
    private final MediaFileSelector.Logger logger;

    WechatGchatImageSelection(
            MediaFileSelector.GchatImageResolver resolver,
            MediaFileSelector.Logger logger) {
        this.resolver = resolver;
        this.logger = logger;
    }

    MediaFileSelector.Selection select(MediaFileSelector.Request request) {
        if (resolver == null || request == null) {
            return null;
        }
        File file = resolver.resolve(request.content());
        if (!isUsable(file)) {
            return null;
        }
        if (WechatImageFiles.isLowQualityThumbnailFile(file)) {
            log("skip thumbnail image upload msgId=" + request.chatRecordId()
                    + " file=" + file.getName()
                    + " size=" + file.length());
            return MediaFileSelector.Selection.of(MediaFileSelector.SelectionStatus.GCHAT_IMAGE_THUMBNAIL, null);
        }
        return MediaFileSelector.Selection.of(MediaFileSelector.SelectionStatus.GCHAT_IMAGE_FILE, file);
    }

    private static boolean isUsable(File file) {
        return MediaFiles.isExistingFile(file);
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
