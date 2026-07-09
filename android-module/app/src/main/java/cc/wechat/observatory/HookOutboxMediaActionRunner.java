package cc.wechat.observatory;

import cc.wechat.observatory.outbox.OutboxMediaFilePreparer;
import cc.wechat.observatory.wechat.SendResult;

import static cc.wechat.observatory.util.Strings.shortError;

final class HookOutboxMediaActionRunner {
    interface PreparedMediaProvider {
        OutboxMediaFilePreparer.PreparedMedia prepare() throws Exception;
    }

    interface MediaSender {
        SendResult send(OutboxMediaFilePreparer.PreparedMedia media) throws Exception;
    }

    private HookOutboxMediaActionRunner() {
    }

    static SendResult run(String kind, PreparedMediaProvider provider, MediaSender sender) {
        OutboxMediaFilePreparer.PreparedMedia media = null;
        try {
            media = provider == null ? null : provider.prepare();
            if (media == null) {
                return SendResult.failed(kind + " media prepare failed");
            }
            if (!media.ok()) {
                return SendResult.failed(media.error);
            }
            SendResult result = sender == null
                    ? SendResult.failed(kind + " sender is unavailable")
                    : sender.send(media);
            if (result.ok) {
                media.retain();
            }
            return result;
        } catch (Throwable t) {
            return SendResult.failed(kind + " send failed: " + shortError(t));
        } finally {
            if (media != null) {
                media.cleanup(kind);
            }
        }
    }
}
