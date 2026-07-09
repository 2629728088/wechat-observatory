package cc.wechat.observatory.media;

public final class MediaAttachmentEnvironment implements MediaAttachmentServices {
    public interface ImageDownloadRequester {
        boolean request(long localId, long serverId, String talker);
    }

    public interface Encoder {
        String encode(byte[] bytes);
    }

    public interface Logger {
        void log(String message);
    }

    private final MediaResolverRuntime mediaResolverRuntime;
    private final ImageDownloadRequester imageDownloadRequester;
    private final Encoder encoder;
    private final Logger logger;

    public MediaAttachmentEnvironment(
            MediaResolverRuntime mediaResolverRuntime,
            ImageDownloadRequester imageDownloadRequester,
            Encoder encoder,
            Logger logger) {
        this.mediaResolverRuntime = mediaResolverRuntime;
        this.imageDownloadRequester = imageDownloadRequester;
        this.encoder = encoder;
        this.logger = logger;
    }

    @Override
    public ImageDownloadResolution.Candidate resolveMediaFileCandidate(int type, String mediaHint, long createTime, String emojiMd5) {
        return mediaResolverRuntime == null
                ? ImageDownloadResolution.Candidate.missing()
                : mediaResolverRuntime.resolveCandidate(type, mediaHint, createTime, emojiMd5);
    }

    @Override
    public ImageDownloadResolution.Candidate resolveImageInfoCandidate(long localId, long serverId, String talker) {
        return mediaResolverRuntime == null
                ? ImageDownloadResolution.Candidate.missing()
                : mediaResolverRuntime.resolveImageInfoCandidate(localId, serverId);
    }

    @Override
    public boolean requestImageDownload(long localId, long serverId, String talker) {
        return imageDownloadRequester != null && imageDownloadRequester.request(localId, serverId, talker);
    }

    @Override
    public String encode(byte[] bytes) {
        return encoder == null ? "" : encoder.encode(bytes);
    }

    @Override
    public void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
