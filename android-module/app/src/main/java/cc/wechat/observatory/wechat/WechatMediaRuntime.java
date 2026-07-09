package cc.wechat.observatory.wechat;

import java.io.File;
import java.util.concurrent.Callable;

import cc.wechat.observatory.media.ImageDownloadRequestTracker;
import cc.wechat.observatory.media.MediaAttachmentProcessor;
import cc.wechat.observatory.media.MediaAttachmentRuntime;
import cc.wechat.observatory.media.MediaHintRuntime;

public final class WechatMediaRuntime {
    public interface Environment {
        Object database();

        File appRoot();

        ClassLoader classLoader();

        void runOnMainThread(Callable<Void> callable) throws Exception;

        void sleep(long millis);

        String encode(byte[] bytes);

        void log(String message);

        default Object loadEmojiInfo(String emojiMd5) {
            return null;
        }

        void onEmojiDiagnosticNeeded(String emojiMd5);
    }

    private final ImageDownloadRequestTracker imageDownloadTracker;
    private final MediaHintRuntime mediaHintRuntime;
    private final WechatMediaAttachmentServices mediaAttachmentServices;

    public WechatMediaRuntime(ImageDownloadRequestTracker imageDownloadTracker, Environment environment) {
        this.imageDownloadTracker = imageDownloadTracker;
        this.mediaHintRuntime = WechatMediaRuntimeFactory.mediaHintRuntime(environment);
        this.mediaAttachmentServices = WechatMediaRuntimeFactory.mediaAttachmentServices(
                environment,
                new WechatMediaRuntimeFactory.HintResolver() {
                    @Override
                    public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
                        return WechatMediaRuntime.this.resolveMediaHint(type, msgId, msgSvrId, mediaHint);
                    }

                    @Override
                    public long resolveImageInfoId(long localId, long serverId) {
                        return WechatMediaRuntime.this.resolveImageInfoId(localId, serverId);
                    }
                });
    }

    public MediaAttachmentProcessor.Result attachDetailed(MediaAttachmentProcessor.Request request) {
        return new MediaAttachmentRuntime(imageDownloadTracker, mediaAttachmentServices)
                .attachDetailed(request);
    }

    public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
        return mediaHintRuntime.resolve(type, msgId, msgSvrId, mediaHint);
    }

    public long resolveImageInfoId(long localId, long serverId) {
        return mediaHintRuntime.resolveImageInfoId(localId, serverId);
    }
}
