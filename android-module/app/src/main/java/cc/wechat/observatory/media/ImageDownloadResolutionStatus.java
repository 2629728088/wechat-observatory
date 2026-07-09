package cc.wechat.observatory.media;

import java.io.File;

final class ImageDownloadResolutionStatus {
    private ImageDownloadResolutionStatus() {
    }

    static boolean hasSelectableFile(ImageDownloadResolution.Status status, File file) {
        return MediaFiles.isExistingFile(file) && isSelectableFileStatus(status);
    }

    static boolean isReferenceTarget(ImageDownloadResolution.Status status) {
        return status == ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET
                || status == ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET;
    }

    static boolean isImageInfoSource(ImageDownloadResolution.Status status) {
        return status == ImageDownloadResolution.Status.IMAGE_INFO_FILE
                || status == ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET
                || status == ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL
                || status == ImageDownloadResolution.Status.IMAGE_INFO_UNSUPPORTED;
    }

    static boolean isDownloadedFallbackSource(ImageDownloadResolution.Status status) {
        return status == ImageDownloadResolution.Status.DOWNLOADED_FILE
                || status == ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET
                || status == ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL
                || status == ImageDownloadResolution.Status.DOWNLOADED_UNSUPPORTED;
    }

    static boolean isThumbnailOnly(ImageDownloadResolution.Status status) {
        return status == ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL
                || status == ImageDownloadResolution.Status.DOWNLOADED_THUMBNAIL;
    }

    static boolean isUnsupported(ImageDownloadResolution.Status status) {
        return status == ImageDownloadResolution.Status.IMAGE_INFO_UNSUPPORTED
                || status == ImageDownloadResolution.Status.DOWNLOADED_UNSUPPORTED;
    }

    private static boolean isSelectableFileStatus(ImageDownloadResolution.Status status) {
        return status == ImageDownloadResolution.Status.IMAGE_INFO_FILE
                || status == ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET
                || status == ImageDownloadResolution.Status.DOWNLOADED_FILE
                || status == ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET;
    }
}
