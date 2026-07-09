package cc.wechat.observatory;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.media.MediaRetryRuntime;
import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class HookMediaRetryEnvironmentTest {
    @Test
    public void prepareAttemptRejectsDisabledOrIncompleteConfig() throws Exception {
        assertNull(environment(config(false, true, "https://bridge.test", "key")).prepareAttempt());
        assertNull(environment(config(true, false, "https://bridge.test", "key")).prepareAttempt());
        assertNull(environment(config(true, true, "", "key")).prepareAttempt());
        assertNull(environment(config(true, true, "https://bridge.test", "")).prepareAttempt());
    }

    @Test
    public void prepareAttemptRejectsFailedRuntimeGate() throws Exception {
        CapturingGate gate = new CapturingGate();
        gate.target = false;

        MediaRetryRuntime.Attempt attempt = environment(
                config(true, true, "https://bridge.test", "key"),
                new Object(),
                gate,
                new CapturingBridge()).prepareAttempt();

        assertNull(attempt);
    }

    @Test
    public void attemptDelegatesHintPayloadAndPostUsingCurrentDatabaseAndConfig() throws Exception {
        BridgeConfig config = config(true, true, "https://bridge.test", "key");
        Object database = new Object();
        CapturingBridge bridge = new CapturingBridge();
        HookMediaRetryEnvironment environment = environment(
                config,
                database,
                new CapturingGate(),
                bridge);

        MediaRetryRuntime.Request request = request();
        MediaRetryRuntime.Attempt attempt = environment.prepareAttempt();
        String hint = attempt.resolveMediaHint(request);
        MessagePayload payload = attempt.buildPayload(request, hint);
        attempt.post(payload);

        assertEquals("resolved-hint", hint);
        assertSame(database, bridge.database);
        assertSame(config, bridge.config);
        assertSame(request, bridge.request);
        assertEquals("resolved-hint", bridge.mediaHint);
        assertSame(payload, bridge.postedPayload);
        assertEquals(100L, payload.chatRecordId);
        assertEquals("encoded", payload.mediaBase64);
    }

    @Test
    public void sleepAndLogDelegateToCollaborators() {
        AtomicLong slept = new AtomicLong();
        CapturingLogger logger = new CapturingLogger();
        HookMediaRetryEnvironment environment = new HookMediaRetryEnvironment(
                null,
                null,
                null,
                null,
                new HookMediaRetryEnvironment.Sleeper() {
                    @Override
                    public void sleep(long millis) {
                        slept.set(millis);
                    }
                },
                logger);

        environment.sleep(123L);
        environment.log("message");

        assertEquals(123L, slept.get());
        assertEquals(1, logger.messages.size());
        assertEquals("message", logger.messages.get(0));
    }

    private static HookMediaRetryEnvironment environment(final BridgeConfig config) {
        return environment(config, new Object(), new CapturingGate(), new CapturingBridge());
    }

    private static HookMediaRetryEnvironment environment(
            final BridgeConfig config,
            final Object database,
            HookMediaRetryEnvironment.RuntimeGate gate,
            HookMediaRetryEnvironment.MessageBridge bridge) {
        return new HookMediaRetryEnvironment(
                new HookMediaRetryEnvironment.ConfigLoader() {
                    @Override
                    public BridgeConfig load() {
                        return config;
                    }
                },
                new HookMediaRetryEnvironment.DatabaseProvider() {
                    @Override
                    public Object database() {
                        return database;
                    }
                },
                gate,
                bridge,
                null,
                null);
    }

    private static BridgeConfig config(
            boolean enabled,
            boolean mediaUploadEnabled,
            String baseUrl,
            String apiKey) throws Exception {
        Constructor<BridgeConfig> constructor = BridgeConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        BridgeConfig config = constructor.newInstance();
        config.enabled = enabled;
        config.mediaUploadEnabled = mediaUploadEnabled;
        config.baseUrl = baseUrl;
        config.apiKey = apiKey;
        return config;
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

    private static final class CapturingGate implements HookMediaRetryEnvironment.RuntimeGate {
        boolean target = true;
        boolean bound = true;
        boolean registered = true;

        @Override
        public boolean isTargetAndroidUser(BridgeConfig config) {
            return target;
        }

        @Override
        public boolean bindRuntimeIdentity(BridgeConfig config) {
            return bound;
        }

        @Override
        public boolean ensureRegistered(BridgeConfig config) {
            return registered;
        }
    }

    private static final class CapturingBridge implements HookMediaRetryEnvironment.MessageBridge {
        Object database;
        BridgeConfig config;
        MediaRetryRuntime.Request request;
        String mediaHint;
        MessagePayload postedPayload;

        @Override
        public String resolveMediaHint(Object database, MediaRetryRuntime.Request request) {
            this.database = database;
            this.request = request;
            return "resolved-hint";
        }

        @Override
        public MessagePayload buildPayload(BridgeConfig config, MediaRetryRuntime.Request request, String mediaHint) {
            this.config = config;
            this.request = request;
            this.mediaHint = mediaHint;
            MessagePayload payload = new MessagePayload();
            payload.chatRecordId = request.recordId();
            payload.mediaBase64 = "encoded";
            return payload;
        }

        @Override
        public void post(BridgeConfig config, MessagePayload payload) {
            this.config = config;
            this.postedPayload = payload;
        }
    }

    private static final class CapturingLogger implements HookMediaRetryEnvironment.Logger {
        final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}
