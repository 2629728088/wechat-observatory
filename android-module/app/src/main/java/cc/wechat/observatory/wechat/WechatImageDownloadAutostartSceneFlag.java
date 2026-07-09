package cc.wechat.observatory.wechat;

final class WechatImageDownloadAutostartSceneFlag {
    private final WechatImageDownloadRuntime.Reflector reflector;

    WechatImageDownloadAutostartSceneFlag(WechatImageDownloadRuntime.Reflector reflector) {
        this.reflector = reflector;
    }

    boolean enable(Object scene) throws Exception {
        reflector.setIntFieldAny(scene, 1, "C");
        return true;
    }
}
