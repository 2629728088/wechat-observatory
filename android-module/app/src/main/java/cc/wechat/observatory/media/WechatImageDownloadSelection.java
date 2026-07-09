package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageDownloadSelection {
    private WechatImageDownloadSelection() {
    }

    static MediaFileSelector.Selection from(
            MediaFileSelector.Request request,
            ImageDownloadResolution resolution,
            MediaFileSelector.Logger logger) {
        File file = resolution == null ? null : resolution.file();
        if (MediaSelectionStatusMapper.hasSelectableImageDownloadFile(resolution)) {
            return MediaFileSelector.Selection.of(
                    MediaSelectionStatusMapper.imageDownloadFileStatus(resolution),
                    file);
        }
        MediaFileSelector.SelectionStatus thumbnailStatus =
                MediaSelectionStatusMapper.imageDownloadThumbnailStatus(resolution);
        if (thumbnailStatus != null) {
            log(logger, "skip thumbnail image upload msgId=" + chatRecordId(request)
                    + " status=" + resolution.status());
            return MediaFileSelector.Selection.of(thumbnailStatus, null);
        }
        MediaFileSelector.SelectionStatus unsupportedStatus =
                MediaSelectionStatusMapper.imageDownloadUnsupportedStatus(resolution);
        if (unsupportedStatus != null) {
            log(logger, "skip unsupported image upload msgId=" + chatRecordId(request)
                    + " status=" + resolution.status());
            return MediaFileSelector.Selection.of(unsupportedStatus, null);
        }
        return null;
    }

    private static long chatRecordId(MediaFileSelector.Request request) {
        return request == null ? 0L : request.chatRecordId();
    }

    private static void log(MediaFileSelector.Logger logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
