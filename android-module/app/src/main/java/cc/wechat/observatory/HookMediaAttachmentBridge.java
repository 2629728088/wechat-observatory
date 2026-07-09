package cc.wechat.observatory;

import cc.wechat.observatory.media.MediaAttachmentLogLine;
import cc.wechat.observatory.media.MediaAttachmentProcessor;
import cc.wechat.observatory.media.MediaAttachmentRequestFactory;
import cc.wechat.observatory.media.MediaFiles;
import cc.wechat.observatory.model.MessagePayload;
import cc.wechat.observatory.wechat.WechatMediaRuntime;

final class HookMediaAttachmentBridge {
    interface Runtime {
        MediaAttachmentProcessor.Result attachDetailed(MediaAttachmentProcessor.Request request);

        String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint);
    }

    interface RuntimeProvider {
        Runtime runtime(Object database);
    }

    interface Logger {
        void log(String message);
    }

    private final RuntimeProvider runtimeProvider;
    private final Logger logger;

    HookMediaAttachmentBridge(RuntimeProvider runtimeProvider, Logger logger) {
        this.runtimeProvider = runtimeProvider;
        this.logger = logger;
    }

    static Runtime fromWechatRuntime(final WechatMediaRuntime runtime) {
        if (runtime == null) {
            return null;
        }
        return new Runtime() {
            @Override
            public MediaAttachmentProcessor.Result attachDetailed(MediaAttachmentProcessor.Request request) {
                return runtime.attachDetailed(request);
            }

            @Override
            public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
                return runtime.resolveMediaHint(type, msgId, msgSvrId, mediaHint);
            }
        };
    }

    void attachMedia(
            Object database,
            MessagePayload payload,
            int type,
            String mediaHint,
            Long msgId,
            Long msgSvrId,
            long createTime,
            String talker,
            String content,
            boolean mediaUploadEnabled,
            long mediaUploadLimitBytes) {
        MediaAttachmentProcessor.Result result;
        Runtime runtime = runtime(database);
        if (runtime == null) {
            result = MediaAttachmentProcessor.Result.skipped(
                    MediaAttachmentProcessor.AttachmentStatus.RUNTIME_UNAVAILABLE,
                    MediaFiles.kindForMessageType(type),
                    null,
                    null);
        } else {
            result = runtime.attachDetailed(MediaAttachmentRequestFactory.from(
                    payload,
                    type,
                    mediaHint,
                    msgId,
                    msgSvrId,
                    createTime,
                    talker,
                    content,
                    mediaUploadEnabled,
                    mediaUploadLimitBytes));
        }
        logMediaAttachmentResult(type, payload == null ? 0L : payload.chatRecordId, result);
    }

    String resolveMediaHint(Object database, int type, Long msgId, Long msgSvrId, String mediaHint) {
        Runtime runtime = runtime(database);
        return runtime == null ? mediaHint : runtime.resolveMediaHint(type, msgId, msgSvrId, mediaHint);
    }

    private Runtime runtime(Object database) {
        return runtimeProvider == null ? null : runtimeProvider.runtime(database);
    }

    private void logMediaAttachmentResult(int type, long chatRecordId, MediaAttachmentProcessor.Result result) {
        if (MediaAttachmentLogLine.shouldLog(result) && logger != null) {
            logger.log(MediaAttachmentLogLine.format(type, chatRecordId, result));
        }
    }
}
