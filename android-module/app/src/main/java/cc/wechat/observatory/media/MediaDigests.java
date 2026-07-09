package cc.wechat.observatory.media;

import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaDigests {
    private MediaDigests() {
    }

    public static String normalizeMd5(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.US);
    }

    public static boolean isMd5Hex(String value) {
        if (isBlank(value) || value.length() != 32) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean digit = ch >= '0' && ch <= '9';
            boolean lower = ch >= 'a' && ch <= 'f';
            boolean upper = ch >= 'A' && ch <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    public static String shortMd5(String md5) {
        if (isBlank(md5)) {
            return "<empty>";
        }
        String value = md5.trim();
        if (value.length() <= 14) {
            return value;
        }
        return value.substring(0, 8) + "..." + value.substring(value.length() - 6);
    }
}
