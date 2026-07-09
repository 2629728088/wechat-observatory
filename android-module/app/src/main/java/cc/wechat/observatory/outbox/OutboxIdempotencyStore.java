package cc.wechat.observatory.outbox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Map;

import cc.wechat.observatory.util.BridgeLogger;
import cc.wechat.observatory.wechat.SendResult;

import static cc.wechat.observatory.util.Strings.isBlank;
import static cc.wechat.observatory.util.Strings.shortError;

public final class OutboxIdempotencyStore {
    private static final String PREFS_NAME = "wechat_observatory_outbox_idempotency";
    private static final String SENT_PREFIX = "sent:";
    private static final long TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long PRUNE_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private static volatile long lastPruneAt = 0L;

    private final SharedPreferences prefs;

    public OutboxIdempotencyStore(Context context) {
        this.prefs = context == null ? null : context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public SendResult recordedSuccess(JSONObject item) {
        long id = item == null ? 0L : item.optLong("id", 0L);
        if (id <= 0L || prefs == null) {
            return null;
        }
        String raw = prefs.getString(sentKey(id), "");
        if (isBlank(raw)) {
            return null;
        }
        try {
            JSONObject record = new JSONObject(raw);
            long recordedAt = record.optLong("recorded_at", 0L);
            long now = System.currentTimeMillis();
            if (recordedAt > 0L && now - recordedAt > TTL_MS) {
                prefs.edit().remove(sentKey(id)).apply();
                return null;
            }

            String currentWxid = item.optString("wxid", "");
            String recordedWxid = record.optString("wxid", "");
            String currentKind = OutboxCommand.kind(item);
            String recordedKind = record.optString("kind", "");
            String currentFingerprint = OutboxCommand.fingerprint(item);
            String recordedFingerprint = record.optString("fingerprint", "");
            if (!sameCommand(currentWxid, currentKind, currentFingerprint, recordedWxid, recordedKind, recordedFingerprint)) {
                BridgeLogger.log("outbox idempotency record mismatch id=" + id
                        + " currentKind=" + currentKind
                        + " recordedKind=" + recordedKind
                        + " currentFp=" + OutboxCommand.shortFingerprint(currentFingerprint)
                        + " recordedFp=" + OutboxCommand.shortFingerprint(recordedFingerprint));
                return null;
            }

            long chatRecordId = record.optLong("chat_record_id", 0L);
            BridgeLogger.log("outbox idempotency hit id=" + id
                    + " kind=" + currentKind
                    + " fp=" + OutboxCommand.shortFingerprint(currentFingerprint)
                    + " chatRecordId=" + chatRecordId);
            return SendResult.sent(chatRecordId);
        } catch (Throwable t) {
            BridgeLogger.log("outbox idempotency read failed id=" + id + " error=" + shortError(t));
            return null;
        }
    }

    public void rememberSuccess(JSONObject item, SendResult result) {
        long id = item == null ? 0L : item.optLong("id", 0L);
        if (id <= 0L || result == null || !result.ok || prefs == null) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            prune(now);
            JSONObject record = new JSONObject();
            record.put("wxid", item.optString("wxid", ""));
            record.put("kind", OutboxCommand.kind(item));
            record.put("fingerprint", OutboxCommand.fingerprint(item));
            record.put("chat_record_id", result.chatRecordId);
            record.put("recorded_at", now);
            prefs.edit().putString(sentKey(id), record.toString()).apply();
        } catch (Throwable t) {
            BridgeLogger.log("outbox idempotency write failed id=" + id + " error=" + shortError(t));
        }
    }

    private static String sentKey(long id) {
        return SENT_PREFIX + id;
    }

    private static boolean sameCommand(
            String currentWxid,
            String currentKind,
            String currentFingerprint,
            String recordedWxid,
            String recordedKind,
            String recordedFingerprint) {
        if (!sameTalker(currentWxid, recordedWxid)) {
            return false;
        }
        return String.valueOf(currentKind).trim().equalsIgnoreCase(String.valueOf(recordedKind).trim())
                && !isBlank(currentFingerprint)
                && currentFingerprint.equals(recordedFingerprint);
    }

    private static boolean sameTalker(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return !a.isEmpty() && a.equals(b);
    }

    private void prune(long now) {
        if (prefs == null || now - lastPruneAt < PRUNE_INTERVAL_MS) {
            return;
        }
        lastPruneAt = now;
        SharedPreferences.Editor editor = null;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(SENT_PREFIX) || !(entry.getValue() instanceof String)) {
                continue;
            }
            try {
                JSONObject record = new JSONObject((String) entry.getValue());
                long recordedAt = record.optLong("recorded_at", 0L);
                if (recordedAt > 0L && now - recordedAt > TTL_MS) {
                    if (editor == null) {
                        editor = prefs.edit();
                    }
                    editor.remove(key);
                }
            } catch (Throwable ignored) {
                if (editor == null) {
                    editor = prefs.edit();
                }
                editor.remove(key);
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }
}
