package cc.wechat.observatory.media;

import java.io.File;

final class MediaSelectionStatusMapper {
    private MediaSelectionStatusMapper() {
    }

    static File candidateFile(ImageDownloadResolution.Candidate candidate) {
        return candidate == null ? null : candidate.file();
    }

    static boolean isThumbnailCandidate(ImageDownloadResolution.Candidate candidate) {
        return candidate != null && candidate.isLowQualityThumbnail();
    }

    static boolean isUnsupportedCandidate(ImageDownloadResolution.Candidate candidate) {
        return candidate != null && candidate.isUnsupported();
    }

    static MediaFileSelector.SelectionStatus baseStatus(ImageDownloadResolution.Candidate candidate) {
        if (candidate != null && candidate.isReferenceTarget()) {
            return MediaFileSelector.SelectionStatus.BASE_REF_TARGET;
        }
        if (candidate != null && candidate.isUnsupported()) {
            return MediaFileSelector.SelectionStatus.BASE_UNSUPPORTED;
        }
        return MediaFileSelector.SelectionStatus.BASE_FILE;
    }

    static MediaFileSelector.SelectionStatus imageDownloadFileStatus(ImageDownloadResolution resolution) {
        if (resolution == null || resolution.status() == null) {
            return MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE;
        }
        if (resolution.isImageInfoSource()) {
            return resolution.isReferenceTarget()
                    ? MediaFileSelector.SelectionStatus.IMAGE_INFO_REF_TARGET
                    : MediaFileSelector.SelectionStatus.IMAGE_INFO_FILE;
        }
        return resolution.isReferenceTarget()
                ? MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_REF_TARGET
                : MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE;
    }

    static boolean hasSelectableImageDownloadFile(ImageDownloadResolution resolution) {
        return resolution != null && resolution.hasSelectableFile();
    }

    static MediaFileSelector.SelectionStatus imageDownloadThumbnailStatus(ImageDownloadResolution resolution) {
        if (resolution == null || !resolution.isThumbnailOnly()) {
            return null;
        }
        return resolution.isImageInfoSource()
                ? MediaFileSelector.SelectionStatus.IMAGE_INFO_THUMBNAIL
                : MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_THUMBNAIL;
    }

    static MediaFileSelector.SelectionStatus imageDownloadUnsupportedStatus(ImageDownloadResolution resolution) {
        if (resolution == null || !resolution.isUnsupported()) {
            return null;
        }
        return resolution.isImageInfoSource()
                ? MediaFileSelector.SelectionStatus.IMAGE_INFO_UNSUPPORTED
                : MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_UNSUPPORTED;
    }
}
