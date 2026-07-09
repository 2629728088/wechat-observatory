package cc.wechat.observatory.media;

final class WechatImageSelectionStrategy {
    private final WechatImageBaseSelection baseSelection;
    private final WechatImageDeferredSelection deferredSelection;

    WechatImageSelectionStrategy(
            MediaFileSelector.ImageDownloadResolver imageDownloadResolver,
            MediaFileSelector.GchatImageResolver gchatImageResolver,
            MediaFileSelector.Logger logger) {
        this.baseSelection = new WechatImageBaseSelection(logger);
        this.deferredSelection = new WechatImageDeferredSelection(
                imageDownloadResolver,
                gchatImageResolver,
                logger);
    }

    MediaFileSelector.Selection select(
            MediaFileSelector.Request request,
            ImageDownloadResolution.Candidate baseCandidate) {
        if (!isImageRequest(request)) {
            return null;
        }
        WechatImageBaseSelection.Result baseResult = baseSelection.select(request, baseCandidate);
        MediaFileSelector.Selection immediateBaseSelection = baseResult.immediateSelection();
        if (immediateBaseSelection != null) {
            return immediateBaseSelection;
        }
        return deferredSelection.select(request, baseResult);
    }

    private static boolean isImageRequest(MediaFileSelector.Request request) {
        return request != null && MediaFiles.isImageMessageType(request.type());
    }
}
