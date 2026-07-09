package cc.wechat.observatory.media;

import static cc.wechat.observatory.util.Strings.shortError;

final class MediaRetryLogLine {
    private MediaRetryLogLine() {
    }

    static String uploaded(int type, long chatRecordId, int attempt, long mediaSize) {
        return "media retry uploaded type=" + type
                + " msgId=" + chatRecordId
                + " attempt=" + attempt
                + " size=" + mediaSize;
    }

    static String scheduled(int type, long chatRecordId) {
        return "media retry scheduled type=" + type + " msgId=" + chatRecordId;
    }

    static String exhausted(int type, long chatRecordId) {
        return "media retry exhausted type=" + type + " msgId=" + chatRecordId;
    }

    static String empty(int type, long chatRecordId, int attempt) {
        return "media retry empty type=" + type
                + " msgId=" + chatRecordId
                + " attempt=" + attempt;
    }

    static String stopped(int type, long chatRecordId, int attempt, String reason) {
        return "media retry stopped type=" + type
                + " msgId=" + chatRecordId
                + " attempt=" + attempt
                + " reason=" + sanitizeReason(reason);
    }

    static String failed(int type, long chatRecordId, int attempt, Throwable t) {
        return "media retry failed type=" + type
                + " msgId=" + chatRecordId
                + " attempt=" + attempt
                + " error=" + shortError(rootCause(t));
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

    private static String sanitizeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "unknown";
        }
        return reason.replace('\n', ' ').replace('\r', ' ');
    }
}
