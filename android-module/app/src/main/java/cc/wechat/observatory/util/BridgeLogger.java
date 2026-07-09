package cc.wechat.observatory.util;

import java.util.regex.Pattern;

import de.robv.android.xposed.XposedBridge;

public final class BridgeLogger {
    private static final String TAG = "WechatGateway";
    private static final Pattern WXID_PATTERN = Pattern.compile("\\bwxid_[A-Za-z0-9_-]+\\b");
    private static final Pattern CHATROOM_PATTERN = Pattern.compile("\\b[A-Za-z0-9_-]+@chatroom\\b");
    private static final Pattern LONG_BASE64_PATTERN = Pattern.compile("\\b[A-Za-z0-9+/=]{256,}\\b");
    private static final Pattern SENSITIVE_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b([A-Za-z0-9_]*(?:api_key|apikey|token|cookie|session|password|secret|credential|auth|authkey|tpauthkey|aeskey|tpurl|url|wxid|selfwxid|media_base64|mediabase64))=([^\\s,&]+)");
    private static final Pattern SENSITIVE_JSON_PATTERN = Pattern.compile(
            "(?i)(\"[A-Za-z0-9_]*(?:api_key|apikey|token|cookie|session|password|secret|credential|auth|authkey|tpauthkey|aeskey|tpurl|url|wxid|selfwxid|media_base64|mediabase64)\"\\s*:\\s*\")([^\"]*)(\")");

    private BridgeLogger() {
    }

    public static void log(String message) {
        XposedBridge.log(TAG + ": " + sanitize(message));
    }

    static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String sanitized = SENSITIVE_JSON_PATTERN.matcher(message).replaceAll("$1<redacted>$3");
        sanitized = SENSITIVE_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=<redacted>");
        sanitized = WXID_PATTERN.matcher(sanitized).replaceAll("<wxid>");
        sanitized = CHATROOM_PATTERN.matcher(sanitized).replaceAll("<chatroom>");
        return LONG_BASE64_PATTERN.matcher(sanitized).replaceAll("<base64>");
    }
}
