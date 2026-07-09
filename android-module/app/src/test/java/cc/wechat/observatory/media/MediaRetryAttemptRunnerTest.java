package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class MediaRetryAttemptRunnerTest {
    @Test
    public void runUploadsPayloadWhenAttemptProducesMedia() {
        TestEnvironment environment = new TestEnvironment();

        MediaRetryAttemptRunner.Result result = new MediaRetryAttemptRunner(environment).run(request());

        assertEquals(MediaRetryAttemptRunner.Status.UPLOADED, result.status());
        assertEquals(1, environment.posted.size());
        assertSame(environment.posted.get(0), result.payload());
    }

    @Test
    public void runReportsEmptyWhenAttemptProducesNoMedia() {
        TestEnvironment environment = new TestEnvironment();
        environment.mediaBase64 = "";

        MediaRetryAttemptRunner.Result result = new MediaRetryAttemptRunner(environment).run(request());

        assertEquals(MediaRetryAttemptRunner.Status.EMPTY, result.status());
        assertEquals(0, environment.posted.size());
    }

    @Test
    public void runStopsWhenNoAttemptCanBePrepared() {
        TestEnvironment environment = new TestEnvironment();
        environment.returnAttempt = false;

        MediaRetryAttemptRunner.Result result = new MediaRetryAttemptRunner(environment).run(request());

        assertEquals(MediaRetryAttemptRunner.Status.STOPPED, result.status());
        assertEquals("attempt unavailable", result.stopReason());
        assertEquals(0, environment.posted.size());
    }

    @Test
    public void runReportsFailureWhenAttemptThrows() {
        TestEnvironment environment = new TestEnvironment();
        IllegalStateException failure = new IllegalStateException("post-boom");
        environment.postFailure = failure;

        MediaRetryAttemptRunner.Result result = new MediaRetryAttemptRunner(environment).run(request());

        assertEquals(MediaRetryAttemptRunner.Status.FAILED, result.status());
        assertSame(failure, result.failure());
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

    private static final class TestEnvironment implements MediaRetryRuntime.Environment {
        final List<MessagePayload> posted = new ArrayList<>();
        boolean returnAttempt = true;
        String mediaBase64 = "encoded";
        RuntimeException postFailure;

        @Override
        public MediaRetryRuntime.Attempt prepareAttempt() {
            if (!returnAttempt) {
                return null;
            }
            return new MediaRetryRuntime.Attempt() {
                @Override
                public String resolveMediaHint(MediaRetryRuntime.Request request) {
                    return "latest-hint";
                }

                @Override
                public MessagePayload buildPayload(MediaRetryRuntime.Request request, String mediaHint) {
                    MessagePayload payload = new MessagePayload();
                    payload.chatRecordId = request.recordId();
                    payload.mediaBase64 = mediaBase64;
                    payload.mediaSize = 512;
                    return payload;
                }

                @Override
                public void post(MessagePayload payload) {
                    if (postFailure != null) {
                        throw postFailure;
                    }
                    posted.add(payload);
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
