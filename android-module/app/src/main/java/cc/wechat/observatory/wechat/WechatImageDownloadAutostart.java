package cc.wechat.observatory.wechat;

final class WechatImageDownloadAutostart {
    private final WechatImageDownloadRuntime.Logger logger;
    private final WechatImageDownloadAutostartSceneFlag sceneFlag;
    private final WechatImageDownloadAutostartCdnPending cdnPending;

    WechatImageDownloadAutostart(
            WechatImageDownloadRuntime.Reflector reflector,
            WechatImageDownloadRuntime.Logger logger) {
        this.logger = logger;
        this.sceneFlag = new WechatImageDownloadAutostartSceneFlag(reflector);
        this.cdnPending = new WechatImageDownloadAutostartCdnPending(reflector);
    }

    void enable(ClassLoader classLoader, Object scene, long msgId) {
        boolean enabled = enableSceneFlag(scene, msgId);
        enabled = enableCdnPendingFlag(classLoader, msgId) || enabled;
        if (enabled) {
            log(WechatImageDownloadLogLine.autostartEnabled(msgId));
        }
    }

    private boolean enableSceneFlag(Object scene, long msgId) {
        try {
            return sceneFlag.enable(scene);
        } catch (Throwable t) {
            log(WechatImageDownloadLogLine.autostartSceneFlagFailed(msgId, t));
            return false;
        }
    }

    private boolean enableCdnPendingFlag(ClassLoader classLoader, long msgId) {
        try {
            return cdnPending.enable(classLoader, msgId);
        } catch (Throwable t) {
            log(WechatImageDownloadLogLine.autostartQueueFlagFailed(msgId, t));
        }
        return false;
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
