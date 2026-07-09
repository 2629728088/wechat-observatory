package cc.wechat.observatory.wechat;

final class WechatImageDownloadQueue {
    private static final int SCENE_TYPE_GET_MSG_IMG = 109;

    private final WechatImageDownloadRuntime.Reflector reflector;

    WechatImageDownloadQueue(WechatImageDownloadRuntime.Reflector reflector) {
        this.reflector = reflector;
    }

    Object dispatch(ClassLoader classLoader, Object scene, Object callback) throws Exception {
        WechatImageDownloadQueueClasses classes = WechatImageDownloadQueueClasses.resolve(reflector, classLoader);
        Object queue = queue(classes);
        reflector.findMethod(queue.getClass(), "a", int.class, classes.sceneEndCallbackClass)
                .invoke(queue, Integer.valueOf(SCENE_TYPE_GET_MSG_IMG), callback);
        return reflector.findMethod(queue.getClass(), "g", classes.modelSceneClass)
                .invoke(queue, scene);
    }

    void unregister(ClassLoader classLoader, Object callback) throws Exception {
        WechatImageDownloadQueueClasses classes = WechatImageDownloadQueueClasses.resolve(reflector, classLoader);
        Object queue = queue(classes);
        reflector.findMethod(queue.getClass(), "q", int.class, classes.sceneEndCallbackClass)
                .invoke(queue, Integer.valueOf(SCENE_TYPE_GET_MSG_IMG), callback);
    }

    private Object queue(WechatImageDownloadQueueClasses classes) throws Exception {
        return reflector.findMethod(classes.queueClass, "d").invoke(null);
    }
}
