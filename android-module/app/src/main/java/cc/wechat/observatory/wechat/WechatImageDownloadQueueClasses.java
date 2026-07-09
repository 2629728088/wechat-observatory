package cc.wechat.observatory.wechat;

final class WechatImageDownloadQueueClasses {
    final Class<?> queueClass;
    final Class<?> sceneEndCallbackClass;
    final Class<?> modelSceneClass;

    private WechatImageDownloadQueueClasses(
            Class<?> queueClass,
            Class<?> sceneEndCallbackClass,
            Class<?> modelSceneClass) {
        this.queueClass = queueClass;
        this.sceneEndCallbackClass = sceneEndCallbackClass;
        this.modelSceneClass = modelSceneClass;
    }

    static WechatImageDownloadQueueClasses resolve(
            WechatImageDownloadRuntime.Reflector reflector,
            ClassLoader classLoader) throws Exception {
        return new WechatImageDownloadQueueClasses(
                reflector.findClass(classLoader, "gm0.j1"),
                reflector.findClass(classLoader, "com.tencent.mm.modelbase.u0"),
                reflector.findClass(classLoader, "com.tencent.mm.modelbase.m1"));
    }
}
