package cc.wechat.observatory.wechat;

final class WechatImageDownloadSceneFactory {
    private final WechatImageDownloadRuntime.Reflector reflector;

    WechatImageDownloadSceneFactory(WechatImageDownloadRuntime.Reflector reflector) {
        this.reflector = reflector;
    }

    Object create(
            Class<?> sceneClass,
            long imageInfoId,
            long localMsgId,
            String talker,
            int downloadType,
            Class<?> progressCallbackClass,
            Object callback) throws Exception {
        try {
            return reflector.findConstructor(
                            sceneClass,
                            long.class,
                            long.class,
                            String.class,
                            int.class,
                            progressCallbackClass,
                            int.class)
                    .newInstance(
                            Long.valueOf(imageInfoId),
                            Long.valueOf(localMsgId),
                            talkerOrEmpty(talker),
                            Integer.valueOf(downloadType),
                            callback,
                            Integer.valueOf(0));
        } catch (NoSuchMethodException ignored) {
            return reflector.findConstructor(
                            sceneClass,
                            long.class,
                            long.class,
                            String.class,
                            int.class,
                            progressCallbackClass)
                    .newInstance(
                            Long.valueOf(imageInfoId),
                            Long.valueOf(localMsgId),
                            talkerOrEmpty(talker),
                            Integer.valueOf(downloadType),
                            callback);
        }
    }

    private static String talkerOrEmpty(String talker) {
        return talker == null ? "" : talker;
    }
}
