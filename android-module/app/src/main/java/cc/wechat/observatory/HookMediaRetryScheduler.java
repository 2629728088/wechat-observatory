package cc.wechat.observatory;

import cc.wechat.observatory.media.MediaRetryRuntime;
import cc.wechat.observatory.model.MessagePayload;

final class HookMediaRetryScheduler {
    private final MediaRetryRuntime runtime;
    private final HookMediaAttachmentController mediaAttachmentController;

    HookMediaRetryScheduler(MediaRetryRuntime runtime) {
        this(runtime, null);
    }

    HookMediaRetryScheduler(MediaRetryRuntime runtime, HookMediaAttachmentController mediaAttachmentController) {
        this.runtime = runtime;
        this.mediaAttachmentController = mediaAttachmentController;
    }

    void rememberUploaded(MessagePayload payload) {
        if (runtime != null) {
            runtime.rememberUploaded(payload);
        }
    }

    void scheduleIfNeeded(
            boolean mediaUploadEnabled,
            Long msgId,
            Long msgSvrId,
            String talker,
            String content,
            Integer isSend,
            Long createTime,
            int type,
            String mediaHint,
            MessagePayload firstPayload) {
        long recordId = msgId == null ? 0L : msgId.longValue();
        if (recordId <= 0L || runtime == null) {
            return;
        }
        String effectiveMediaHint = mediaAttachmentController == null
                ? mediaHint
                : mediaAttachmentController.resolveEffectiveMediaHint(
                        isSend,
                        type,
                        talker,
                        msgId,
                        msgSvrId,
                        mediaHint);
        runtime.scheduleIfNeeded(
                new MediaRetryRuntime.Request(
                        mediaUploadEnabled,
                        recordId,
                        msgSvrId,
                        talker,
                        content,
                        isSend,
                        createTime,
                        type,
                        effectiveMediaHint),
                firstPayload);
    }
}
