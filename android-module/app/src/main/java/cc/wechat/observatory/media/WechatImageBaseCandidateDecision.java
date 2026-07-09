package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageBaseCandidateDecision {
    private final File file;
    private final MediaFileSelector.SelectionStatus status;
    private final boolean thumbnail;
    private final boolean unsupported;

    private WechatImageBaseCandidateDecision(
            File file,
            MediaFileSelector.SelectionStatus status,
            boolean thumbnail,
            boolean unsupported) {
        this.file = file;
        this.status = status;
        this.thumbnail = thumbnail;
        this.unsupported = unsupported;
    }

    static WechatImageBaseCandidateDecision from(ImageDownloadResolution.Candidate candidate) {
        File file = MediaSelectionStatusMapper.candidateFile(candidate);
        if (!MediaFiles.isExistingFile(file)) {
            return new WechatImageBaseCandidateDecision(null, null, false, false);
        }
        if (MediaSelectionStatusMapper.isThumbnailCandidate(candidate)) {
            return new WechatImageBaseCandidateDecision(file, null, true, false);
        }
        if (MediaSelectionStatusMapper.isUnsupportedCandidate(candidate)) {
            return new WechatImageBaseCandidateDecision(file, null, false, true);
        }
        return new WechatImageBaseCandidateDecision(
                file,
                MediaSelectionStatusMapper.baseStatus(candidate),
                false,
                false);
    }

    boolean hasUsableFile() {
        return MediaFiles.isExistingFile(file) && status != null;
    }

    File file() {
        return file;
    }

    MediaFileSelector.SelectionStatus status() {
        return status;
    }

    boolean isThumbnail() {
        return thumbnail;
    }

    boolean isUnsupported() {
        return unsupported;
    }

    MediaFileSelector.Selection selection() {
        return hasUsableFile() ? MediaFileSelector.Selection.of(status, file) : null;
    }

    MediaFileSelector.Selection unsupportedFallbackSelection() {
        return unsupported
                ? MediaFileSelector.Selection.of(MediaFileSelector.SelectionStatus.BASE_UNSUPPORTED, null)
                : null;
    }
}
