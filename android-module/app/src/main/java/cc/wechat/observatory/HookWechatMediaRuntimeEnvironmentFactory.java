package cc.wechat.observatory;

import java.util.concurrent.Callable;

import cc.wechat.observatory.wechat.WechatMediaRuntimeEnvironment;

import static cc.wechat.observatory.util.Strings.shortError;

final class HookWechatMediaRuntimeEnvironmentFactory {
    private HookWechatMediaRuntimeEnvironmentFactory() {
    }

    static WechatMediaRuntimeEnvironment create(
            Object database,
            final HookWechatMediaRuntimeProvider.AppRootProvider appRootProvider,
            final HookWechatMediaRuntimeProvider.ClassLoaderProvider classLoaderProvider,
            final HookWechatMediaRuntimeProvider.MainThreadRunner mainThreadRunner,
            final HookWechatMediaRuntimeProvider.Sleeper sleeper,
            final HookWechatMediaRuntimeProvider.Encoder encoder,
            final HookWechatMediaRuntimeProvider.Logger logger,
            final HookWechatMediaRuntimeProvider.EmojiInfoLoader emojiInfoLoader,
            final HookWechatMediaRuntimeProvider.EmojiDiagnosticReporter emojiDiagnosticReporter) {
        final ClassLoader runtimeClassLoader = classLoader(classLoaderProvider);
        return new WechatMediaRuntimeEnvironment(
                database,
                appRootProvider == null ? null : appRootProvider.appRoot(),
                runtimeClassLoader,
                new WechatMediaRuntimeEnvironment.MainThreadRunner() {
                    @Override
                    public void run(Callable<Void> callable) throws Exception {
                        if (mainThreadRunner != null) {
                            mainThreadRunner.run(callable);
                        } else if (callable != null) {
                            callable.call();
                        }
                    }
                },
                new WechatMediaRuntimeEnvironment.Sleeper() {
                    @Override
                    public void sleep(long millis) {
                        if (sleeper != null) {
                            sleeper.sleep(millis);
                        }
                    }
                },
                new WechatMediaRuntimeEnvironment.Encoder() {
                    @Override
                    public String encode(byte[] bytes) {
                        return encoder == null ? "" : encoder.encode(bytes);
                    }
                },
                new WechatMediaRuntimeEnvironment.Logger() {
                    @Override
                    public void log(String message) {
                        if (logger != null) {
                            logger.log(message);
                        }
                    }
                },
                new WechatMediaRuntimeEnvironment.EmojiInfoLoader() {
                    @Override
                    public Object load(String emojiMd5) {
                        if (emojiInfoLoader == null) {
                            return null;
                        }
                        try {
                            return emojiInfoLoader.load(runtimeClassLoader, emojiMd5);
                        } catch (Throwable t) {
                            if (logger != null) {
                                logger.log("emoji info load failed error=" + shortError(t));
                            }
                            return null;
                        }
                    }
                },
                new WechatMediaRuntimeEnvironment.EmojiDiagnosticReporter() {
                    @Override
                    public void report(String emojiMd5) {
                        if (emojiDiagnosticReporter != null) {
                            emojiDiagnosticReporter.report(emojiMd5);
                        }
                    }
                });
    }

    private static ClassLoader classLoader(HookWechatMediaRuntimeProvider.ClassLoaderProvider provider) {
        ClassLoader classLoader = provider == null ? null : provider.classLoader();
        return classLoader == null ? HookWechatMediaRuntimeEnvironmentFactory.class.getClassLoader() : classLoader;
    }
}
