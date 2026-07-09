package cc.wechat.observatory.media;

final class MediaAttachmentSkipLogLine {
    private MediaAttachmentSkipLogLine() {
    }

    static String selectorMissing(int type, long chatRecordId) {
        return base(type, chatRecordId) + " reason=selector_missing";
    }

    static String mediaNotSelected(
            int type,
            long chatRecordId,
            MediaFileSelector.Selection selection) {
        return base(type, chatRecordId)
                + " selectionStatus="
                + (selection == null ? "null" : selection.status());
    }

    static String writeFailed(
            int type,
            long chatRecordId,
            MediaFileSelector.SelectionStatus selectionStatus,
            MediaPayloadWriter.Status writerStatus) {
        return base(type, chatRecordId)
                + " selectionStatus="
                + selectionStatus
                + " writerStatus="
                + writerStatus;
    }

    private static String base(int type, long chatRecordId) {
        return "media attachment skipped type=" + type + " msgId=" + chatRecordId;
    }
}
