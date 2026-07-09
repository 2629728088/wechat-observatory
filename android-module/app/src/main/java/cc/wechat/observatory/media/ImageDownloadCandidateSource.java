package cc.wechat.observatory.media;

import java.io.File;

final class ImageDownloadCandidateSource {
    private enum Kind {
        MISSING,
        IMAGE_FILE,
        REF_TARGET,
        THUMBNAIL,
        UNSUPPORTED
    }

    private final Kind kind;
    private final File source;
    private final File file;

    private ImageDownloadCandidateSource(Kind kind, File source, File file) {
        this.kind = kind == null ? Kind.MISSING : kind;
        this.source = source;
        this.file = file;
    }

    static ImageDownloadCandidateSource fromMediaResult(MediaResolver.Result result) {
        if (result == null) {
            return missing();
        }
        return fromResolvedImageCandidate(
                result.source(),
                result.file(),
                result.isImageThumbnail(),
                result.isImageUnsupported(),
                result.isImageReferenceTarget());
    }

    static ImageDownloadCandidateSource fromImageInfoResult(MediaResolver.ImageInfoResult result) {
        if (result == null) {
            return missing();
        }
        return fromResolvedImageCandidate(
                result.source(),
                result.file(),
                result.isImageThumbnail(),
                result.isImageUnsupported(),
                result.isImageReferenceTarget());
    }

    static ImageDownloadCandidateSource missing() {
        return new ImageDownloadCandidateSource(Kind.MISSING, null, null);
    }

    private static ImageDownloadCandidateSource imageFile(File file) {
        if (file == null) {
            return missing();
        }
        return new ImageDownloadCandidateSource(Kind.IMAGE_FILE, file, file);
    }

    private static ImageDownloadCandidateSource fromResolvedImageCandidate(
            File source,
            File file,
            boolean thumbnail,
            boolean unsupported,
            boolean referenceTarget) {
        if (thumbnail) {
            return thumbnail(source);
        }
        if (unsupported) {
            return unsupported(source);
        }
        if (referenceTarget) {
            return refTarget(source, file);
        }
        return imageFile(file);
    }

    private static ImageDownloadCandidateSource refTarget(File source, File file) {
        if (file == null) {
            return missing();
        }
        return new ImageDownloadCandidateSource(Kind.REF_TARGET, source, file);
    }

    private static ImageDownloadCandidateSource thumbnail(File source) {
        if (source == null) {
            return missing();
        }
        return new ImageDownloadCandidateSource(Kind.THUMBNAIL, source, null);
    }

    private static ImageDownloadCandidateSource unsupported(File source) {
        if (source == null) {
            return missing();
        }
        return new ImageDownloadCandidateSource(Kind.UNSUPPORTED, source, null);
    }

    boolean isMissing() {
        return kind == Kind.MISSING;
    }

    boolean isImageFile() {
        return kind == Kind.IMAGE_FILE;
    }

    boolean isReferenceTarget() {
        return kind == Kind.REF_TARGET;
    }

    boolean isThumbnail() {
        return kind == Kind.THUMBNAIL;
    }

    boolean isUnsupported() {
        return kind == Kind.UNSUPPORTED;
    }

    File source() {
        return source;
    }

    File file() {
        return file;
    }
}
