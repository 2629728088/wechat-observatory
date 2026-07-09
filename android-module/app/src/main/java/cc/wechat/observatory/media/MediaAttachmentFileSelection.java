package cc.wechat.observatory.media;

import java.io.File;

final class MediaAttachmentFileSelection {
    static final class Result {
        private final MediaAttachmentProcessor.AttachmentStatus status;
        private final File file;
        private final MediaFileSelector.SelectionStatus selectionStatus;

        private Result(
                MediaAttachmentProcessor.AttachmentStatus status,
                File file,
                MediaFileSelector.SelectionStatus selectionStatus) {
            this.status = status;
            this.file = file;
            this.selectionStatus = selectionStatus;
        }

        static Result selected(File file, MediaFileSelector.SelectionStatus selectionStatus) {
            return new Result(MediaAttachmentProcessor.AttachmentStatus.ATTACHED, file, selectionStatus);
        }

        static Result skipped(
                MediaAttachmentProcessor.AttachmentStatus status,
                MediaFileSelector.SelectionStatus selectionStatus) {
            return new Result(status, null, selectionStatus);
        }

        MediaAttachmentProcessor.AttachmentStatus status() {
            return status;
        }

        File file() {
            return file;
        }

        MediaFileSelector.SelectionStatus selectionStatus() {
            return selectionStatus;
        }

        boolean hasFile() {
            return MediaFiles.isExistingFile(file);
        }
    }

    private final MediaFileSelector selector;
    private final MediaAttachmentProcessor.Logger logger;

    MediaAttachmentFileSelection(MediaFileSelector selector, MediaAttachmentProcessor.Logger logger) {
        this.selector = selector;
        this.logger = logger;
    }

    Result select(MediaAttachmentProcessor.Request request) {
        if (selector == null) {
            log(MediaAttachmentSkipLogLine.selectorMissing(request.type(), request.chatRecordId()));
            return Result.skipped(MediaAttachmentProcessor.AttachmentStatus.SELECTOR_MISSING, null);
        }
        MediaFileSelector.Selection selection = selector.selectDetailed(
                MediaAttachmentSelectionRequestMapper.from(request));
        File file = selection == null ? null : selection.file();
        MediaFileSelector.SelectionStatus selectionStatus = selection == null ? null : selection.status();
        if (selection == null || !selection.hasFile()) {
            log(MediaAttachmentSkipLogLine.mediaNotSelected(request.type(), request.chatRecordId(), selection));
            return Result.skipped(
                    MediaAttachmentProcessor.AttachmentStatus.MEDIA_NOT_SELECTED,
                    selectionStatus);
        }
        return Result.selected(file, selectionStatus);
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
