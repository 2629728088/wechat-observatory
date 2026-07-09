package cc.wechat.observatory.media;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class WechatImageInfoStore {
    public interface Loader {
        Object load(Object storage, String methodName, Object... args) throws Exception;
    }

    private WechatImageInfoStore() {
    }

    public static Object load(Object storage, long msgId, long msgSvrId, String talker, Loader loader) {
        if (storage == null || loader == null) {
            return null;
        }
        Object info = null;
        if (!isBlank(talker) && msgId > 0L) {
            info = tryLoad(storage, loader, "T1", talker, Long.valueOf(msgId));
        }
        if (info == null && !isBlank(talker) && msgSvrId > 0L) {
            info = tryLoad(storage, loader, "b2", talker, Long.valueOf(msgSvrId));
        }
        if (info == null && msgId > 0L) {
            info = tryLoad(storage, loader, "C1", Long.valueOf(msgId));
        }
        if (info == null && msgSvrId > 0L) {
            info = tryLoad(storage, loader, "C1", Long.valueOf(msgSvrId));
        }
        return info;
    }

    public static long downloadIdOrFallback(Object storage, long msgId, long msgSvrId, String talker, Loader loader) {
        long imageInfoId = WechatImageInfo.localId(load(storage, msgId, msgSvrId, talker, loader));
        return imageInfoId > 0L ? imageInfoId : msgId;
    }

    private static Object tryLoad(Object storage, Loader loader, String methodName, Object... args) {
        try {
            Object info = loader.load(storage, methodName, args);
            return WechatImageInfo.localId(info) > 0L ? info : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
