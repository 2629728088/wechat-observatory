package cc.wechat.observatory.media;

import cc.wechat.observatory.model.MessagePayload;

public final class MediaAttachmentProcessor {
    public interface Encoder {
        String encode(byte[] bytes);
    }

    public interface Logger {
        void log(String message);
    }

    public enum AttachmentStatus {
        ATTACHED,
        INVALID_REQUEST,
        UNSUPPORTED_TYPE,
        UPLOAD_DISABLED,
        RUNTIME_UNAVAILABLE,
        SELECTOR_MISSING,
        MEDIA_NOT_SELECTED,
        WRITE_FAILED
    }

    public static final class Result {
        private final AttachmentStatus status;
        private final boolean attached;
        private final String mediaKind;
        private final MediaFileSelector.SelectionStatus selectionStatus;
        private final MediaPayloadWriter.Status writerStatus;

        private Result(
                AttachmentStatus status,
                boolean attached,
                String mediaKind,
                MediaFileSelector.SelectionStatus selectionStatus,
                MediaPayloadWriter.Status writerStatus) {
            this.status = status;
            this.attached = attached;
            this.mediaKind = mediaKind;
            this.selectionStatus = selectionStatus;
            this.writerStatus = writerStatus;
        }

        public static Result attached(String mediaKind, MediaFileSelector.SelectionStatus selectionStatus) {
            return new Result(AttachmentStatus.ATTACHED, true, mediaKind, selectionStatus, null);
        }

        public static Result skipped(
                AttachmentStatus status,
                String mediaKind,
                MediaFileSelector.SelectionStatus selectionStatus,
                MediaPayloadWriter.Status writerStatus) {
            return new Result(status, false, mediaKind, selectionStatus, writerStatus);
        }

        public AttachmentStatus status() {
            return status;
        }

        public boolean isAttached() {
            return attached;
        }

        public String mediaKind() {
            return mediaKind;
        }

        public MediaFileSelector.SelectionStatus selectionStatus() {
            return selectionStatus;
        }

        public MediaPayloadWriter.Status writerStatus() {
            return writerStatus;
        }
    }

    public static final class Request {
        private final MessagePayload payload;
        private final int type;
        private final String mediaHint;
        private final Long msgId;
        private final Long msgSvrId;
        private final long createTime;
        private final String talker;
        private final String content;
        private final String emojiMd5;
        private final long chatRecordId;
        private final boolean mediaUploadEnabled;
        private final long mediaUploadLimitBytes;

        public Request(
                MessagePayload payload,
                int type,
                String mediaHint,
                Long msgId,
                Long msgSvrId,
                long createTime,
                String talker,
                String content,
                String emojiMd5,
                long chatRecordId,
                boolean mediaUploadEnabled,
                long mediaUploadLimitBytes) {
            this.payload = payload;
            this.type = type;
            this.mediaHint = mediaHint;
            this.msgId = msgId;
            this.msgSvrId = msgSvrId;
            this.createTime = createTime;
            this.talker = talker;
            this.content = content;
            this.emojiMd5 = emojiMd5;
            this.chatRecordId = chatRecordId;
            this.mediaUploadEnabled = mediaUploadEnabled;
            this.mediaUploadLimitBytes = mediaUploadLimitBytes;
        }

        public MessagePayload payload() {
            return payload;
        }

        public int type() {
            return type;
        }

        public String mediaHint() {
            return mediaHint;
        }

        public Long msgId() {
            return msgId;
        }

        public Long msgSvrId() {
            return msgSvrId;
        }

        public long createTime() {
            return createTime;
        }

        public String talker() {
            return talker;
        }

        public String content() {
            return content;
        }

        public String emojiMd5() {
            return emojiMd5;
        }

        public long chatRecordId() {
            return chatRecordId;
        }

        public boolean mediaUploadEnabled() {
            return mediaUploadEnabled;
        }

        public long mediaUploadLimitBytes() {
            return mediaUploadLimitBytes;
        }
    }

    private final MediaAttachmentPayloadWriter payloadWriter;
    private final MediaAttachmentFileSelection fileSelection;
    private final Logger logger;

    public MediaAttachmentProcessor(MediaFileSelector selector, Encoder encoder, Logger logger) {
        this.payloadWriter = MediaAttachmentPayloadWriter.from(encoder, logger);
        this.fileSelection = new MediaAttachmentFileSelection(selector, logger);
        this.logger = logger;
    }

    public boolean attach(Request request) {
        return attachDetailed(request).isAttached();
    }

    public Result attachDetailed(Request request) {
        MediaAttachmentPreflight.Result preflight = MediaAttachmentPreflight.check(request);
        if (!preflight.ready()) {
            return Result.skipped(preflight.status(), preflight.mediaKind(), null, null);
        }
        MediaAttachmentFileSelection.Result selection = fileSelection.select(request);
        if (!selection.hasFile()) {
            return Result.skipped(
                    selection.status(),
                    preflight.mediaKind(),
                    selection.selectionStatus(),
                    null);
        }
        MediaPayloadWriter.Result result = payloadWriter.write(
                request.payload(),
                request.type(),
                selection.file(),
                request.mediaUploadLimitBytes());
        if (!result.isWritten()) {
            log(MediaAttachmentSkipLogLine.writeFailed(
                    request.type(),
                    request.chatRecordId(),
                    selection.selectionStatus(),
                    result.status()));
            return Result.skipped(
                    AttachmentStatus.WRITE_FAILED,
                    preflight.mediaKind(),
                    selection.selectionStatus(),
                    result.status());
        }
        return Result.attached(preflight.mediaKind(), selection.selectionStatus());
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
