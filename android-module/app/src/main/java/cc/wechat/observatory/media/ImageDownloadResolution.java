package cc.wechat.observatory.media;

import java.io.File;

public final class ImageDownloadResolution {
    public enum Status {
        IMAGE_INFO_FILE,
        IMAGE_INFO_REF_TARGET,
        IMAGE_INFO_THUMBNAIL,
        IMAGE_INFO_UNSUPPORTED,
        DOWNLOADED_FILE,
        DOWNLOADED_REF_TARGET,
        DOWNLOADED_THUMBNAIL,
        DOWNLOADED_UNSUPPORTED,
        NOT_READY
    }

    public enum CandidateKind {
        MISSING,
        IMAGE_FILE,
        IMAGE_REF_TARGET,
        LOW_QUALITY_THUMBNAIL,
        UNSUPPORTED
    }

    public static final class Candidate {
        private final CandidateKind kind;
        private final File file;

        private Candidate(CandidateKind kind, File file) {
            this.kind = kind;
            this.file = file;
        }

        public static Candidate missing() {
            return new Candidate(CandidateKind.MISSING, null);
        }

        public static Candidate fromFile(File file) {
            if (!MediaFiles.isExistingFile(file)) {
                return missing();
            }
            if (WechatImageFiles.isLowQualityThumbnailFile(file)) {
                return new Candidate(CandidateKind.LOW_QUALITY_THUMBNAIL, file);
            }
            return new Candidate(CandidateKind.IMAGE_FILE, file);
        }

        public static Candidate refTarget(File file) {
            if (!MediaFiles.isExistingFile(file)) {
                return missing();
            }
            return new Candidate(CandidateKind.IMAGE_REF_TARGET, file);
        }

        public static Candidate unsupported(File file) {
            if (!MediaFiles.isExistingFile(file)) {
                return missing();
            }
            return new Candidate(CandidateKind.UNSUPPORTED, file);
        }

        public boolean isMissing() {
            return kind == CandidateKind.MISSING;
        }

        public boolean isImageFile() {
            return kind == CandidateKind.IMAGE_FILE;
        }

        public boolean isReferenceTarget() {
            return kind == CandidateKind.IMAGE_REF_TARGET;
        }

        public boolean isLowQualityThumbnail() {
            return kind == CandidateKind.LOW_QUALITY_THUMBNAIL;
        }

        public boolean isUnsupported() {
            return kind == CandidateKind.UNSUPPORTED;
        }

        public boolean hasExistingFile() {
            return MediaFiles.isExistingFile(file);
        }

        public File file() {
            return file;
        }
    }

    private final Status status;
    private final File file;
    private final String logMessage;

    ImageDownloadResolution(Status status, File file, String logMessage) {
        this.status = status;
        this.file = file;
        this.logMessage = logMessage;
    }

    public Status status() {
        return status;
    }

    public File file() {
        return file;
    }

    public String logMessage() {
        return logMessage;
    }

    public boolean hasSelectableFile() {
        return ImageDownloadResolutionStatus.hasSelectableFile(status, file);
    }

    public boolean isReferenceTarget() {
        return ImageDownloadResolutionStatus.isReferenceTarget(status);
    }

    public boolean isImageInfoSource() {
        return ImageDownloadResolutionStatus.isImageInfoSource(status);
    }

    public boolean isDownloadedFallbackSource() {
        return ImageDownloadResolutionStatus.isDownloadedFallbackSource(status);
    }

    public boolean isThumbnailOnly() {
        return ImageDownloadResolutionStatus.isThumbnailOnly(status);
    }

    public boolean isUnsupported() {
        return ImageDownloadResolutionStatus.isUnsupported(status);
    }

    public static ImageDownloadResolution evaluate(
            long localId,
            long serverId,
            boolean requestedDownload,
            File imageInfoFile,
            File downloadedFallbackFile) {
        return ImageDownloadResolutionEvaluator.evaluateFiles(
                localId,
                serverId,
                requestedDownload,
                imageInfoFile,
                downloadedFallbackFile);
    }

    public static ImageDownloadResolution evaluateCandidates(
            long localId,
            long serverId,
            boolean requestedDownload,
            Candidate imageInfoCandidate,
            Candidate downloadedFallbackCandidate) {
        return ImageDownloadResolutionEvaluator.evaluateCandidates(
                localId,
                serverId,
                requestedDownload,
                imageInfoCandidate,
                downloadedFallbackCandidate);
    }

    static ImageDownloadResolution evaluateCandidateSet(
            long localId,
            long serverId,
            ImageDownloadCandidateSet candidates) {
        return ImageDownloadResolutionEvaluator.evaluateCandidateSet(
                localId,
                serverId,
                candidates);
    }
}
