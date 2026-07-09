package cc.wechat.observatory.media;

import cc.wechat.observatory.model.MessagePayload;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaRetryRuntime {
    public interface Environment {
        Attempt prepareAttempt() throws Exception;

        void sleep(long millis);

        void log(String message);
    }

    public interface Attempt {
        String resolveMediaHint(Request request);

        MessagePayload buildPayload(Request request, String mediaHint);

        void post(MessagePayload payload) throws Exception;
    }

    public interface WorkerStarter {
        void start(String name, Runnable runnable);
    }

    public static final class Request {
        private final boolean mediaUploadEnabled;
        private final long recordId;
        private final Long msgSvrId;
        private final String talker;
        private final String content;
        private final Integer isSend;
        private final Long createTime;
        private final int type;
        private final String mediaHint;

        public Request(
                boolean mediaUploadEnabled,
                long recordId,
                Long msgSvrId,
                String talker,
                String content,
                Integer isSend,
                Long createTime,
                int type,
                String mediaHint) {
            this.mediaUploadEnabled = mediaUploadEnabled;
            this.recordId = recordId;
            this.msgSvrId = msgSvrId;
            this.talker = talker;
            this.content = content;
            this.isSend = isSend;
            this.createTime = createTime;
            this.type = type;
            this.mediaHint = mediaHint;
        }

        public boolean mediaUploadEnabled() {
            return mediaUploadEnabled;
        }

        public long recordId() {
            return recordId;
        }

        public Long msgSvrId() {
            return msgSvrId;
        }

        public String talker() {
            return talker;
        }

        public String content() {
            return content;
        }

        public Integer isSend() {
            return isSend;
        }

        public Long createTime() {
            return createTime;
        }

        public int type() {
            return type;
        }

        public String mediaHint() {
            return mediaHint;
        }
    }

    private final MediaUploadTracker tracker;
    private final long[] retryDelaysMs;
    private final Environment environment;
    private final MediaRetryAttemptRunner attemptRunner;
    private final WorkerStarter workerStarter;
    private long lastFailureLogAt;

    public MediaRetryRuntime(MediaUploadTracker tracker, long[] retryDelaysMs, Environment environment) {
        this(tracker, retryDelaysMs, environment, defaultWorkerStarter());
    }

    MediaRetryRuntime(
            MediaUploadTracker tracker,
            long[] retryDelaysMs,
            Environment environment,
            WorkerStarter workerStarter) {
        this.tracker = tracker == null ? new MediaUploadTracker(1) : tracker;
        this.retryDelaysMs = retryDelaysMs == null ? new long[0] : retryDelaysMs.clone();
        this.environment = environment;
        this.attemptRunner = new MediaRetryAttemptRunner(environment);
        this.workerStarter = workerStarter == null ? defaultWorkerStarter() : workerStarter;
    }

    public boolean scheduleIfNeeded(Request request, MessagePayload firstPayload) {
        if (!shouldSchedule(request, firstPayload)) {
            return false;
        }
        if (!tracker.markRetryScheduled(request.recordId())) {
            return false;
        }
        if (environment != null) {
            environment.log(MediaRetryLogLine.scheduled(request.type(), request.recordId()));
        }
        workerStarter.start("WechatGatewayMediaRetry-" + request.recordId(), new Runnable() {
            @Override
            public void run() {
                retry(request);
            }
        });
        return true;
    }

    public void rememberUploaded(MessagePayload payload) {
        if (payload == null || payload.chatRecordId <= 0L || isBlank(payload.mediaBase64)) {
            return;
        }
        tracker.rememberUploaded(payload.chatRecordId);
    }

    public boolean hasUploaded(long chatRecordId) {
        return tracker.hasUploaded(chatRecordId);
    }

    void retry(Request request) {
        if (request == null || environment == null) {
            return;
        }
        for (int i = 0; i < retryDelaysMs.length; i++) {
            environment.sleep(retryDelaysMs[i]);
            if (tracker.hasUploaded(request.recordId())) {
                return;
            }
            MediaRetryAttemptRunner.Result result = attemptRunner.run(request);
            if (result.status() == MediaRetryAttemptRunner.Status.STOPPED) {
                environment.log(MediaRetryLogLine.stopped(
                        request.type(),
                        request.recordId(),
                        i + 1,
                        result.stopReason()));
                return;
            }
            if (result.status() == MediaRetryAttemptRunner.Status.EMPTY) {
                environment.log(MediaRetryLogLine.empty(request.type(), request.recordId(), i + 1));
                continue;
            }
            if (result.status() == MediaRetryAttemptRunner.Status.UPLOADED) {
                if (!finishUploaded(request, i + 1, result.payload())) {
                    continue;
                }
                return;
            }
            logFailure(request.recordId(), request.type(), i + 1, result.failure());
        }
        environment.log(MediaRetryLogLine.exhausted(request.type(), request.recordId()));
    }

    private boolean finishUploaded(MediaRetryRuntime.Request request, int attempt, MessagePayload payload) {
        try {
            rememberUploaded(payload);
            environment.log(MediaRetryLogLine.uploaded(
                    request.type(),
                    request.recordId(),
                    attempt,
                    payload.mediaSize));
            return true;
        } catch (Throwable t) {
            logFailure(request.recordId(), request.type(), attempt, t);
            return false;
        }
    }

    private boolean shouldSchedule(Request request, MessagePayload firstPayload) {
        if (request == null) {
            return false;
        }
        return MediaRetryPolicy.shouldSchedule(
                request.mediaUploadEnabled(),
                request.recordId(),
                request.type(),
                tracker.hasUploaded(request.recordId()),
                firstPayload == null ? "" : firstPayload.mediaBase64);
    }

    private void logFailure(long recordId, int type, int attempt, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLogAt < 30000L) {
            return;
        }
        lastFailureLogAt = now;
        if (environment != null) {
            environment.log(MediaRetryLogLine.failed(type, recordId, attempt, t));
        }
    }

    private static WorkerStarter defaultWorkerStarter() {
        return new WorkerStarter() {
            @Override
            public void start(String name, Runnable runnable) {
                Thread worker = new Thread(runnable, name);
                worker.setDaemon(true);
                worker.start();
            }
        };
    }
}
