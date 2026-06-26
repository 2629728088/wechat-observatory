package cc.wechat.observatory.util;

import de.robv.android.xposed.XposedBridge;

public final class BridgeLogger {
    private static final String TAG = "WechatGateway";

    private BridgeLogger() {
    }

    public static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
