package cc.wechat.observatory.media;

import static cc.wechat.observatory.util.Strings.isBlank;

final class MediaAttachmentPreflight {
    static final class Result {
        private final boolean ready;
        private final MediaAttachmentProcessor.AttachmentStatus status;
        private final String mediaKind;

        private Result(
                boolean ready,
                MediaAttachmentProcessor.AttachmentStatus status,
                String mediaKind) {
            this.ready = ready;
            this.status = status;
            this.mediaKind = mediaKind;
        }

        static Result ready(String mediaKind) {
            return new Result(true, MediaAttachmentProcessor.AttachmentStatus.ATTACHED, mediaKind);
        }

        static Result skipped(MediaAttachmentProcessor.AttachmentStatus status, String mediaKind) {
            return new Result(false, status, mediaKind);
        }

        boolean ready() {
            return ready;
        }

        MediaAttachmentProcessor.AttachmentStatus status() {
            return status;
        }

        String mediaKind() {
            return mediaKind;
        }
    }

    private MediaAttachmentPreflight() {
    }

    static Result check(MediaAttachmentProcessor.Request request) {
        if (request == null || request.payload() == null) {
            return Result.skipped(MediaAttachmentProcessor.AttachmentStatus.INVALID_REQUEST, "");
        }
        String kind = MediaFiles.kindForMessageType(request.type());
        if (isBlank(kind)) {
            return Result.skipped(MediaAttachmentProcessor.AttachmentStatus.UNSUPPORTED_TYPE, "");
        }
        request.payload().mediaKind = kind;
        if (!request.mediaUploadEnabled()) {
            return Result.skipped(MediaAttachmentProcessor.AttachmentStatus.UPLOAD_DISABLED, kind);
        }
        return Result.ready(kind);
    }
}
