package cc.wechat.observatory.media;

import java.io.File;

final class WechatImageCandidateResolver {
    private WechatImageCandidateResolver() {
    }

    static WechatImageFileResolver.CandidateResolution resolve(
            File file,
            WechatImageFileResolver.Logger logger) {
        switch (WechatImageCandidateClassifier.classify(file)) {
            case MISSING:
                return WechatImageFileResolver.CandidateResolution.missing(file);
            case REFERENCE_POINTER:
                return resolveReference(file, logger);
            case LOW_QUALITY_THUMBNAIL:
                log(logger, "skip thumbnail image candidate file=" + file.getName() + " size=" + file.length());
                return WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(file);
            case REAL_IMAGE:
                return WechatImageFileResolver.CandidateResolution.realImage(file);
            case UNSUPPORTED:
            default:
                return WechatImageFileResolver.CandidateResolution.unsupported(file);
        }
    }

    private static WechatImageFileResolver.CandidateResolution resolveReference(
            File pointer,
            WechatImageFileResolver.Logger logger) {
        WechatImageReferenceResolver.Result result =
                WechatImageReferenceResolver.resolve(
                        pointer,
                        WechatImageLoggerAdapters.referenceLogger(logger));
        if (result == null || !result.found()) {
            logReferenceMiss(logger, pointer, result);
            return WechatImageFileResolver.CandidateResolution.unsupported(pointer);
        }
        return WechatImageFileResolver.CandidateResolution.refTarget(result.pointer(), result.target());
    }

    private static void logReferenceMiss(
            WechatImageFileResolver.Logger logger,
            File pointer,
            WechatImageReferenceResolver.Result result) {
        if (logger == null || pointer == null) {
            return;
        }
        String status = result == null || result.status() == null
                ? "UNKNOWN"
                : result.status().name();
        logger.log("image ref unresolved status=" + status + " pointer=" + pointer.getName());
    }

    private static void log(WechatImageFileResolver.Logger logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
