package cc.wechat.observatory.wechat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class WechatImageDownloadRuntime {
    public interface Logger {
        void log(String message);
    }

    public interface ImageInfoIdResolver {
        long resolve(ClassLoader classLoader, long msgId, long msgSvrId, String talker);
    }

    interface Reflector {
        Class<?> findClass(ClassLoader classLoader, String name) throws Exception;

        Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws Exception;

        Constructor<?> findConstructor(Class<?> cls, Class<?>... parameterTypes) throws Exception;

        Field findFieldAny(Class<?> cls, String... names) throws Exception;

        void setIntFieldAny(Object target, int value, String... names) throws Exception;
    }

    private static final int DOWNLOAD_TYPE_MID = 0;
    private static final int DOWNLOAD_TYPE_HD = 1;

    private final ImageInfoIdResolver imageInfoIdResolver;
    private final Logger logger;
    private final Reflector reflector;
    private final WechatImageDownloadSceneFactory sceneFactory;
    private final WechatImageDownloadQueue queue;
    private final WechatImageDownloadCallbackFactory callbackFactory;
    private final WechatImageDownloadAutostart autostart;

    public WechatImageDownloadRuntime(ImageInfoIdResolver imageInfoIdResolver, Logger logger) {
        this(imageInfoIdResolver, logger, new DefaultReflector());
    }

    WechatImageDownloadRuntime(ImageInfoIdResolver imageInfoIdResolver, Logger logger, Reflector reflector) {
        this.imageInfoIdResolver = imageInfoIdResolver;
        this.logger = logger;
        this.reflector = reflector;
        this.sceneFactory = new WechatImageDownloadSceneFactory(reflector);
        this.queue = new WechatImageDownloadQueue(reflector);
        this.callbackFactory = new WechatImageDownloadCallbackFactory();
        this.autostart = new WechatImageDownloadAutostart(reflector, logger);
    }

    public void request(ClassLoader classLoader, long msgId, long msgSvrId, String talker) throws Exception {
        enqueue(classLoader, msgId, msgSvrId, talker, DOWNLOAD_TYPE_MID);
        enqueue(classLoader, msgId, msgSvrId, talker, DOWNLOAD_TYPE_HD);
    }

    private void enqueue(ClassLoader classLoader, long msgId, long msgSvrId, String talker, int downloadType) throws Exception {
        WechatImageDownloadRuntimeClasses classes =
                WechatImageDownloadRuntimeClasses.resolve(reflector, classLoader);
        Object callback = callbackFactory.create(
                classLoader,
                classes.progressCallbackClass,
                classes.sceneEndCallbackClass,
                new WechatImageDownloadCallbackFactory.Handler() {
                    @Override
                    public void onSceneEnd(Object callback, Object[] args) {
                        unregisterCallback(classLoader, callback);
                        log(WechatImageDownloadLogLine.onSceneEnd(args));
                    }
                });
        Long localMsgId = Long.valueOf(msgId);
        Long imageInfoId = Long.valueOf(imageInfoIdResolver == null ? msgId : imageInfoIdResolver.resolve(classLoader, msgId, msgSvrId, talker));
        Object scene = sceneFactory.create(
                classes.sceneClass,
                imageInfoId.longValue(),
                localMsgId.longValue(),
                talker,
                downloadType,
                classes.progressCallbackClass,
                callback);
        autostart.enable(classLoader, scene, msgId);
        Object result = queue.dispatch(classLoader, scene, callback);
        log(WechatImageDownloadLogLine.dispatched(result));
        log(WechatImageDownloadLogLine.enqueued(imageInfoId.longValue(), msgId, msgSvrId, downloadType));
    }

    private void unregisterCallback(ClassLoader classLoader, Object callback) {
        try {
            queue.unregister(classLoader, callback);
        } catch (Throwable t) {
            log(WechatImageDownloadLogLine.callbackUnregisterSkipped(t));
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

    private static final class DefaultReflector implements Reflector {
        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) throws Exception {
            return Class.forName(name, false, classLoader);
        }

        @Override
        public Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws Exception {
            Class<?> current = cls;
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
            Method method = cls.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }

        @Override
        public Constructor<?> findConstructor(Class<?> cls, Class<?>... parameterTypes) throws Exception {
            Constructor<?> ctor = cls.getDeclaredConstructor(parameterTypes);
            ctor.setAccessible(true);
            return ctor;
        }

        @Override
        public Field findFieldAny(Class<?> cls, String... names) throws Exception {
            NoSuchFieldException last = null;
            for (String name : names) {
                try {
                    return findField(cls, name);
                } catch (NoSuchFieldException e) {
                    last = e;
                }
            }
            throw last == null ? new NoSuchFieldException("") : last;
        }

        @Override
        public void setIntFieldAny(Object target, int value, String... names) throws Exception {
            findFieldAny(target.getClass(), names).setInt(target, value);
        }

        private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
            Class<?> current = cls;
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }
    }
}
