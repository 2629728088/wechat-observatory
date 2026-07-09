package cc.wechat.observatory.media;

import java.io.File;

public final class OutboundMediaNames {
    private static final int MAX_VOICE_BASE_LENGTH = 32;

    private OutboundMediaNames() {
    }

    public static String videoBaseName(File videoFile) {
        return videoBaseName(videoFile, System.currentTimeMillis());
    }

    static String videoBaseName(File videoFile, long timestampMillis) {
        StringBuilder out = safeAsciiName(baseName(videoFile), Integer.MAX_VALUE);
        if (out.length() == 0) {
            out.append("outbox_").append(timestampMillis);
        }
        return out.toString();
    }

    public static String voiceBaseName(File voiceFile) {
        return voiceBaseName(voiceFile, System.currentTimeMillis());
    }

    static String voiceBaseName(File voiceFile, long timestampMillis) {
        StringBuilder out = safeAsciiName(baseName(voiceFile), MAX_VOICE_BASE_LENGTH);
        if (out.length() == 0) {
            out.append("voice");
        }
        return "wo_voice_" + timestampMillis + "_" + out;
    }

    private static String baseName(File file) {
        String name = file == null ? "" : file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    private static StringBuilder safeAsciiName(String name, int maxLength) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < maxLength; i++) {
            char ch = name.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else if (ch == '_' || ch == '-') {
                out.append(ch);
            }
        }
        return out;
    }
}
