package cc.wechat.observatory.media;

import cc.wechat.observatory.model.MessagePayload;

import static cc.wechat.observatory.util.Strings.isBlank;

final class MediaRetryAttemptRunner {
    enum Status {
        UPLOADED,
        EMPTY,
        STOPPED,
        FAILED
    }

    static final class Result {
        private final Status status;
        private final MessagePayload payload;
        private final Throwable failure;
        private final String stopReason;

        private Result(Status status, MessagePayload payload, Throwable failure, String stopReason) {
            this.status = status;
            this.payload = payload;
            this.failure = failure;
            this.stopReason = stopReason == null ? "" : stopReason;
        }

        static Result uploaded(MessagePayload payload) {
            return new Result(Status.UPLOADED, payload, null, "");
        }

        static Result empty() {
            return new Result(Status.EMPTY, null, null, "");
        }

        static Result stopped(String reason) {
            return new Result(Status.STOPPED, null, null, reason);
        }

        static Result failed(Throwable failure) {
            return new Result(Status.FAILED, null, failure, "");
        }

        Status status() {
            return status;
        }

        MessagePayload payload() {
            return payload;
        }

        Throwable failure() {
            return failure;
        }

        String stopReason() {
            return stopReason;
        }
    }

    private final MediaRetryRuntime.Environment environment;

    MediaRetryAttemptRunner(MediaRetryRuntime.Environment environment) {
        this.environment = environment;
    }

    Result run(MediaRetryRuntime.Request request) {
        if (request == null || environment == null) {
            return Result.stopped("runtime unavailable");
        }
        try {
            MediaRetryRuntime.Attempt attempt = environment.prepareAttempt();
            if (attempt == null) {
                return Result.stopped("attempt unavailable");
            }
            String latestHint = attempt.resolveMediaHint(request);
            MessagePayload payload = attempt.buildPayload(request, latestHint);
            if (payload == null || isBlank(payload.mediaBase64)) {
                return Result.empty();
            }
            attempt.post(payload);
            return Result.uploaded(payload);
        } catch (Throwable t) {
            return Result.failed(t);
        }
    }
}
