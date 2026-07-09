package cc.wechat.observatory.wechat;

import java.util.concurrent.Callable;

final class WechatImageDownloadServiceEnvironmentAdapters {
    private WechatImageDownloadServiceEnvironmentAdapters() {
    }

    static WechatImageDownloadBridge.Environment bridgeEnvironment(
            final WechatImageDownloadService.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new WechatImageDownloadBridge.Environment() {
            @Override
            public ClassLoader classLoader() {
                return environment.classLoader();
            }

            @Override
            public long resolveImageInfoId(long requestedLocalId, long requestedServerId) {
                return environment.resolveImageInfoId(requestedLocalId, requestedServerId);
            }

            @Override
            public void runOnMainThread(Callable<Void> callable) throws Exception {
                environment.runOnMainThread(callable);
            }

            @Override
            public void sleep(long millis) {
                environment.sleep(millis);
            }

            @Override
            public void log(String message) {
                environment.log(message);
            }
        };
    }
}
