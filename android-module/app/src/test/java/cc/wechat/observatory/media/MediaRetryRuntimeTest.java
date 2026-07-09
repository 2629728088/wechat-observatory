package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaRetryRuntimeTest {
    @Test
    public void scheduleRunsRetryAndRemembersUploadedPayload() {
        final TestEnvironment environment = new TestEnvironment();
        MediaRetryRuntime runtime = new MediaRetryRuntime(
                new MediaUploadTracker(8),
                new long[]{0L},
                environment,
                inlineWorker());

        boolean scheduled = runtime.scheduleIfNeeded(request(3), new MessagePayload());

        assertTrue(scheduled);
        assertEquals(1, environment.prepareCalls.get());
        assertEquals(1, environment.posted.size());
        assertEquals("latest-hint", environment.resolvedHints.get(0));
        assertTrue(runtime.hasUploaded(100L));
        assertTrue(contains(environment.logs, "media retry scheduled type=3"));
        assertTrue(contains(environment.logs, "media retry uploaded type=3"));
    }

    @Test
    public void scheduleSkipsWhenFirstPayloadAlreadyHasMedia() {
        final TestEnvironment environment = new TestEnvironment();
        MediaRetryRuntime runtime = new MediaRetryRuntime(
                new MediaUploadTracker(8),
                new long[]{0L},
                environment,
                inlineWorker());
        MessagePayload firstPayload = new MessagePayload();
        firstPayload.mediaBase64 = "already-uploaded";

        boolean scheduled = runtime.scheduleIfNeeded(request(3), firstPayload);

        assertFalse(scheduled);
        assertEquals(0, environment.prepareCalls.get());
        assertEquals(0, environment.posted.size());
    }

    @Test
    public void retryContinuesUntilMediaPayloadIsAvailable() {
        final TestEnvironment environment = new TestEnvironment();
        environment.blankPayloadsBeforeSuccess = 1;
        MediaRetryRuntime runtime = new MediaRetryRuntime(
                new MediaUploadTracker(8),
                new long[]{0L, 0L},
                environment,
                inlineWorker());

        boolean scheduled = runtime.scheduleIfNeeded(request(49), new MessagePayload());

        assertTrue(scheduled);
        assertEquals(2, environment.prepareCalls.get());
        assertEquals(1, environment.posted.size());
        assertTrue(runtime.hasUploaded(100L));
        assertTrue(contains(environment.logs, "media retry empty type=49"));
    }

    @Test
    public void retryContinuesAfterPrepareAttemptThrows() {
        final TestEnvironment environment = new TestEnvironment();
        environment.prepareFailuresBeforeSuccess = 1;
        MediaRetryRuntime runtime = new MediaRetryRuntime(
                new MediaUploadTracker(8),
                new long[]{0L, 0L},
                environment,
                inlineWorker());

        boolean scheduled = runtime.scheduleIfNeeded(request(3), new MessagePayload());

        assertTrue(scheduled);
        assertEquals(2, environment.prepareCalls.get());
        assertEquals(1, environment.posted.size());
        assertTrue(runtime.hasUploaded(100L));
        assertTrue(contains(environment.logs, "media retry failed type=3"));
        assertTrue(contains(environment.logs, "prepare-boom"));
    }

    @Test
    public void retryLogsStoppedWhenAttemptIsUnavailable() {
        final TestEnvironment environment = new TestEnvironment();
        environment.returnAttempt = false;
        MediaRetryRuntime runtime = new MediaRetryRuntime(
                new MediaUploadTracker(8),
                new long[]{0L, 0L},
                environment,
                inlineWorker());

        boolean scheduled = runtime.scheduleIfNeeded(request(3), new MessagePayload());

        assertTrue(scheduled);
        assertEquals(1, environment.prepareCalls.get());
        assertEquals(0, environment.posted.size());
        assertTrue(contains(environment.logs, "media retry stopped type=3"));
        assertTrue(contains(environment.logs, "attempt unavailable"));
    }


    private static MediaRetryRuntime.Request request(int type) {
        return new MediaRetryRuntime.Request(
                true,
                100L,
                Long.valueOf(200L),
                "talker",
                "content",
                Integer.valueOf(0),
                Long.valueOf(123456L),
                type,
                "fallback-hint");
    }

    private static MediaRetryRuntime.WorkerStarter inlineWorker() {
        return new MediaRetryRuntime.WorkerStarter() {
            @Override
            public void start(String name, Runnable runnable) {
                runnable.run();
            }
        };
    }

    private static boolean contains(List<String> values, String expected) {
        for (String value : values) {
            if (value != null && value.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static final class TestEnvironment implements MediaRetryRuntime.Environment {
        final AtomicInteger prepareCalls = new AtomicInteger();
        final List<MessagePayload> posted = new ArrayList<>();
        final List<String> resolvedHints = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        int blankPayloadsBeforeSuccess;
        int prepareFailuresBeforeSuccess;
        boolean returnAttempt = true;

        @Override
        public MediaRetryRuntime.Attempt prepareAttempt() {
            prepareCalls.incrementAndGet();
            if (!returnAttempt) {
                return null;
            }
            if (prepareFailuresBeforeSuccess > 0) {
                prepareFailuresBeforeSuccess--;
                throw new IllegalStateException("prepare-boom");
            }
            return new MediaRetryRuntime.Attempt() {
                @Override
                public String resolveMediaHint(MediaRetryRuntime.Request request) {
                    return "latest-hint";
                }

                @Override
                public MessagePayload buildPayload(MediaRetryRuntime.Request request, String mediaHint) {
                    resolvedHints.add(mediaHint);
                    MessagePayload payload = new MessagePayload();
                    payload.chatRecordId = request.recordId();
                    payload.mediaSize = 512;
                    if (blankPayloadsBeforeSuccess > 0) {
                        blankPayloadsBeforeSuccess--;
                    } else {
                        payload.mediaBase64 = "encoded";
                    }
                    return payload;
                }

                @Override
                public void post(MessagePayload payload) {
                    posted.add(payload);
                }
            };
        }

        @Override
        public void sleep(long millis) {
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }
    }
}
