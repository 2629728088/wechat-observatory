package cc.wechat.observatory.media;

import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaCacheNames {
    private MediaCacheNames() {
    }

    public static String safeExtension(String mediaName, String mediaUrl) {
        String value = firstNonBlank(mediaName, mediaUrl).trim().toLowerCase(Locale.US);
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int dot = value.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= value.length()) {
            return ".img";
        }
        String ext = value.substring(dot);
        if (ext.length() > 8) {
            return ".img";
        }
        for (int i = 1; i < ext.length(); i++) {
            char ch = ext.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'))) {
                return ".img";
            }
        }
        return ext;
    }

    public static String safeFileName(String mediaName, String mediaUrl) {
        String value = firstNonBlank(mediaName, mediaUrl).trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < value.length()) {
            value = value.substring(slash + 1);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && out.length() < 80; i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else if (ch == '.' || ch == '_' || ch == '-') {
                out.append(ch);
            }
        }
        String name = out.toString();
        if (isBlank(name) || ".".equals(name) || "..".equals(name) || name.startsWith(".")) {
            String ext = safeExtension(mediaName, mediaUrl);
            if (".img".equals(ext)) {
                ext = ".bin";
            }
            name = "file" + ext;
        }
        return name;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
