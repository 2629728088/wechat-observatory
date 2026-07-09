package cc.wechat.observatory.media;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MediaResolver {
    public interface Logger {
        void log(String message);
    }

    public enum ResolutionStatus {
        NOT_FOUND,
        DIRECT_MEDIA_FILE,
        DIRECT_IMAGE_FILE,
        DIRECT_IMAGE_REF_TARGET,
        DIRECT_IMAGE_THUMBNAIL,
        DIRECT_IMAGE_UNSUPPORTED,
        PROFILE_MEDIA_FILE,
        PROFILE_IMAGE_FILE,
        PROFILE_IMAGE_REF_TARGET,
        PROFILE_IMAGE_THUMBNAIL,
        PROFILE_IMAGE_UNSUPPORTED,
        EMOJI_MEDIA_FILE,
        EMOJI_DIAGNOSTIC_NEEDED,
        VOICE_RECENT_FILE,
        VIDEO_CACHE_FILE
    }

    public enum ImageInfoStatus {
        MISSING_INPUT,
        DIRECT_IMAGE_FILE,
        DIRECT_IMAGE_REF_TARGET,
        DIRECT_IMAGE_THUMBNAIL,
        DIRECT_IMAGE_UNSUPPORTED,
        PROFILE_IMAGE_FILE,
        PROFILE_IMAGE_REF_TARGET,
        PROFILE_IMAGE_THUMBNAIL,
        PROFILE_IMAGE_UNSUPPORTED,
        NO_CANDIDATE_NAMES,
        CANDIDATES_NOT_FOUND
    }

    public static final class Result {
        private final ResolutionStatus status;
        private final File file;
        private final File source;
        private final boolean emojiDiagnosticNeeded;

        private Result(ResolutionStatus status, File file, File source, boolean emojiDiagnosticNeeded) {
            this.status = status;
            this.file = file;
            this.source = source;
            this.emojiDiagnosticNeeded = emojiDiagnosticNeeded;
        }

        public static Result found(File file) {
            return found(ResolutionStatus.DIRECT_MEDIA_FILE, file);
        }

        public static Result found(ResolutionStatus status, File file) {
            return found(status, file, file);
        }

        static Result found(ResolutionStatus status, File file, File source) {
            if (file == null) {
                return notFound();
            }
            return new Result(status, file, source == null ? file : source, false);
        }

        public static Result thumbnail(ResolutionStatus status, File source) {
            if (source == null) {
                return notFound();
            }
            return new Result(status, null, source, false);
        }

        public static Result unsupported(ResolutionStatus status, File source) {
            if (source == null) {
                return notFound();
            }
            return new Result(status, null, source, false);
        }

        public static Result notFound() {
            return new Result(ResolutionStatus.NOT_FOUND, null, null, false);
        }

        public static Result emojiDiagnosticNeeded() {
            return new Result(ResolutionStatus.EMOJI_DIAGNOSTIC_NEEDED, null, null, true);
        }

        public ResolutionStatus status() {
            return status;
        }

        public File file() {
            return file;
        }

        public File source() {
            return source;
        }

        public boolean needsEmojiDiagnostic() {
            return emojiDiagnosticNeeded;
        }

        public boolean isNotFound() {
            return status == ResolutionStatus.NOT_FOUND;
        }

        public boolean isImageThumbnail() {
            return status == ResolutionStatus.DIRECT_IMAGE_THUMBNAIL
                    || status == ResolutionStatus.PROFILE_IMAGE_THUMBNAIL;
        }

        public boolean isImageUnsupported() {
            return status == ResolutionStatus.DIRECT_IMAGE_UNSUPPORTED
                    || status == ResolutionStatus.PROFILE_IMAGE_UNSUPPORTED;
        }

        public boolean isImageReferenceTarget() {
            return status == ResolutionStatus.DIRECT_IMAGE_REF_TARGET
                    || status == ResolutionStatus.PROFILE_IMAGE_REF_TARGET;
        }
    }

    public static final class ImageInfoResult {
        private final ImageInfoStatus status;
        private final File file;
        private final File source;
        private final long localInfoId;
        private final List<String> candidateNames;
        private final List<String> fieldDebug;

        private ImageInfoResult(
                ImageInfoStatus status,
                File file,
                File source,
                long localInfoId,
                List<String> candidateNames,
                List<String> fieldDebug) {
            this.status = status;
            this.file = file;
            this.source = source;
            this.localInfoId = localInfoId;
            this.candidateNames = copy(candidateNames);
            this.fieldDebug = copy(fieldDebug);
        }

        public static ImageInfoResult missing() {
            return new ImageInfoResult(ImageInfoStatus.MISSING_INPUT, null, null, 0L, null, null);
        }

        static ImageInfoResult of(
                ImageInfoStatus status,
                File file,
                File source,
                long localInfoId,
                List<String> candidateNames,
                List<String> fieldDebug) {
            return new ImageInfoResult(status, file, source, localInfoId, candidateNames, fieldDebug);
        }

        public ImageInfoStatus status() {
            return status;
        }

        public File file() {
            return file;
        }

        public File source() {
            return source;
        }

        public long localInfoId() {
            return localInfoId;
        }

        public List<String> candidateNames() {
            return copy(candidateNames);
        }

        public List<String> fieldDebug() {
            return copy(fieldDebug);
        }

        public boolean isImageThumbnail() {
            return status == ImageInfoStatus.DIRECT_IMAGE_THUMBNAIL
                    || status == ImageInfoStatus.PROFILE_IMAGE_THUMBNAIL;
        }

        public boolean isImageUnsupported() {
            return status == ImageInfoStatus.DIRECT_IMAGE_UNSUPPORTED
                    || status == ImageInfoStatus.PROFILE_IMAGE_UNSUPPORTED;
        }

        public boolean isImageReferenceTarget() {
            return status == ImageInfoStatus.DIRECT_IMAGE_REF_TARGET
                    || status == ImageInfoStatus.PROFILE_IMAGE_REF_TARGET;
        }
    }

    private MediaResolver() {
    }

    public static Result resolveMediaFile(
            File appRoot,
            int type,
            String mediaHint,
            long createTime,
            String emojiMd5,
            Logger logger) {
        List<String> names = MediaSearchPlan.candidateNames(type, mediaHint, emojiMd5);
        if (MediaFiles.isImageMessageType(type)) {
            return resolveImageMediaFile(appRoot, mediaHint, names, logger);
        }
        return resolveNonImageMediaFile(appRoot, type, mediaHint, names, createTime, logger);
    }

    private static Result resolveImageMediaFile(
            File appRoot,
            String mediaHint,
            List<String> names,
            Logger logger) {
        return WechatImageMediaResolver.resolve(appRoot, mediaHint, names, logger);
    }

    private static Result resolveNonImageMediaFile(
            File appRoot,
            int type,
            String mediaHint,
            List<String> names,
            long createTime,
            Logger logger) {
        if (shouldSkipNonImageSearch(type, names)) {
            return Result.notFound();
        }
        Result direct = resolveDirectMediaHint(appRoot, mediaHint);
        if (!direct.isNotFound()) {
            return direct;
        }
        if (appRoot == null) {
            return Result.notFound();
        }
        return WechatNonImageMediaResolver.resolve(appRoot, type, names, createTime, logger);
    }

    private static boolean shouldSkipNonImageSearch(int type, List<String> names) {
        return (names == null || names.isEmpty()) && !MediaFiles.isVoiceMessageType(type);
    }

    private static Result resolveDirectMediaHint(File appRoot, String mediaHint) {
        File direct = MediaDirectFileResolver.resolve(appRoot, mediaHint);
        return direct == null
                ? Result.notFound()
                : Result.found(ResolutionStatus.DIRECT_MEDIA_FILE, direct);
    }

    public static ImageInfoResult resolveImageInfo(File appRoot, Object imageInfo, Logger logger) {
        return WechatImageInfoResolver.resolve(appRoot, imageInfo, logger);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<String>() : new ArrayList<String>(values);
    }
}
