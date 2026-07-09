package cc.wechat.observatory.media;

final class WechatImageBaseSelection {
    static final class Result {
        private final MediaFileSelector.Selection immediateSelection;
        private final MediaFileSelector.Selection unsupportedFallback;

        private Result(
                MediaFileSelector.Selection immediateSelection,
                MediaFileSelector.Selection unsupportedFallback) {
            this.immediateSelection = immediateSelection;
            this.unsupportedFallback = unsupportedFallback;
        }

        MediaFileSelector.Selection immediateSelection() {
            return immediateSelection;
        }

        MediaFileSelector.Selection unsupportedFallback() {
            return unsupportedFallback;
        }
    }

    private final MediaFileSelector.Logger logger;

    WechatImageBaseSelection(MediaFileSelector.Logger logger) {
        this.logger = logger;
    }

    Result select(
            MediaFileSelector.Request request,
            ImageDownloadResolution.Candidate candidate) {
        WechatImageBaseCandidateDecision decision = WechatImageBaseCandidateDecision.from(candidate);
        logDeferredCandidate(request, decision);
        return new Result(
                decision.selection(),
                decision.unsupportedFallbackSelection());
    }

    private void logDeferredCandidate(
            MediaFileSelector.Request request,
            WechatImageBaseCandidateDecision decision) {
        if (request == null || decision == null || decision.file() == null) {
            return;
        }
        if (decision.isThumbnail()) {
            log("image media hint resolved thumbnail only, requesting full image msgId=" + request.chatRecordId()
                    + " file=" + decision.file().getName()
                    + " size=" + decision.file().length());
        }
        if (decision.isUnsupported()) {
            log("image media hint resolved unsupported candidate, requesting full image msgId=" + request.chatRecordId()
                    + " file=" + decision.file().getName());
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
