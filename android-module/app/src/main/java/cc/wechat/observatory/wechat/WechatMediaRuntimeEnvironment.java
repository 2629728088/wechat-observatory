package cc.wechat.observatory.wechat;

import java.io.File;
import java.util.concurrent.Callable;

public final class WechatMediaRuntimeEnvironment implements WechatMediaRuntime.Environment {
    public interface MainThreadRunner {
        void run(Callable<Void> callable) throws Exception;
    }

    public interface Sleeper {
        void sleep(long millis);
    }

    public interface Encoder {
        String encode(byte[] bytes);
    }

    public interface Logger {
        void log(String message);
    }

    public interface EmojiDiagnosticReporter {
        void report(String emojiMd5);
    }

    public interface EmojiInfoLoader {
        Object load(String emojiMd5);
    }

    private final Object database;
    private final File appRoot;
    private final ClassLoader classLoader;
    private final MainThreadRunner mainThreadRunner;
    private final Sleeper sleeper;
    private final Encoder encoder;
    private final Logger logger;
    private final EmojiInfoLoader emojiInfoLoader;
    private final EmojiDiagnosticReporter emojiDiagnosticReporter;

    public WechatMediaRuntimeEnvironment(
            Object database,
            File appRoot,
            ClassLoader classLoader,
            MainThreadRunner mainThreadRunner,
            Sleeper sleeper,
            Encoder encoder,
            Logger logger,
            EmojiDiagnosticReporter emojiDiagnosticReporter) {
        this(database, appRoot, classLoader, mainThreadRunner, sleeper, encoder, logger, null, emojiDiagnosticReporter);
    }

    public WechatMediaRuntimeEnvironment(
            Object database,
            File appRoot,
            ClassLoader classLoader,
            MainThreadRunner mainThreadRunner,
            Sleeper sleeper,
            Encoder encoder,
            Logger logger,
            EmojiInfoLoader emojiInfoLoader,
            EmojiDiagnosticReporter emojiDiagnosticReporter) {
        this.database = database;
        this.appRoot = appRoot;
        this.classLoader = classLoader;
        this.mainThreadRunner = mainThreadRunner;
        this.sleeper = sleeper;
        this.encoder = encoder;
        this.logger = logger;
        this.emojiInfoLoader = emojiInfoLoader;
        this.emojiDiagnosticReporter = emojiDiagnosticReporter;
    }

    @Override
    public Object database() {
        return database;
    }

    @Override
    public File appRoot() {
        return appRoot;
    }

    @Override
    public ClassLoader classLoader() {
        return classLoader;
    }

    @Override
    public void runOnMainThread(Callable<Void> callable) throws Exception {
        if (mainThreadRunner == null) {
            if (callable != null) {
                callable.call();
            }
            return;
        }
        mainThreadRunner.run(callable);
    }

    @Override
    public void sleep(long millis) {
        if (sleeper != null) {
            sleeper.sleep(millis);
        }
    }

    @Override
    public String encode(byte[] bytes) {
        return encoder == null ? "" : encoder.encode(bytes);
    }

    @Override
    public void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

    @Override
    public Object loadEmojiInfo(String emojiMd5) {
        return emojiInfoLoader == null ? null : emojiInfoLoader.load(emojiMd5);
    }

    @Override
    public void onEmojiDiagnosticNeeded(String emojiMd5) {
        if (emojiDiagnosticReporter != null) {
            emojiDiagnosticReporter.report(emojiMd5);
        }
    }
}
