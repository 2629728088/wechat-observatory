package cc.wechat.observatory.wechat;

import java.util.concurrent.Callable;

final class WechatImageDownloadBridgeEnvironmentAdapters {
    private WechatImageDownloadBridgeEnvironmentAdapters() {
    }

    static ClassLoader classLoader(WechatImageDownloadBridge.Environment environment) {
        ClassLoader loader = environment == null ? null : environment.classLoader();
        return loader == null ? WechatImageDownloadBridge.class.getClassLoader() : loader;
    }

    static WechatImageDownloadRequester.ImageInfoIdResolver imageInfoIdResolver(
            final WechatImageDownloadBridge.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new WechatImageDownloadRequester.ImageInfoIdResolver() {
            @Override
            public long resolve(long requestedLocalId, long requestedServerId) {
                return environment.resolveImageInfoId(requestedLocalId, requestedServerId);
            }
        };
    }

    static WechatImageDownloadRequester.MainThreadRunner mainThreadRunner(
            final WechatImageDownloadBridge.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new WechatImageDownloadRequester.MainThreadRunner() {
            @Override
            public void run(Callable<Void> callable) throws Exception {
                environment.runOnMainThread(callable);
            }
        };
    }

    static WechatImageDownloadRequester.Sleeper sleeper(
            final WechatImageDownloadBridge.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new WechatImageDownloadRequester.Sleeper() {
            @Override
            public void sleep(long millis) {
                environment.sleep(millis);
            }
        };
    }

    static WechatImageDownloadRequester.Logger logger(
            final WechatImageDownloadBridge.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new WechatImageDownloadRequester.Logger() {
            @Override
            public void log(String message) {
                environment.log(message);
            }
        };
    }
}
