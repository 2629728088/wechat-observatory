package cc.wechat.observatory;

import cc.wechat.observatory.config.BridgeConfig;

final class HookMediaRetryEnvironmentFactory {
    interface ConfigLoader {
        BridgeConfig load() throws Exception;
    }

    interface DatabaseProvider {
        Object database();
    }

    interface TargetUserChecker {
        boolean isTargetAndroidUser(BridgeConfig config) throws Exception;
    }

    interface RuntimeIdentityBinder {
        boolean bindRuntimeIdentity(BridgeConfig config) throws Exception;
    }

    interface RegistrationEnsurer {
        boolean ensureRegistered(BridgeConfig config) throws Exception;
    }

    interface Sleeper {
        void sleep(long millis);
    }

    interface Logger {
        void log(String message);
    }

    private final ConfigLoader configLoader;
    private final DatabaseProvider databaseProvider;
    private final TargetUserChecker targetUserChecker;
    private final RuntimeIdentityBinder runtimeIdentityBinder;
    private final RegistrationEnsurer registrationEnsurer;
    private final Sleeper sleeper;
    private final Logger logger;

    HookMediaRetryEnvironmentFactory(
            ConfigLoader configLoader,
            DatabaseProvider databaseProvider,
            TargetUserChecker targetUserChecker,
            RuntimeIdentityBinder runtimeIdentityBinder,
            RegistrationEnsurer registrationEnsurer,
            Sleeper sleeper,
            Logger logger) {
        this.configLoader = configLoader;
        this.databaseProvider = databaseProvider;
        this.targetUserChecker = targetUserChecker;
        this.runtimeIdentityBinder = runtimeIdentityBinder;
        this.registrationEnsurer = registrationEnsurer;
        this.sleeper = sleeper;
        this.logger = logger;
    }

    HookMediaRetryEnvironment create(HookMediaRetryEnvironment.MessageBridge messageBridge) {
        return new HookMediaRetryEnvironment(
                new HookMediaRetryEnvironment.ConfigLoader() {
                    @Override
                    public BridgeConfig load() throws Exception {
                        return configLoader == null ? null : configLoader.load();
                    }
                },
                new HookMediaRetryEnvironment.DatabaseProvider() {
                    @Override
                    public Object database() {
                        return databaseProvider == null ? null : databaseProvider.database();
                    }
                },
                new HookMediaRetryEnvironment.RuntimeGate() {
                    @Override
                    public boolean isTargetAndroidUser(BridgeConfig config) throws Exception {
                        return targetUserChecker != null && targetUserChecker.isTargetAndroidUser(config);
                    }

                    @Override
                    public boolean bindRuntimeIdentity(BridgeConfig config) throws Exception {
                        return runtimeIdentityBinder != null && runtimeIdentityBinder.bindRuntimeIdentity(config);
                    }

                    @Override
                    public boolean ensureRegistered(BridgeConfig config) throws Exception {
                        return registrationEnsurer != null && registrationEnsurer.ensureRegistered(config);
                    }
                },
                messageBridge,
                new HookMediaRetryEnvironment.Sleeper() {
                    @Override
                    public void sleep(long millis) {
                        if (sleeper != null) {
                            sleeper.sleep(millis);
                        }
                    }
                },
                new HookMediaRetryEnvironment.Logger() {
                    @Override
                    public void log(String message) {
                        if (logger != null) {
                            logger.log(message);
                        }
                    }
                });
    }
}
