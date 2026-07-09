package cc.wechat.observatory.wechat;

import java.io.File;
import java.util.concurrent.Callable;

import cc.wechat.observatory.media.ImageDownloadResolution;
import cc.wechat.observatory.media.MediaAttachmentEnvironment;
import cc.wechat.observatory.media.MediaAttachmentServices;

public final class WechatMediaAttachmentServices implements MediaAttachmentServices {
    public interface Environment {
        File appRoot();

        String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint);

        ClassLoader classLoader();

        long resolveImageInfoId(long localId, long serverId);

        void runOnMainThread(Callable<Void> callable) throws Exception;

        void sleep(long millis);

        String encode(byte[] bytes);

        void log(String message);

        default Object loadEmojiInfo(String emojiMd5) {
            return null;
        }

        void onEmojiDiagnosticNeeded(String emojiMd5);
    }

    interface ImageDownloadRequester {
        boolean request(long localId, long serverId, String talker);
    }

    private final MediaAttachmentEnvironment delegate;

    public WechatMediaAttachmentServices(final Environment environment) {
        this(environment, WechatMediaAttachmentEnvironmentFactory.defaultImageDownloadRequester(environment));
    }

    WechatMediaAttachmentServices(final Environment environment, ImageDownloadRequester imageDownloadRequester) {
        this.delegate = WechatMediaAttachmentEnvironmentFactory.create(environment, imageDownloadRequester);
    }

    @Override
    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(
            int type,
            String mediaHint,
            long createTime,
            String emojiMd5) {
        return delegate.resolveMediaFileCandidate(type, mediaHint, createTime, emojiMd5);
    }

    @Override
    public ImageDownloadResolution.Candidate resolveImageInfoCandidate(long localId, long serverId, String talker) {
        return delegate.resolveImageInfoCandidate(localId, serverId, talker);
    }

    @Override
    public boolean requestImageDownload(long localId, long serverId, String talker) {
        return delegate.requestImageDownload(localId, serverId, talker);
    }

    @Override
    public String encode(byte[] bytes) {
        return delegate.encode(bytes);
    }

    @Override
    public void log(String message) {
        delegate.log(message);
    }
}
