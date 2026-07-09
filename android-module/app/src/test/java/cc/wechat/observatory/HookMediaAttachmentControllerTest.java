package cc.wechat.observatory;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.media.MediaAttachmentProcessor;
import cc.wechat.observatory.media.MediaFileSelector;
import cc.wechat.observatory.media.OutgoingMediaSourceRegistry;
import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class HookMediaAttachmentControllerTest {
    @Test
    public void attachMediaDelegatesWithConfigUploadSettings() throws Exception {
        Object database = new Object();
        BridgeConfig config = config();
        config.mediaUploadEnabled = true;
        config.mediaUploadLimitBytes = 4096L;
        MessagePayload payload = new MessagePayload();
        CapturingRuntime runtime = new CapturingRuntime();
        HookMediaAttachmentController controller = new HookMediaAttachmentController(
                new HookMediaAttachmentBridge(new SingleRuntimeProvider(database, runtime), null));

        controller.attachMedia(
                database,
                config,
                payload,
                Integer.valueOf(0),
                3,
                "hint.jpg",
                Long.valueOf(10L),
                Long.valueOf(20L),
                123456L,
                "talker",
                "content");

        assertSame(payload, runtime.request.payload());
        assertEquals(3, runtime.request.type());
        assertEquals("hint.jpg", runtime.request.mediaHint());
        assertEquals(Long.valueOf(10L), runtime.request.msgId());
        assertEquals(Long.valueOf(20L), runtime.request.msgSvrId());
        assertEquals(123456L, runtime.request.createTime());
        assertEquals("talker", runtime.request.talker());
        assertEquals("content", runtime.request.content());
        assertTrue(runtime.request.mediaUploadEnabled());
        assertEquals(4096L, runtime.request.mediaUploadLimitBytes());
    }

    @Test
    public void attachMediaUsesOutgoingSourceForLocalSentMessage() throws Exception {
        Object database = new Object();
        BridgeConfig config = config();
        MessagePayload payload = new MessagePayload();
        File image = File.createTempFile("wxo-controller-attach-", ".jpg");
        image.deleteOnExit();
        CapturingRuntime runtime = new CapturingRuntime();
        HookMediaAttachmentController controller = new HookMediaAttachmentController(
                new HookMediaAttachmentBridge(new SingleRuntimeProvider(database, runtime), null),
                new OutgoingMediaSourceRegistry());

        controller.rememberOutgoingSource("room@chatroom", 3, image, 100L);
        controller.attachMedia(
                database,
                config,
                payload,
                Integer.valueOf(1),
                3,
                "thumb",
                Long.valueOf(101L),
                null,
                123456L,
                "room@chatroom",
                "[图片]");

        assertEquals(image.getAbsolutePath(), runtime.request.mediaHint());
    }

    @Test
    public void resolveMediaHintDelegatesToBridge() {
        Object database = new Object();
        CapturingRuntime runtime = new CapturingRuntime();
        runtime.resolvedHint = "resolved.jpg";
        HookMediaAttachmentController controller = new HookMediaAttachmentController(
                new HookMediaAttachmentBridge(new SingleRuntimeProvider(database, runtime), null));

        String hint = controller.resolveMediaHint(
                database,
                3,
                Long.valueOf(1L),
                Long.valueOf(2L),
                "fallback.jpg");

        assertEquals("resolved.jpg", hint);
        assertEquals(3, runtime.hintType);
        assertEquals(Long.valueOf(1L), runtime.hintMsgId);
        assertEquals(Long.valueOf(2L), runtime.hintMsgSvrId);
        assertEquals("fallback.jpg", runtime.hintFallback);
    }

    @Test
    public void outgoingSourceOverridesOnlyLocalSentMediaHint() throws Exception {
        File image = File.createTempFile("wxo-controller-outgoing-", ".jpg");
        image.deleteOnExit();
        HookMediaAttachmentController controller = new HookMediaAttachmentController(
                null,
                new OutgoingMediaSourceRegistry());

        controller.rememberOutgoingSource("room@chatroom", 3, image, 100L);

        assertEquals(image.getAbsolutePath(), controller.resolveEffectiveMediaHint(
                Integer.valueOf(1),
                3,
                "room@chatroom",
                Long.valueOf(101L),
                null,
                "thumb"));
        assertEquals("thumb", controller.resolveEffectiveMediaHint(
                Integer.valueOf(0),
                3,
                "room@chatroom",
                Long.valueOf(101L),
                null,
                "thumb"));
        assertEquals("thumb", controller.resolveEffectiveMediaHint(
                Integer.valueOf(1),
                3,
                "room@chatroom",
                Long.valueOf(101L),
                Long.valueOf(999L),
                "thumb"));
    }

    private static BridgeConfig config() throws Exception {
        Constructor<BridgeConfig> constructor = BridgeConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static final class SingleRuntimeProvider implements HookMediaAttachmentBridge.RuntimeProvider {
        private final Object expectedDatabase;
        private final CapturingRuntime runtime;

        SingleRuntimeProvider(Object expectedDatabase, CapturingRuntime runtime) {
            this.expectedDatabase = expectedDatabase;
            this.runtime = runtime;
        }

        @Override
        public HookMediaAttachmentBridge.Runtime runtime(Object database) {
            assertSame(expectedDatabase, database);
            return runtime;
        }
    }

    private static final class CapturingRuntime implements HookMediaAttachmentBridge.Runtime {
        MediaAttachmentProcessor.Request request;
        String resolvedHint = "";
        int hintType;
        Long hintMsgId;
        Long hintMsgSvrId;
        String hintFallback;

        @Override
        public MediaAttachmentProcessor.Result attachDetailed(MediaAttachmentProcessor.Request request) {
            this.request = request;
            return MediaAttachmentProcessor.Result.attached(
                    "image",
                    MediaFileSelector.SelectionStatus.BASE_FILE);
        }

        @Override
        public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
            this.hintType = type;
            this.hintMsgId = msgId;
            this.hintMsgSvrId = msgSvrId;
            this.hintFallback = mediaHint;
            return resolvedHint;
        }
    }
}
