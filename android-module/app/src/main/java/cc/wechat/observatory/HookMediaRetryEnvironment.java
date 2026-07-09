package cc.wechat.observatory;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.media.MediaRetryRuntime;
import cc.wechat.observatory.model.MessagePayload;

import static cc.wechat.observatory.util.Strings.isBlank;

final class HookMediaRetryEnvironment implements MediaRetryRuntime.Environment {
    interface ConfigLoader {
        BridgeConfig load() throws Exception;
    }

    interface DatabaseProvider {
        Object database();
    }

    interface RuntimeGate {
        boolean isTargetAndroidUser(BridgeConfig config) throws Exception;

        boolean bindRuntimeIdentity(BridgeConfig config) throws Exception;

        boolean ensureRegistered(BridgeConfig config) throws Exception;
    }

    interface MessageBridge {
        String resolveMediaHint(Object database, MediaRetryRuntime.Request request);

        MessagePayload buildPayload(BridgeConfig config, MediaRetryRuntime.Request request, String mediaHint);

        void post(BridgeConfig config, MessagePayload payload) throws Exception;
    }

    interface Sleeper {
        void sleep(long millis);
    }

    interface Logger {
        void log(String message);
    }

    private final ConfigLoader configLoader;
    private final DatabaseProvider databaseProvider;
    private final RuntimeGate runtimeGate;
    private final MessageBridge messageBridge;
    private final Sleeper sleeper;
    private final Logger logger;

    HookMediaRetryEnvironment(
            ConfigLoader configLoader,
            DatabaseProvider databaseProvider,
            RuntimeGate runtimeGate,
            MessageBridge messageBridge,
            Sleeper sleeper,
            Logger logger) {
        this.configLoader = configLoader;
        this.databaseProvider = databaseProvider;
        this.runtimeGate = runtimeGate;
        this.messageBridge = messageBridge;
        this.sleeper = sleeper;
        this.logger = logger;
    }

    @Override
    public MediaRetryRuntime.Attempt prepareAttempt() throws Exception {
        final BridgeConfig config = configLoader == null ? null : configLoader.load();
        if (config == null) {
            log("media retry prepare skipped: config unavailable");
            return null;
        }
        if (!config.enabled) {
            log("media retry prepare skipped: bridge disabled");
            return null;
        }
        if (!config.mediaUploadEnabled) {
            log("media retry prepare skipped: media upload disabled");
            return null;
        }
        if (isBlank(config.baseUrl) || isBlank(config.apiKey)) {
            log("media retry prepare skipped: bridge credentials unavailable");
            return null;
        }
        if (runtimeGate == null) {
            log("media retry prepare skipped: runtime gate unavailable");
            return null;
        }
        if (!runtimeGate.isTargetAndroidUser(config)) {
            log("media retry prepare skipped: non-target android user");
            return null;
        }
        if (!runtimeGate.bindRuntimeIdentity(config)) {
            log("media retry prepare skipped: runtime identity unavailable");
            return null;
        }
        if (!runtimeGate.ensureRegistered(config)) {
            log("media retry prepare skipped: registration unavailable");
            return null;
        }
        return new MediaRetryRuntime.Attempt() {
            @Override
            public String resolveMediaHint(MediaRetryRuntime.Request request) {
                return messageBridge == null
                        ? request.mediaHint()
                        : messageBridge.resolveMediaHint(currentDatabase(), request);
            }

            @Override
            public MessagePayload buildPayload(MediaRetryRuntime.Request request, String mediaHint) {
                return messageBridge == null ? null : messageBridge.buildPayload(config, request, mediaHint);
            }

            @Override
            public void post(MessagePayload payload) throws Exception {
                if (messageBridge != null) {
                    messageBridge.post(config, payload);
                }
            }
        };
    }

    @Override
    public void sleep(long millis) {
        if (sleeper != null) {
            sleeper.sleep(millis);
        }
    }

    @Override
    public void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

    private Object currentDatabase() {
        return databaseProvider == null ? null : databaseProvider.database();
    }
}
