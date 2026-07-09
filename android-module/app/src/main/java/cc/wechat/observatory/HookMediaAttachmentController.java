package cc.wechat.observatory;

import java.io.File;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.media.OutgoingMediaSourceRegistry;
import cc.wechat.observatory.model.MessagePayload;

final class HookMediaAttachmentController {
    private final HookMediaAttachmentBridge bridge;
    private final OutgoingMediaSourceRegistry outgoingMediaSources;

    HookMediaAttachmentController(HookMediaAttachmentBridge bridge) {
        this(bridge, new OutgoingMediaSourceRegistry());
    }

    HookMediaAttachmentController(HookMediaAttachmentBridge bridge, OutgoingMediaSourceRegistry outgoingMediaSources) {
        this.bridge = bridge;
        this.outgoingMediaSources = outgoingMediaSources;
    }

    void attachMedia(
            Object database,
            BridgeConfig config,
            MessagePayload payload,
            Integer isSend,
            int type,
            String mediaHint,
            Long msgId,
            Long msgSvrId,
            long createTime,
            String talker,
            String content) {
        if (bridge == null) {
            return;
        }
        String effectiveMediaHint = resolveEffectiveMediaHint(
                isSend,
                type,
                talker,
                msgId,
                msgSvrId,
                mediaHint);
        bridge.attachMedia(
                database,
                payload,
                type,
                effectiveMediaHint,
                msgId,
                msgSvrId,
                createTime,
                talker,
                content,
                config != null && config.mediaUploadEnabled,
                config == null ? 0L : config.mediaUploadLimitBytes);
    }

    String resolveMediaHint(Object database, int type, Long msgId, Long msgSvrId, String mediaHint) {
        return bridge == null ? mediaHint : bridge.resolveMediaHint(database, type, msgId, msgSvrId, mediaHint);
    }

    void rememberOutgoingSource(String talker, int type, File file, long afterMsgId) {
        if (outgoingMediaSources != null) {
            outgoingMediaSources.rememberPending(talker, type, file, afterMsgId);
        }
    }

    void bindOutgoingSource(String talker, int type, long msgId, File file) {
        if (outgoingMediaSources != null) {
            outgoingMediaSources.bind(talker, type, msgId, file);
        }
    }

    String resolveEffectiveMediaHint(
            Integer isSend,
            int type,
            String talker,
            Long msgId,
            Long msgSvrId,
            String mediaHint) {
        return outgoingMediaSources == null
                ? mediaHint
                : outgoingMediaSources.resolveHint(isSend, type, talker, msgId, msgSvrId, mediaHint);
    }
}
