package cc.wechat.observatory.outbox;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class OutboxCommand {
    private OutboxCommand() {
    }

    public static String kind(JSONObject item) {
        String kind = item == null ? "" : item.optString("kind", "");
        if (isBlank(kind) && item != null) {
            kind = item.optString("media_kind", "");
        }
        return isBlank(kind) ? "text" : kind.trim().toLowerCase(Locale.US);
    }

    public static String fingerprint(JSONObject item) throws Exception {
        if (item == null) {
            return "";
        }
        StringBuilder source = new StringBuilder();
        appendFingerprintPart(source, "wxid", item.optString("wxid", ""));
        appendFingerprintPart(source, "kind", kind(item));
        appendFingerprintPart(source, "text", item.optString("text", ""));
        appendFingerprintPart(source, "media_url", item.optString("media_url", ""));
        appendFingerprintPart(source, "media_name", item.optString("media_name", ""));
        appendFingerprintPart(source, "media_mime", item.optString("media_mime", ""));
        appendFingerprintPart(source, "media_size", String.valueOf(item.optLong("media_size", 0L)));
        appendFingerprintPart(source, "payload_json", payloadString(item));
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(source.toString().getBytes(StandardCharsets.UTF_8));
        return toHex(digest);
    }

    public static String shortFingerprint(String fingerprint) {
        if (isBlank(fingerprint)) {
            return "";
        }
        String trimmed = fingerprint.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12);
    }

    private static void appendFingerprintPart(StringBuilder out, String name, String value) {
        out.append(name).append('=');
        if (value != null) {
            out.append(value.trim());
        }
        out.append('\n');
    }

    private static String payloadString(JSONObject item) {
        Object value = item.opt("payload_json");
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        return String.valueOf(value);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes == null ? 0 : bytes.length * 2);
        if (bytes == null) {
            return "";
        }
        for (byte value : bytes) {
            int b = value & 0xff;
            if (b < 16) {
                out.append('0');
            }
            out.append(Integer.toHexString(b));
        }
        return out.toString();
    }
}
