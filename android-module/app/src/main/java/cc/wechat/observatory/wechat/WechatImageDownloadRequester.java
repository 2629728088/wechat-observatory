package cc.wechat.observatory.wechat;

import java.util.concurrent.Callable;

public final class WechatImageDownloadRequester {
    public interface ImageInfoIdResolver {
        long resolve(long localId, long serverId);
    }

    public interface MainThreadRunner {
        void run(Callable<Void> callable) throws Exception;
    }

    public interface Sleeper {
        void sleep(long millis);
    }

    public interface Logger {
        void log(String message);
    }

    interface RuntimeInvoker {
        void request(ClassLoader classLoader, long imageInfoId, long localId, long serverId, String talker) throws Exception;
    }

    private static final long IMAGE_DOWNLOAD_WAIT_MS = 1800L;

    private final ImageInfoIdResolver imageInfoIdResolver;
    private final MainThreadRunner mainThreadRunner;
    private final Sleeper sleeper;
    private final Logger logger;
    private final RuntimeInvoker runtimeInvoker;

    public WechatImageDownloadRequester(
            ImageInfoIdResolver imageInfoIdResolver,
            MainThreadRunner mainThreadRunner,
            Sleeper sleeper,
            Logger logger) {
        this(imageInfoIdResolver, mainThreadRunner, sleeper, logger, null);
    }

    WechatImageDownloadRequester(
            ImageInfoIdResolver imageInfoIdResolver,
            MainThreadRunner mainThreadRunner,
            Sleeper sleeper,
            Logger logger,
            RuntimeInvoker runtimeInvoker) {
        this.imageInfoIdResolver = imageInfoIdResolver;
        this.mainThreadRunner = mainThreadRunner;
        this.sleeper = sleeper;
        this.logger = logger;
        this.runtimeInvoker = runtimeInvoker == null ? new DefaultRuntimeInvoker(logger) : runtimeInvoker;
    }

    public boolean request(final ClassLoader classLoader, final long localId, final long serverId, final String talker) {
        if (localId <= 0L) {
            log(WechatImageDownloadLogLine.skippedMissingLocalId(serverId));
            return false;
        }
        try {
            final long resolvedImageInfoId = imageInfoIdResolver == null ? 0L : imageInfoIdResolver.resolve(localId, serverId);
            final long imageInfoId = resolvedImageInfoId > 0L ? resolvedImageInfoId : localId;
            MainThreadRunner runner = mainThreadRunner;
            if (runner == null) {
                return false;
            }
            runner.run(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    runtimeInvoker.request(classLoader, imageInfoId, localId, serverId, talker);
                    return null;
                }
            });
            if (sleeper != null) {
                sleeper.sleep(IMAGE_DOWNLOAD_WAIT_MS);
            }
            return true;
        } catch (Throwable t) {
            log(WechatImageDownloadLogLine.requestFailed(localId, serverId, t));
            return false;
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

    private static final class DefaultRuntimeInvoker implements RuntimeInvoker {
        private final Logger logger;

        DefaultRuntimeInvoker(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void request(final ClassLoader classLoader, final long imageInfoId, long localId, long serverId, String talker) throws Exception {
            new WechatImageDownloadRuntime(
                    WechatImageDownloadRequesterRuntimeAdapters.imageInfoIdResolver(imageInfoId),
                    WechatImageDownloadRequesterRuntimeAdapters.logger(logger))
                    .request(classLoader, localId, serverId, talker);
        }
    }
}
