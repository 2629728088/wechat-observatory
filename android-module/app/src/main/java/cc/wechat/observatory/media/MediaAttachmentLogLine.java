package cc.wechat.observatory.media;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaAttachmentLogLine {
    private MediaAttachmentLogLine() {
    }

    public static boolean shouldLog(MediaAttachmentProcessor.Result result) {
        if (result == null || result.status() == null) {
            return false;
        }
        switch (result.status()) {
            case ATTACHED:
            case RUNTIME_UNAVAILABLE:
            case SELECTOR_MISSING:
            case MEDIA_NOT_SELECTED:
            case WRITE_FAILED:
                return true;
            default:
                return false;
        }
    }

    public static String format(int type, long chatRecordId, MediaAttachmentProcessor.Result result) {
        StringBuilder builder = new StringBuilder();
        builder.append("media attachment result type=")
                .append(type)
                .append(" msgId=")
                .append(chatRecordId);
        if (result == null || result.status() == null) {
            return builder.append(" status=null").toString();
        }
        builder.append(" status=").append(result.status());
        if (!isBlank(result.mediaKind())) {
            builder.append(" kind=").append(result.mediaKind());
        }
        if (result.selectionStatus() != null) {
            builder.append(" selectionStatus=").append(result.selectionStatus());
        }
        if (result.writerStatus() != null) {
            builder.append(" writerStatus=").append(result.writerStatus());
        }
        return builder.toString();
    }
}
