package cc.wechat.observatory.media;

import java.io.File;

import static cc.wechat.observatory.util.Strings.isBlank;

final class WechatImageBucketCandidateResolver {
    private WechatImageBucketCandidateResolver() {
    }

    static WechatImageFileResolver.CandidateResolution resolve(
            File bucket,
            String fileName,
            WechatImageFileResolver.Logger logger) {
        if (bucket == null || isBlank(fileName)) {
            return WechatImageFileResolver.CandidateResolution.missing(null);
        }
        WechatImageFileResolver.CandidateResolution referenced = resolveReference(bucket, fileName, logger);
        if (referenced.hasResolvedFile()) {
            return referenced;
        }
        WechatImageFileResolver.CandidateResolution exact = resolveExact(bucket, fileName, logger);
        return selectAfterReferenceMiss(referenced, exact);
    }

    static WechatImageFileResolver.CandidateResolution resolveReference(
            File bucket,
            String fileName,
            WechatImageFileResolver.Logger logger) {
        if (bucket == null || isBlank(fileName)) {
            return WechatImageFileResolver.CandidateResolution.missing(null);
        }
        WechatImageFileResolver.CandidateResolution bestMiss =
                WechatImageFileResolver.CandidateResolution.missing(null);
        for (String pointerName : WechatImageSearchPlan.referencePointerNames(fileName)) {
            WechatImageFileResolver.CandidateResolution referenced =
                    resolveReferencePointer(bucket, pointerName, logger);
            if (referenced.hasResolvedFile()) {
                return referenced;
            }
            bestMiss = WechatImageMissPriority.firstNonMissing(
                    bestMiss,
                    normalizeReferenceMiss(referenced));
        }
        return bestMiss;
    }

    private static WechatImageFileResolver.CandidateResolution resolveReferencePointer(
            File bucket,
            String pointerName,
            WechatImageFileResolver.Logger logger) {
        return WechatImageFileResolver.resolveCandidateDetails(new File(bucket, pointerName), logger);
    }

    private static WechatImageFileResolver.CandidateResolution resolveExact(
            File bucket,
            String fileName,
            WechatImageFileResolver.Logger logger) {
        return WechatImageFileResolver.resolveCandidateDetails(new File(bucket, fileName), logger);
    }

    private static WechatImageFileResolver.CandidateResolution selectAfterReferenceMiss(
            WechatImageFileResolver.CandidateResolution referenced,
            WechatImageFileResolver.CandidateResolution exact) {
        if (exact == null || exact.isMissing()) {
            return referenced == null
                    ? WechatImageFileResolver.CandidateResolution.missing(null)
                    : referenced;
        }
        return exact;
    }

    private static WechatImageFileResolver.CandidateResolution normalizeReferenceMiss(
            WechatImageFileResolver.CandidateResolution referenced) {
        if (referenced == null) {
            return WechatImageFileResolver.CandidateResolution.missing(null);
        }
        if (referenced.isReferenceTarget() || referenced.isUnsupported()) {
            return referenced;
        }
        return WechatImageFileResolver.CandidateResolution.missing(referenced.source());
    }
}
