package cc.wechat.observatory.media;

import java.io.File;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaFileSelector {
    public interface BaseResolver {
        ImageDownloadResolution.Candidate resolve(int type, String mediaHint, long createTime, String emojiMd5);
    }

    public interface ImageDownloadResolver {
        ImageDownloadResolution resolve(String mediaHint, Long msgId, Long msgSvrId, long createTime, String talker);
    }

    public interface EmojiResolver {
        File resolve(String emojiMd5);
    }

    public interface GchatImageResolver {
        File resolve(String content);
    }

    public interface Logger {
        void log(String message);
    }

    public enum SelectionStatus {
        UNSUPPORTED_TYPE,
        BASE_FILE,
        BASE_REF_TARGET,
        BASE_UNSUPPORTED,
        EMOJI_FILE,
        IMAGE_INFO_FILE,
        IMAGE_INFO_REF_TARGET,
        IMAGE_INFO_THUMBNAIL,
        IMAGE_INFO_UNSUPPORTED,
        IMAGE_DOWNLOAD_FILE,
        IMAGE_DOWNLOAD_REF_TARGET,
        IMAGE_DOWNLOAD_THUMBNAIL,
        IMAGE_DOWNLOAD_UNSUPPORTED,
        GCHAT_IMAGE_FILE,
        GCHAT_IMAGE_THUMBNAIL,
        NOT_FOUND
    }

    public static final class Selection {
        private final SelectionStatus status;
        private final File file;

        private Selection(SelectionStatus status, File file) {
            this.status = status;
            this.file = file;
        }

        static Selection of(SelectionStatus status, File file) {
            return new Selection(status, file);
        }

        public SelectionStatus status() {
            return status;
        }

        public File file() {
            return file;
        }

        public boolean hasFile() {
            return MediaFiles.isExistingFile(file);
        }
    }

    public static final class Request {
        private final int type;
        private final String mediaHint;
        private final Long msgId;
        private final Long msgSvrId;
        private final long createTime;
        private final String talker;
        private final String content;
        private final String emojiMd5;
        private final long chatRecordId;

        public Request(
                int type,
                String mediaHint,
                Long msgId,
                Long msgSvrId,
                long createTime,
                String talker,
                String content,
                String emojiMd5,
                long chatRecordId) {
            this.type = type;
            this.mediaHint = mediaHint;
            this.msgId = msgId;
            this.msgSvrId = msgSvrId;
            this.createTime = createTime;
            this.talker = talker;
            this.content = content;
            this.emojiMd5 = emojiMd5;
            this.chatRecordId = chatRecordId;
        }

        public int type() {
            return type;
        }

        public String mediaHint() {
            return mediaHint;
        }

        public Long msgId() {
            return msgId;
        }

        public Long msgSvrId() {
            return msgSvrId;
        }

        public long createTime() {
            return createTime;
        }

        public String talker() {
            return talker;
        }

        public String content() {
            return content;
        }

        public String emojiMd5() {
            return emojiMd5;
        }

        public long chatRecordId() {
            return chatRecordId;
        }
    }

    private final MediaSelectionBaseCandidate baseCandidate;
    private final WechatImageSelectionStrategy imageSelectionStrategy;
    private final NonImageMediaSelectionStrategy nonImageSelectionStrategy;
    private final Logger logger;

    public MediaFileSelector(
            BaseResolver baseResolver,
            ImageDownloadResolver imageDownloadResolver,
            EmojiResolver emojiResolver,
            GchatImageResolver gchatImageResolver,
            Logger logger) {
        this.baseCandidate = new MediaSelectionBaseCandidate(baseResolver);
        this.imageSelectionStrategy = new WechatImageSelectionStrategy(imageDownloadResolver, gchatImageResolver, logger);
        this.nonImageSelectionStrategy = new NonImageMediaSelectionStrategy(emojiResolver);
        this.logger = logger;
    }

    public File select(Request request) {
        return selectDetailed(request).file();
    }

    public Selection selectDetailed(Request request) {
        if (request == null || !MediaFiles.isSupportedMessageType(request.type())) {
            return Selection.of(SelectionStatus.UNSUPPORTED_TYPE, null);
        }
        ImageDownloadResolution.Candidate candidate = baseCandidate.resolve(request);
        if (MediaFiles.isImageMessageType(request.type())) {
            Selection imageSelection = imageSelectionStrategy.select(request, candidate);
            if (imageSelection != null) {
                return imageSelection;
            }
            logMiss(request);
            return Selection.of(SelectionStatus.NOT_FOUND, null);
        }
        Selection nonImageSelection = nonImageSelectionStrategy.select(request, candidate);
        if (nonImageSelection != null) {
            return nonImageSelection;
        }
        logMiss(request);
        return Selection.of(SelectionStatus.NOT_FOUND, null);
    }

    private void logMiss(Request request) {
        if (MediaFiles.shouldLogMissingMedia(request.type())) {
            log("media file not found type=" + request.type()
                    + " msgId=" + request.chatRecordId()
                    + " hintPresent=" + !isBlank(request.mediaHint()));
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
