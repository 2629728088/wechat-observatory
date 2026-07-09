package cc.wechat.observatory;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.media.MediaRetryRuntime;
import cc.wechat.observatory.model.MessagePayload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class HookMediaRetryEnvironmentFactoryTest {
    @Test
    public void createWiresCollaboratorsIntoRetryEnvironment() throws Exception {
        BridgeConfig config = config(true, true, "https://bridge.test", "key");
        Object database = new Object();
        CapturingGate gate = new CapturingGate();
        CapturingBridge bridge = new CapturingBridge();
        CapturingSleeper sleeper = new CapturingSleeper();
        CapturingLogger logger = new CapturingLogger();
        HookMediaRetryEnvironment environment = new HookMediaRetryEnvironmentFactory(
                new FixedConfigLoader(config),
                new FixedDatabaseProvider(database),
                gate,
                gate,
                gate,
                sleeper,
                logger)
                .create(bridge);

        MediaRetryRuntime.Attempt attempt = environment.prepareAttempt();
        MediaRetryRuntime.Request request = request();
        String hint = attempt.resolveMediaHint(request);
        MessagePayload payload = attempt.buildPayload(request, hint);
        attempt.post(payload);
        environment.sleep(321L);
        environment.log("retry-log");

        assertNotNull(attempt);
        assertSame(config, gate.targetConfig);
        assertSame(config, gate.bindConfig);
        assertSame(config, gate.registerConfig);
        assertSame(database, bridge.database);
        assertSame(config, bridge.config);
        assertSame(request, bridge.request);
        assertEquals("resolved-hint", hint);
        assertEquals(100L, payload.chatRecordId);
        assertSame(payload, bridge.postedPayload);
        assertEquals(321L, sleeper.millis);
        assertEquals("retry-log", logger.messages.get(0));
    }

    @Test
    public void createRejectsAttemptWhenAnyGateFails() throws Exception {
        BridgeConfig config = config(true, true, "https://bridge.test", "key");
        CapturingGate gate = new CapturingGate();
        gate.target = false;
        HookMediaRetryEnvironment environment = new HookMediaRetryEnvironmentFactory(
                new FixedConfigLoader(config),
                new FixedDatabaseProvider(new Object()),
                gate,
                gate,
                gate,
                null,
                null)
                .create(new CapturingBridge());

        assertNull(environment.prepareAttempt());
    }

    @Test
    public void createUsesSafeDefaultsWhenCollaboratorsAreMissing() throws Exception {
        HookMediaRetryEnvironment environment = new HookMediaRetryEnvironmentFactory(
                null,
                null,
                null,
                null,
                null,
                null,
                null)
                .create(null);

        assertNull(environment.prepareAttempt());
        environment.sleep(1L);
        environment.log("ignored");
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

    private static final class FixedConfigLoader implements HookMediaRetryEnvironmentFactory.ConfigLoader {
        private final BridgeConfig config;

        FixedConfigLoader(BridgeConfig config) {
            this.config = config;
        }

        @Override
        public BridgeConfig load() {
            return config;
        }
    }

    private static final class FixedDatabaseProvider implements HookMediaRetryEnvironmentFactory.DatabaseProvider {
        private final Object database;

        FixedDatabaseProvider(Object database) {
            this.database = database;
        }

        @Override
        public Object database() {
            return database;
        }
    }

    private static final class CapturingGate implements
            HookMediaRetryEnvironmentFactory.TargetUserChecker,
            HookMediaRetryEnvironmentFactory.RuntimeIdentityBinder,
            HookMediaRetryEnvironmentFactory.RegistrationEnsurer {
        boolean target = true;
        boolean bound = true;
        boolean registered = true;
        BridgeConfig targetConfig;
        BridgeConfig bindConfig;
        BridgeConfig registerConfig;

        @Override
        public boolean isTargetAndroidUser(BridgeConfig config) {
            this.targetConfig = config;
            return target;
        }

        @Override
        public boolean bindRuntimeIdentity(BridgeConfig config) {
            this.bindConfig = config;
            return bound;
        }

        @Override
        public boolean ensureRegistered(BridgeConfig config) {
            this.registerConfig = config;
            return registered;
        }
    }

    private static final class CapturingBridge implements HookMediaRetryEnvironment.MessageBridge {
        Object database;
        BridgeConfig config;
        MediaRetryRuntime.Request request;
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

    private static final class CapturingSleeper implements HookMediaRetryEnvironmentFactory.Sleeper {
        long millis;

        @Override
        public void sleep(long millis) {
            this.millis = millis;
        }
    }

    private static final class CapturingLogger implements HookMediaRetryEnvironmentFactory.Logger {
        final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}
