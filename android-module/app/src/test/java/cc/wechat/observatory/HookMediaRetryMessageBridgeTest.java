package cc.wechat.observatory;

import org.junit.Test;

import java.lang.reflect.Constructor;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.media.MediaRetryRuntime;
import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class HookMediaRetryMessageBridgeTest {
    @Test
    public void resolveMediaHintMapsRetryRequestToResolverArguments() {
        Object database = new Object();
        CapturingHintResolver resolver = new CapturingHintResolver();
        HookMediaRetryMessageBridge bridge = new HookMediaRetryMessageBridge(resolver, null, null);
        MediaRetryRuntime.Request request = request();

        String hint = bridge.resolveMediaHint(database, request);

        assertEquals("resolved-hint", hint);
        assertSame(database, resolver.database);
        assertEquals(3, resolver.type);
        assertEquals(Long.valueOf(100L), resolver.msgId);
        assertEquals(Long.valueOf(200L), resolver.msgSvrId);
        assertEquals("fallback-hint", resolver.mediaHint);
    }

    @Test
    public void buildPayloadMapsRetryRequestToPayloadBuilderArguments() throws Exception {
        BridgeConfig config = config();
        CapturingPayloadBuilder builder = new CapturingPayloadBuilder();
        HookMediaRetryMessageBridge bridge = new HookMediaRetryMessageBridge(null, builder, null);
        MediaRetryRuntime.Request request = request();

        MessagePayload payload = bridge.buildPayload(config, request, "resolved-hint");

        assertEquals(100L, payload.chatRecordId);
        assertSame(config, builder.config);
        assertEquals("talker", builder.talker);
        assertEquals("content", builder.content);
        assertEquals(Integer.valueOf(0), builder.isSend);
        assertEquals(Long.valueOf(100L), builder.msgId);
        assertEquals(Long.valueOf(200L), builder.msgSvrId);
        assertEquals(Long.valueOf(123456L), builder.createTime);
        assertEquals(3, builder.type);
        assertEquals("resolved-hint", builder.mediaHint);
    }

    @Test
    public void postDelegatesToPoster() throws Exception {
        BridgeConfig config = config();
        MessagePayload payload = new MessagePayload();
        CapturingPoster poster = new CapturingPoster();
        HookMediaRetryMessageBridge bridge = new HookMediaRetryMessageBridge(null, null, poster);

        bridge.post(config, payload);

        assertSame(config, poster.config);
        assertSame(payload, poster.payload);
    }

    @Test
    public void missingCollaboratorsReturnSafeFallbacks() throws Exception {
        HookMediaRetryMessageBridge bridge = new HookMediaRetryMessageBridge(null, null, null);

        assertEquals("fallback-hint", bridge.resolveMediaHint(new Object(), request()));
        assertEquals("", bridge.resolveMediaHint(new Object(), null));
        assertNull(bridge.buildPayload(config(), request(), "hint"));
        bridge.post(config(), new MessagePayload());
    }

    private static BridgeConfig config() throws Exception {
        Constructor<BridgeConfig> constructor = BridgeConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static MediaRetryRuntime.Request request() {
        return new MediaRetryRuntime.Request(
                true,
                100L,
                Long.valueOf(200L),
                "talker",
                "content",
                Integer.valueOf(0),
                Long.valueOf(123456L),
                3,
                "fallback-hint");
    }

    private static final class CapturingHintResolver implements HookMediaRetryMessageBridge.MediaHintResolver {
        Object database;
        int type;
        Long msgId;
        Long msgSvrId;
        String mediaHint;

        @Override
        public String resolve(Object database, int type, Long msgId, Long msgSvrId, String mediaHint) {
            this.database = database;
            this.type = type;
            this.msgId = msgId;
            this.msgSvrId = msgSvrId;
            this.mediaHint = mediaHint;
            return "resolved-hint";
        }
    }

    private static final class CapturingPayloadBuilder implements HookMediaRetryMessageBridge.PayloadBuilder {
        BridgeConfig config;
        String talker;
        String content;
        Integer isSend;
        Long msgId;
        Long msgSvrId;
        Long createTime;
        int type;
        String mediaHint;

        @Override
        public MessagePayload build(
                BridgeConfig config,
                String talker,
                String content,
                Integer isSend,
                Long msgId,
                Long msgSvrId,
                Long createTime,
                int type,
                String mediaHint) {
            this.config = config;
            this.talker = talker;
            this.content = content;
            this.isSend = isSend;
            this.msgId = msgId;
            this.msgSvrId = msgSvrId;
            this.createTime = createTime;
            this.type = type;
            this.mediaHint = mediaHint;
            MessagePayload payload = new MessagePayload();
            payload.chatRecordId = msgId == null ? 0L : msgId.longValue();
            return payload;
        }
    }

    private static final class CapturingPoster implements HookMediaRetryMessageBridge.Poster {
        BridgeConfig config;
        MessagePayload payload;

        @Override
        public void post(BridgeConfig config, MessagePayload payload) {
            this.config = config;
            this.payload = payload;
        }
    }
}
