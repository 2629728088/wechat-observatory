package cc.wechat.observatory;

import java.io.File;
import java.util.concurrent.Callable;

import cc.wechat.observatory.media.ImageDownloadRequestTracker;
import cc.wechat.observatory.wechat.WechatMediaRuntime;
import cc.wechat.observatory.wechat.WechatMediaRuntimeEnvironment;

final class HookWechatMediaRuntimeProvider implements HookMediaAttachmentBridge.RuntimeProvider {
    interface AppRootProvider {
        File appRoot();
    }

    interface ClassLoaderProvider {
        ClassLoader classLoader();
    }

    interface MainThreadRunner {
        void run(Callable<Void> callable) throws Exception;
    }

    interface Sleeper {
        void sleep(long millis);
    }

    interface Encoder {
        String encode(byte[] bytes);
    }

    interface Logger {
        void log(String message);
    }

    interface EmojiDiagnosticReporter {
        void report(String emojiMd5);
    }

    interface EmojiInfoLoader {
        Object load(ClassLoader classLoader, String emojiMd5) throws Exception;
    }

    private final ImageDownloadRequestTracker imageDownloadTracker;
    private final AppRootProvider appRootProvider;
    private final ClassLoaderProvider classLoaderProvider;
    private final MainThreadRunner mainThreadRunner;
    private final Sleeper sleeper;
    private final Encoder encoder;
    private final Logger logger;
    private final EmojiInfoLoader emojiInfoLoader;
    private final EmojiDiagnosticReporter emojiDiagnosticReporter;

    HookWechatMediaRuntimeProvider(
            ImageDownloadRequestTracker imageDownloadTracker,
            AppRootProvider appRootProvider,
            ClassLoaderProvider classLoaderProvider,
            MainThreadRunner mainThreadRunner,
            Sleeper sleeper,
            Encoder encoder,
            Logger logger,
            EmojiDiagnosticReporter emojiDiagnosticReporter) {
        this(imageDownloadTracker, appRootProvider, classLoaderProvider, mainThreadRunner, sleeper,
                encoder, logger, null, emojiDiagnosticReporter);
    }

    HookWechatMediaRuntimeProvider(
            ImageDownloadRequestTracker imageDownloadTracker,
            AppRootProvider appRootProvider,
            ClassLoaderProvider classLoaderProvider,
            MainThreadRunner mainThreadRunner,
            Sleeper sleeper,
            Encoder encoder,
            Logger logger,
            EmojiInfoLoader emojiInfoLoader,
            EmojiDiagnosticReporter emojiDiagnosticReporter) {
        this.imageDownloadTracker = imageDownloadTracker;
        this.appRootProvider = appRootProvider;
        this.classLoaderProvider = classLoaderProvider;
        this.mainThreadRunner = mainThreadRunner;
        this.sleeper = sleeper;
        this.encoder = encoder;
        this.logger = logger;
        this.emojiInfoLoader = emojiInfoLoader;
        this.emojiDiagnosticReporter = emojiDiagnosticReporter;
    }

    @Override
    public HookMediaAttachmentBridge.Runtime runtime(Object database) {
        return HookMediaAttachmentBridge.fromWechatRuntime(new WechatMediaRuntime(
                imageDownloadTracker,
                environment(database)));
    }

    WechatMediaRuntimeEnvironment environment(Object database) {
        return HookWechatMediaRuntimeEnvironmentFactory.create(
                database,
                appRootProvider,
                classLoaderProvider,
                mainThreadRunner,
                sleeper,
                encoder,
                logger,
                emojiInfoLoader,
                emojiDiagnosticReporter);
    }
}
