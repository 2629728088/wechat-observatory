package cc.wechat.observatory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import cc.wechat.observatory.media.MediaAttachmentProcessor;
import cc.wechat.observatory.media.MediaFileSelector;
import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class HookMediaAttachmentBridgeTest {
    @Test
    public void attachMediaBuildsRequestAndLogsResult() {
        Object database = new Object();
        MessagePayload payload = new MessagePayload();
        payload.chatRecordId = 42L;
        CapturingRuntime runtime = new CapturingRuntime(
                MediaAttachmentProcessor.Result.attached("image", MediaFileSelector.SelectionStatus.BASE_FILE));
        CapturingLogger logger = new CapturingLogger();
        HookMediaAttachmentBridge bridge = new HookMediaAttachmentBridge(
                new SingleRuntimeProvider(database, runtime),
                logger);

        bridge.attachMedia(
                database,
                payload,
                3,
                "hint.jpg",
                Long.valueOf(10L),
                Long.valueOf(20L),
                123456L,
                "talker",
                "content",
                true,
                4096L);

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
        assertEquals(1, logger.messages.size());
        assertEquals("media attachment result type=3 msgId=42 status=ATTACHED kind=image selectionStatus=BASE_FILE",
                logger.messages.get(0));
    }

    @Test
    public void resolveMediaHintDelegatesToRuntime() {
        CapturingRuntime runtime = new CapturingRuntime(
                MediaAttachmentProcessor.Result.skipped(
                        MediaAttachmentProcessor.AttachmentStatus.UNSUPPORTED_TYPE,
                        "",
                        null,
                        null));
        runtime.resolvedHint = "resolved.jpg";
        Object database = new Object();
        HookMediaAttachmentBridge bridge = new HookMediaAttachmentBridge(
                new SingleRuntimeProvider(database, runtime),
                null);

        String hint = bridge.resolveMediaHint(database, 3, Long.valueOf(1L), Long.valueOf(2L), "fallback.jpg");

        assertEquals("resolved.jpg", hint);
        assertEquals(3, runtime.hintType);
        assertEquals(Long.valueOf(1L), runtime.hintMsgId);
        assertEquals(Long.valueOf(2L), runtime.hintMsgSvrId);
        assertEquals("fallback.jpg", runtime.hintFallback);
    }

    @Test
    public void missingRuntimeKeepsFallbackHintAndLogsUnavailableAttachment() {
        MessagePayload payload = new MessagePayload();
        payload.chatRecordId = 7L;
        CapturingLogger logger = new CapturingLogger();
        HookMediaAttachmentBridge bridge = new HookMediaAttachmentBridge(
                new HookMediaAttachmentBridge.RuntimeProvider() {
                    @Override
                    public HookMediaAttachmentBridge.Runtime runtime(Object database) {
                        return null;
                    }
                },
                logger);

        bridge.attachMedia(new Object(), payload, 3, "", null, null, 0L, "", "", true, 1024L);
        String hint = bridge.resolveMediaHint(new Object(), 3, null, null, "fallback.jpg");

        assertEquals("fallback.jpg", hint);
        assertEquals(1, logger.messages.size());
        assertEquals("media attachment result type=3 msgId=7 status=RUNTIME_UNAVAILABLE kind=image",
                logger.messages.get(0));
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
        private final MediaAttachmentProcessor.Result result;
        MediaAttachmentProcessor.Request request;
        String resolvedHint = "";
        int hintType;
        Long hintMsgId;
        Long hintMsgSvrId;
        String hintFallback;

        CapturingRuntime(MediaAttachmentProcessor.Result result) {
            this.result = result;
        }

        @Override
        public MediaAttachmentProcessor.Result attachDetailed(MediaAttachmentProcessor.Request request) {
            this.request = request;
            return result;
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

    private static final class CapturingLogger implements HookMediaAttachmentBridge.Logger {
        final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}
