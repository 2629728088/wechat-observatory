package cc.wechat.observatory.wechat;

import java.util.concurrent.Callable;

public final class WechatImageDownloadBridge {
    public interface Environment {
        ClassLoader classLoader();

        long resolveImageInfoId(long localId, long serverId);

        void runOnMainThread(Callable<Void> callable) throws Exception;

        void sleep(long millis);

        void log(String message);
    }

    interface Requester {
        boolean request(ClassLoader classLoader, long localId, long serverId, String talker);
    }

    interface RequesterFactory {
        Requester create(
                WechatImageDownloadRequester.ImageInfoIdResolver imageInfoIdResolver,
                WechatImageDownloadRequester.MainThreadRunner mainThreadRunner,
                WechatImageDownloadRequester.Sleeper sleeper,
                WechatImageDownloadRequester.Logger logger);
    }

    private final Environment environment;
    private final RequesterFactory requesterFactory;

    public WechatImageDownloadBridge(Environment environment) {
        this(environment, new DefaultRequesterFactory());
    }

    WechatImageDownloadBridge(Environment environment, RequesterFactory requesterFactory) {
        this.environment = environment;
        this.requesterFactory = requesterFactory == null ? new DefaultRequesterFactory() : requesterFactory;
    }

    public boolean request(long localId, long serverId, String talker) {
        if (environment == null) {
            return false;
        }
        return requesterFactory.create(
                        WechatImageDownloadBridgeEnvironmentAdapters.imageInfoIdResolver(environment),
                        WechatImageDownloadBridgeEnvironmentAdapters.mainThreadRunner(environment),
                        WechatImageDownloadBridgeEnvironmentAdapters.sleeper(environment),
                        WechatImageDownloadBridgeEnvironmentAdapters.logger(environment))
                .request(WechatImageDownloadBridgeEnvironmentAdapters.classLoader(environment),
                        localId,
                        serverId,
                        talker);
    }

    private static final class DefaultRequesterFactory implements RequesterFactory {
        @Override
        public Requester create(
                WechatImageDownloadRequester.ImageInfoIdResolver imageInfoIdResolver,
                WechatImageDownloadRequester.MainThreadRunner mainThreadRunner,
                WechatImageDownloadRequester.Sleeper sleeper,
                WechatImageDownloadRequester.Logger logger) {
            final WechatImageDownloadRequester requester = new WechatImageDownloadRequester(
                    imageInfoIdResolver,
                    mainThreadRunner,
                    sleeper,
                    logger);
            return new Requester() {
                @Override
                public boolean request(ClassLoader classLoader, long localId, long serverId, String talker) {
                    return requester.request(classLoader, localId, serverId, talker);
                }
            };
        }
    }
}
