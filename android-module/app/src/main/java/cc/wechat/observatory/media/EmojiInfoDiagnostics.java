package cc.wechat.observatory.media;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class EmojiInfoDiagnostics {
    private static final int FIELD_LIMIT = 12;

    private EmojiInfoDiagnostics() {
    }

    public static String fieldSummary(Object emojiInfo) {
        if (emojiInfo == null) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        Set<String> seen = new HashSet<>();
        int count = 0;
        Class<?> current = emojiInfo.getClass();
        while (current != null && count < FIELD_LIMIT) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (count >= FIELD_LIMIT) {
                    break;
                }
                if (field == null
                        || field.getType() != String.class
                        || Modifier.isStatic(field.getModifiers())
                        || !seen.add(field.getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(emojiInfo);
                    if (value == null || isBlank(String.valueOf(value))) {
                        continue;
                    }
                    if (count > 0) {
                        out.append(", ");
                    }
                    out.append(field.getName()).append("=").append(summarizeValue(field.getName(), String.valueOf(value)));
                    count++;
                } catch (Throwable ignored) {
                    // Diagnostics only; skip fields that are not readable on this build.
                }
            }
            current = current.getSuperclass();
        }
        if (count == 0) {
            out.append("no-string-fields");
        }
        out.append("]");
        return out.toString();
    }

    static String summarizeValue(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return "<url>";
        }
        if (MediaDigests.isMd5Hex(trimmed)) {
            return MediaDigests.shortMd5(trimmed);
        }
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf(File.separatorChar) >= 0) {
            return shorten(trimmed, 96);
        }
        return shorten(trimmed, 80);
    }

    static String summarizeValue(String fieldName, String value) {
        if (isSensitiveFieldName(fieldName)) {
            return "<redacted>";
        }
        return summarizeValue(value);
    }

    private static boolean isSensitiveFieldName(String fieldName) {
        if (isBlank(fieldName)) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.US);
        return lower.contains("token")
                || lower.contains("key")
                || lower.contains("secret")
                || lower.contains("password")
                || lower.contains("cookie")
                || lower.contains("session")
                || lower.contains("credential")
                || lower.contains("auth")
                || lower.contains("url");
    }

    private static String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
