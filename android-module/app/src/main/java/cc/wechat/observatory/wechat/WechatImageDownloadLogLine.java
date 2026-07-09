package cc.wechat.observatory.wechat;

import static cc.wechat.observatory.util.Strings.shortError;

final class WechatImageDownloadLogLine {
    private WechatImageDownloadLogLine() {
    }

    static String skippedMissingLocalId(long msgSvrId) {
        return "image NetSceneGetMsgImg skipped: missing local msgId msgSvrId=" + msgSvrId;
    }

    static String requestFailed(long msgId, long msgSvrId, Throwable t) {
        return "image NetSceneGetMsgImg request failed msgId=" + msgId
                + " msgSvrId=" + msgSvrId
                + " error=" + shortError(rootCause(t));
    }

    static String enqueued(long imageInfoId, long msgId, long msgSvrId, int downloadType) {
        return "image NetSceneGetMsgImg enqueued imageInfoId=" + imageInfoId
                + " msgId=" + msgId
                + " msgSvrId=" + msgSvrId
                + " type=" + downloadType;
    }

    static String dispatched(Object result) {
        return "image NetSceneGetMsgImg dispatched via NetSceneQueue result=" + result;
    }

    static String autostartSceneFlagFailed(long msgId, Throwable t) {
        return "image NetSceneGetMsgImg autostart scene flag failed msgId=" + msgId
                + " error=" + shortError(rootCause(t));
    }

    static String autostartQueueFlagFailed(long msgId, Throwable t) {
        return "image NetSceneGetMsgImg autostart queue flag failed msgId=" + msgId
                + " error=" + shortError(rootCause(t));
    }

    static String autostartEnabled(long msgId) {
        return "image NetSceneGetMsgImg autostart enabled msgId=" + msgId;
    }

    static String onSceneEnd(Object[] args) {
        return "image NetSceneGetMsgImg onSceneEnd errType="
                + arg(args, 0)
                + " errCode=" + arg(args, 1);
    }

    static String callbackUnregisterSkipped(Throwable t) {
        return "image NetSceneGetMsgImg callback unregister skipped: " + shortError(rootCause(t));
    }

    private static Object arg(Object[] args, int index) {
        return args != null && args.length > index ? args[index] : "?";
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        int depth = 0;
        while (current != null && current.getCause() != null && current.getCause() != current && depth < 8) {
            current = current.getCause();
            depth++;
        }
        return current == null ? t : current;
    }
}
