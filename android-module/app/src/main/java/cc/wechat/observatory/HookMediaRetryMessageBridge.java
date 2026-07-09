package cc.wechat.observatory;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.media.MediaRetryRuntime;
import cc.wechat.observatory.model.MessagePayload;

final class HookMediaRetryMessageBridge implements HookMediaRetryEnvironment.MessageBridge {
    interface MediaHintResolver {
        String resolve(Object database, int type, Long msgId, Long msgSvrId, String mediaHint);
    }

    interface PayloadBuilder {
        MessagePayload build(
                BridgeConfig config,
                String talker,
                String content,
                Integer isSend,
                Long msgId,
                Long msgSvrId,
                Long createTime,
                int type,
                String mediaHint);
    }

    interface Poster {
        void post(BridgeConfig config, MessagePayload payload) throws Exception;
    }

    private final MediaHintResolver mediaHintResolver;
    private final PayloadBuilder payloadBuilder;
    private final Poster poster;

    HookMediaRetryMessageBridge(
            MediaHintResolver mediaHintResolver,
            PayloadBuilder payloadBuilder,
            Poster poster) {
        this.mediaHintResolver = mediaHintResolver;
        this.payloadBuilder = payloadBuilder;
        this.poster = poster;
    }

    @Override
    public String resolveMediaHint(Object database, MediaRetryRuntime.Request request) {
        if (request == null || mediaHintResolver == null) {
            return request == null ? "" : request.mediaHint();
        }
        return mediaHintResolver.resolve(
                database,
                request.type(),
                Long.valueOf(request.recordId()),
                request.msgSvrId(),
                request.mediaHint());
    }

    @Override
    public MessagePayload buildPayload(
            BridgeConfig config,
            MediaRetryRuntime.Request request,
            String mediaHint) {
        if (request == null || payloadBuilder == null) {
            return null;
        }
        return payloadBuilder.build(
                config,
                request.talker(),
                request.content(),
                request.isSend(),
                Long.valueOf(request.recordId()),
                request.msgSvrId(),
                request.createTime(),
                request.type(),
                mediaHint);
    }

    @Override
    public void post(BridgeConfig config, MessagePayload payload) throws Exception {
        if (poster != null) {
            poster.post(config, payload);
        }
    }
}
