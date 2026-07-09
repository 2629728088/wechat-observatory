package cc.wechat.observatory.wechat;

import java.lang.reflect.Field;
import java.util.Set;

final class WechatImageDownloadAutostartCdnPending {
    private final WechatImageDownloadRuntime.Reflector reflector;

    WechatImageDownloadAutostartCdnPending(WechatImageDownloadRuntime.Reflector reflector) {
        this.reflector = reflector;
    }

    boolean enable(ClassLoader classLoader, long msgId) throws Exception {
        Object pending = pendingSet(cdnCore(classLoader));
        if (pending instanceof Set && msgId > 0L) {
            addPendingImageKey((Set) pending, msgId);
            return true;
        }
        return false;
    }

    private Object cdnCore(ClassLoader classLoader) throws Exception {
        return reflector.findMethod(
                        reflector.findClass(classLoader, "com.tencent.mm.modelcdntran.s1"),
                        "fj")
                .invoke(null);
    }

    private Object pendingSet(Object cdnCore) throws Exception {
        Field pendingField = reflector.findFieldAny(cdnCore.getClass(), "u");
        return pendingField.get(cdnCore);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPendingImageKey(Set pending, long msgId) {
        synchronized (pending) {
            pending.add("image_" + msgId);
        }
    }
}
