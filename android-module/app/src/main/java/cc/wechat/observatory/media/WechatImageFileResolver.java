package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

final class WechatImageFileResolver {
    interface Logger {
        void log(String message);
    }

    enum CandidateKind {
        MISSING,
        REAL_IMAGE,
        LOW_QUALITY_THUMBNAIL,
        REF_TARGET,
        UNSUPPORTED
    }

    abstract static class CandidateResult {
        private final CandidateKind kind;
        private final File source;
        private final File file;

        CandidateResult(CandidateKind kind, File source, File file) {
            this.kind = kind == null ? CandidateKind.MISSING : kind;
            this.source = source;
            this.file = file;
        }

        CandidateKind kind() {
            return kind;
        }

        boolean hasResolvedFile() {
            return file != null;
        }

        boolean isMissing() {
            return kind == CandidateKind.MISSING;
        }

        boolean isRealImage() {
            return kind == CandidateKind.REAL_IMAGE;
        }

        boolean isLowQualityThumbnail() {
            return kind == CandidateKind.LOW_QUALITY_THUMBNAIL;
        }

        boolean isReferenceTarget() {
            return kind == CandidateKind.REF_TARGET;
        }

        boolean isUnsupported() {
            return kind == CandidateKind.UNSUPPORTED;
        }

        File source() {
            return source;
        }

        File file() {
            return file;
        }
    }

    static final class CandidateResolution extends CandidateResult {
        private CandidateResolution(CandidateKind kind, File source, File file) {
            super(kind, source, file);
        }

        static CandidateResolution missing(File source) {
            return new CandidateResolution(CandidateKind.MISSING, source, null);
        }

        static CandidateResolution realImage(File source) {
            return new CandidateResolution(CandidateKind.REAL_IMAGE, source, source);
        }

        static CandidateResolution lowQualityThumbnail(File source) {
            return new CandidateResolution(CandidateKind.LOW_QUALITY_THUMBNAIL, source, null);
        }

        static CandidateResolution refTarget(File source, File file) {
            return new CandidateResolution(CandidateKind.REF_TARGET, source, file);
        }

        static CandidateResolution unsupported(File source) {
            return new CandidateResolution(CandidateKind.UNSUPPORTED, source, null);
        }
    }

    static final class ProfileSearchResult extends CandidateResult {
        private ProfileSearchResult(CandidateKind kind, File source, File file) {
            super(kind, source, file);
        }

        static ProfileSearchResult missing() {
            return new ProfileSearchResult(CandidateKind.MISSING, null, null);
        }

        static ProfileSearchResult from(CandidateResolution resolution) {
            if (resolution == null) {
                return missing();
            }
            return new ProfileSearchResult(resolution.kind(), resolution.source(), resolution.file());
        }

    }

    private WechatImageFileResolver() {
    }

    static ProfileSearchResult findInProfileRootsDetails(File profileRoot, String[] roots, List<String> names, Logger logger) {
        return WechatImageNamedFileFinder.findInProfileRootsDetails(profileRoot, roots, names, logger);
    }

    static CandidateResolution resolveCandidateDetails(File file, Logger logger) {
        return WechatImageCandidateResolver.resolve(file, logger);
    }
}
