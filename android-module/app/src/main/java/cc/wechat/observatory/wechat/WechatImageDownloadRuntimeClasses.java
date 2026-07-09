package cc.wechat.observatory.wechat;

final class WechatImageDownloadRuntimeClasses {
    final Class<?> sceneClass;
    final Class<?> progressCallbackClass;
    final Class<?> sceneEndCallbackClass;

    private WechatImageDownloadRuntimeClasses(
            Class<?> sceneClass,
            Class<?> progressCallbackClass,
            Class<?> sceneEndCallbackClass) {
        this.sceneClass = sceneClass;
        this.progressCallbackClass = progressCallbackClass;
        this.sceneEndCallbackClass = sceneEndCallbackClass;
    }

    static WechatImageDownloadRuntimeClasses resolve(
            WechatImageDownloadRuntime.Reflector reflector,
            ClassLoader classLoader) throws Exception {
        return new WechatImageDownloadRuntimeClasses(
                reflector.findClass(classLoader, "m11.t0"),
                reflector.findClass(classLoader, "com.tencent.mm.modelbase.v0"),
                reflector.findClass(classLoader, "com.tencent.mm.modelbase.u0"));
    }
}
