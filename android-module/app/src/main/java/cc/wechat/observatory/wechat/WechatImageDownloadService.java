package cc.wechat.observatory.wechat;

import java.util.concurrent.Callable;

public final class WechatImageDownloadService {
    public interface Environment {
        ClassLoader classLoader();

        long resolveImageInfoId(long localId, long serverId);

        void runOnMainThread(Callable<Void> callable) throws Exception;

        void sleep(long millis);

        void log(String message);
    }

    interface Bridge {
        boolean request(long localId, long serverId, String talker);
    }

    interface BridgeFactory {
        Bridge create(WechatImageDownloadBridge.Environment environment);
    }

    private final Environment environment;
    private final BridgeFactory bridgeFactory;

    public WechatImageDownloadService(Environment environment) {
        this(environment, new DefaultBridgeFactory());
    }

    WechatImageDownloadService(Environment environment, BridgeFactory bridgeFactory) {
        this.environment = environment;
        this.bridgeFactory = bridgeFactory == null ? new DefaultBridgeFactory() : bridgeFactory;
    }

    public boolean request(long localId, long serverId, String talker) {
        if (environment == null) {
            return false;
        }
        return bridgeFactory.create(WechatImageDownloadServiceEnvironmentAdapters.bridgeEnvironment(environment))
                .request(localId, serverId, talker);
    }

    private static final class DefaultBridgeFactory implements BridgeFactory {
        @Override
        public Bridge create(final WechatImageDownloadBridge.Environment environment) {
            final WechatImageDownloadBridge bridge = new WechatImageDownloadBridge(environment);
            return new Bridge() {
                @Override
                public boolean request(long localId, long serverId, String talker) {
                    return bridge.request(localId, serverId, talker);
                }
            };
        }
    }
}
