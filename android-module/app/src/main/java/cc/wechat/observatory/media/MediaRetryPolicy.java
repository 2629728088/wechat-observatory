package cc.wechat.observatory.media;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaRetryPolicy {
    private MediaRetryPolicy() {
    }

    public static boolean shouldSchedule(
            boolean mediaUploadEnabled,
            long chatRecordId,
            int messageType,
            boolean alreadyUploaded,
            String firstMediaBase64) {
        if (!mediaUploadEnabled || chatRecordId <= 0L || alreadyUploaded) {
            return false;
        }
        if (!isBlank(firstMediaBase64)) {
            return false;
        }
        return !isBlank(MediaFiles.kindForMessageType(messageType));
    }
}
