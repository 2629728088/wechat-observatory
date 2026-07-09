package cc.wechat.observatory.outbox;

import org.json.JSONObject;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class OutboxDispatchPolicy {
    private OutboxDispatchPolicy() {
    }

    public static int normalizedParallelism(int configured, int itemCount) {
        if (itemCount <= 1) {
            return 1;
        }
        int value = configured;
        if (value < 1) {
            value = 1;
        } else if (value > 8) {
            value = 8;
        }
        return Math.min(value, itemCount);
    }

    public static String laneKey(JSONObject item) {
        String wxid = item == null ? "" : item.optString("wxid", "").trim();
        if (!isBlank(wxid)) {
            return wxid;
        }
        long id = item == null ? 0L : item.optLong("id", 0L);
        return "outbox:" + id;
    }
}
