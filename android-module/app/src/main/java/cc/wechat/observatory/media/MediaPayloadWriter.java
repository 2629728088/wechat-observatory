package cc.wechat.observatory.media;

import java.io.File;

import cc.wechat.observatory.model.MessagePayload;

import static cc.wechat.observatory.util.Strings.shortError;

public final class MediaPayloadWriter {
    public interface Encoder {
        String encode(byte[] bytes);
    }

    public interface Logger {
        void log(String message);
    }

    public enum Status {
        WRITTEN,
        INVALID_INPUT,
        FILE_SIZE_OUT_OF_RANGE,
        ENCODED_EMPTY,
        READ_FAILED
    }

    public static final class Result {
        private final Status status;
        private final boolean written;

        private Result(Status status, boolean written) {
            this.status = status;
            this.written = written;
        }

        public static Result written() {
            return new Result(Status.WRITTEN, true);
        }

        public static Result skipped(Status status) {
            return new Result(status, false);
        }

        public Status status() {
            return status;
        }

        public boolean isWritten() {
            return written;
        }
    }

    private MediaPayloadWriter() {
    }

    public static boolean write(
            MessagePayload payload,
            int type,
            File file,
            long limit,
            Encoder encoder,
            Logger logger) {
        return writeDetailed(payload, type, file, limit, encoder, logger).isWritten();
    }

    public static Result writeDetailed(
            MessagePayload payload,
            int type,
            File file,
            long limit,
            Encoder encoder,
            Logger logger) {
        if (payload == null || !MediaFiles.isExistingFile(file)) {
            return Result.skipped(Status.INVALID_INPUT);
        }
        long length = file.length();
        if (length <= 0L || length > limit) {
            log(logger, "skip media upload type=" + type
                    + " size=" + length
                    + " file=" + file.getName());
            return Result.skipped(Status.FILE_SIZE_OUT_OF_RANGE);
        }
        try {
            MediaAttachment attachment = MediaAttachment.fromFile(file, type, payload.id, limit);
            MediaPayloadWritePlan writePlan = MediaPayloadWritePlan.from(attachment, encoder);
            if (writePlan == null) {
                return Result.skipped(Status.ENCODED_EMPTY);
            }
            writePlan.applyTo(payload);
            return Result.written();
        } catch (Throwable t) {
            log(logger, "read media failed type=" + type + " error=" + shortError(t));
            return Result.skipped(Status.READ_FAILED);
        }
    }

    private static void log(Logger logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
