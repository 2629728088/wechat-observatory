package cc.wechat.observatory.media;

import java.io.File;

import static cc.wechat.observatory.util.Strings.isBlank;
import static cc.wechat.observatory.util.Strings.shortError;

public final class MediaResolverRuntime {
    private static final int IMAGE_INFO_HINT_TYPE = MediaFiles.MESSAGE_TYPE_IMAGE;
    private static final long IMAGE_INFO_HINT_CREATE_TIME = 0L;
    private static final String IMAGE_INFO_HINT_ORIGINAL = "";
    private static final String IMAGE_INFO_HINT_EMOJI_MD5 = "";

    public interface Environment {
        File appRoot();

        default String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
            return mediaHint;
        }

        void log(String message);

        void onEmojiDiagnosticNeeded(String emojiMd5);
    }

    public interface EmojiInfoProvider {
        Object load(String emojiMd5);
    }

    private final Environment environment;
    private final EmojiInfoProvider emojiInfoProvider;

    public MediaResolverRuntime(Environment environment) {
        this(environment, null);
    }

    public MediaResolverRuntime(Environment environment, EmojiInfoProvider emojiInfoProvider) {
        this.environment = environment;
        this.emojiInfoProvider = emojiInfoProvider;
    }

    public File resolve(int type, String mediaHint, long createTime, String emojiMd5) {
        return resolveDetailed(type, mediaHint, createTime, emojiMd5).file();
    }

    public ImageDownloadResolution.Candidate resolveCandidate(int type, String mediaHint, long createTime, String emojiMd5) {
        return imageDownloadCandidate(resolveDetailed(type, mediaHint, createTime, emojiMd5));
    }

    public MediaResolver.Result resolveDetailed(int type, String mediaHint, long createTime, String emojiMd5) {
        if (environment == null) {
            return MediaResolver.Result.notFound();
        }
        MediaResolver.Logger logger = logger();
        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                environment.appRoot(),
                type,
                mediaHint,
                createTime,
                emojiMd5,
                logger);
        if (result.needsEmojiDiagnostic()) {
            MediaResolver.Result emojiInfoResult = resolveEmojiInfoMedia(type, emojiMd5, logger);
            if (!emojiInfoResult.isNotFound()) {
                return emojiInfoResult;
            }
            environment.onEmojiDiagnosticNeeded(emojiMd5);
        }
        return result;
    }

    private MediaResolver.Result resolveEmojiInfoMedia(
            int type,
            String emojiMd5,
            MediaResolver.Logger logger) {
        if (!MediaFiles.isEmojiMessageType(type)) {
            return MediaResolver.Result.notFound();
        }
        Object emojiInfo;
        try {
            emojiInfo = emojiInfoProvider == null ? null : emojiInfoProvider.load(emojiMd5);
        } catch (Throwable t) {
            environment.log("emoji info load failed md5=" + MediaDigests.shortMd5(emojiMd5)
                    + " error=" + shortError(t));
            return MediaResolver.Result.notFound();
        }
        return WechatEmojiInfoMediaResolver.resolve(environment.appRoot(), emojiInfo, emojiMd5, logger);
    }

    private MediaResolver.Logger logger() {
        return new MediaResolver.Logger() {
            @Override
            public void log(String message) {
                environment.log(message);
            }
        };
    }

    public ImageDownloadResolution.Candidate resolveImageInfoCandidate(long localId, long serverId) {
        if (environment == null) {
            return ImageDownloadResolution.Candidate.missing();
        }
        String hint = resolveImageInfoHint(localId, serverId);
        if (isBlank(hint)) {
            return ImageDownloadResolution.Candidate.missing();
        }
        return resolveImageInfoHintCandidate(hint);
    }

    public ImageDownloadResolution.Candidate resolveImageInfoCandidate(long localId, long serverId, String talker) {
        return resolveImageInfoCandidate(localId, serverId);
    }

    private String resolveImageInfoHint(long localId, long serverId) {
        return environment.resolveMediaHint(
                IMAGE_INFO_HINT_TYPE,
                Long.valueOf(localId),
                Long.valueOf(serverId),
                IMAGE_INFO_HINT_ORIGINAL);
    }

    private ImageDownloadResolution.Candidate resolveImageInfoHintCandidate(String hint) {
        return imageDownloadCandidate(resolveDetailed(
                IMAGE_INFO_HINT_TYPE,
                hint,
                IMAGE_INFO_HINT_CREATE_TIME,
                IMAGE_INFO_HINT_EMOJI_MD5));
    }

    public static ImageDownloadResolution.Candidate imageDownloadCandidate(MediaResolver.Result result) {
        return ImageDownloadCandidateMapper.fromMediaResult(result);
    }

    public static ImageDownloadResolution.Candidate imageDownloadCandidate(MediaResolver.ImageInfoResult result) {
        return ImageDownloadCandidateMapper.fromImageInfoResult(result);
    }
}
