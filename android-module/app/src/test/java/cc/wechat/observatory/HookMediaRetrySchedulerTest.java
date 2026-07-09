package cc.wechat.observatory;

import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cc.wechat.observatory.media.MediaRetryRuntime;
import cc.wechat.observatory.media.MediaUploadTracker;
import cc.wechat.observatory.media.OutgoingMediaSourceRegistry;
import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class HookMediaRetrySchedulerTest {
    @Test
    public void scheduleResolvesOutgoingLocalMediaHintBeforeRetryRuntime() throws Exception {
        File image = File.createTempFile("wxo-retry-outgoing-", ".jpg");
        image.deleteOnExit();
        HookMediaAttachmentController attachments = new HookMediaAttachmentController(
                null,
                new OutgoingMediaSourceRegistry());
        attachments.rememberOutgoingSource("room@chatroom", 3, image, 100L);
        CapturingEnvironment environment = new CapturingEnvironment();
        HookMediaRetryScheduler scheduler = new HookMediaRetryScheduler(
                new MediaRetryRuntime(new MediaUploadTracker(8), new long[]{0L}, environment),
                attachments);

        scheduler.scheduleIfNeeded(
                true,
                Long.valueOf(101L),
                null,
                "room@chatroom",
                "[图片]",
                Integer.valueOf(1),
                Long.valueOf(123456L),
                3,
                "thumb",
                new MessagePayload());

        assertTrue(environment.await());
        assertEquals(image.getAbsolutePath(), environment.mediaHint);
    }

    private static final class CapturingEnvironment implements MediaRetryRuntime.Environment {
        private final CountDownLatch latch = new CountDownLatch(1);
        String mediaHint;

        boolean await() throws InterruptedException {
            return latch.await(2L, TimeUnit.SECONDS);
        }

        @Override
        public MediaRetryRuntime.Attempt prepareAttempt() {
            return new MediaRetryRuntime.Attempt() {
                @Override
                public String resolveMediaHint(MediaRetryRuntime.Request request) {
                    return request.mediaHint();
                }

                @Override
                public MessagePayload buildPayload(MediaRetryRuntime.Request request, String mediaHint) {
                    CapturingEnvironment.this.mediaHint = mediaHint;
                    MessagePayload payload = new MessagePayload();
                    payload.chatRecordId = request.recordId();
                    payload.mediaBase64 = "encoded";
                    payload.mediaSize = 1;
                    return payload;
                }

                @Override
                public void post(MessagePayload payload) {
                    latch.countDown();
                }
            };
        }

        @Override
        public void sleep(long millis) {
        }

        @Override
        public void log(String message) {
        }
    }
}
