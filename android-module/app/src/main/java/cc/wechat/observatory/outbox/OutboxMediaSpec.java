package cc.wechat.observatory.outbox;

import org.json.JSONObject;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class OutboxMediaSpec {
    public static final int DEFAULT_VOICE_DURATION_MS = 1000;

    public final String mediaUrl;
    public final String mediaName;
    public final int durationMs;

    private OutboxMediaSpec(String mediaUrl, String mediaName, int durationMs) {
        this.mediaUrl = mediaUrl;
        this.mediaName = mediaName;
        this.durationMs = durationMs;
    }

    public static OutboxMediaSpec from(JSONObject item) {
        JSONObject payload = item == null ? null : item.optJSONObject("payload_json");
        return fromValues(
                item == null ? "" : item.optString("media_url", ""),
                item == null ? "" : item.optString("media_name", ""),
                item == null ? 0 : item.optInt("media_duration_ms", 0),
                item == null ? 0 : item.optInt("duration_ms", 0),
                payload == null ? "" : payload.optString("media_url", ""),
                payload == null ? "" : payload.optString("media_name", ""),
                payload == null ? 0 : payload.optInt("media_duration_ms", 0),
                payload == null ? 0 : payload.optInt("duration_ms", 0));
    }

    static OutboxMediaSpec fromValues(
            String mediaUrl,
            String mediaName,
            int mediaDurationMs,
            int durationMs,
            String payloadMediaUrl,
            String payloadMediaName,
            int payloadMediaDurationMs,
            int payloadDurationMs) {
        return new OutboxMediaSpec(
                firstNonBlank(mediaUrl, payloadMediaUrl),
                firstNonBlank(mediaName, payloadMediaName),
                firstPositiveInt(
                        mediaDurationMs,
                        durationMs,
                        payloadMediaDurationMs,
                        payloadDurationMs,
                        DEFAULT_VOICE_DURATION_MS));
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : (second == null ? "" : second);
    }

    private static int firstPositiveInt(int first, int second, int third, int fourth, int fallback) {
        if (first > 0) {
            return first;
        }
        if (second > 0) {
            return second;
        }
        if (third > 0) {
            return third;
        }
        if (fourth > 0) {
            return fourth;
        }
        return fallback;
    }
}
