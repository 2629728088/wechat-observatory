package cc.wechat.observatory.wechat;

public final class SendResult {
    public final boolean ok;
    public final long chatRecordId;
    public final String error;

    private SendResult(boolean ok, long chatRecordId, String error) {
        this.ok = ok;
        this.chatRecordId = chatRecordId;
        this.error = error;
    }

    public static SendResult sent(long chatRecordId) {
        return new SendResult(true, chatRecordId, "");
    }

    public static SendResult failed(String error) {
        return new SendResult(false, 0L, error == null ? "send failed" : error);
    }
}
