package cc.wechat.observatory.media;

final class WechatImageDeferredSelection {
    private final MediaFileSelector.ImageDownloadResolver imageDownloadResolver;
    private final WechatGchatImageSelection gchatImageSelection;
    private final MediaFileSelector.Logger logger;

    WechatImageDeferredSelection(
            MediaFileSelector.ImageDownloadResolver imageDownloadResolver,
            MediaFileSelector.GchatImageResolver gchatImageResolver,
            MediaFileSelector.Logger logger) {
        this.imageDownloadResolver = imageDownloadResolver;
        this.gchatImageSelection = new WechatGchatImageSelection(gchatImageResolver, logger);
        this.logger = logger;
    }

    MediaFileSelector.Selection select(
            MediaFileSelector.Request request,
            WechatImageBaseSelection.Result baseResult) {
        MediaFileSelector.Selection downloadSelection = selectDownloaded(request);
        if (hasFile(downloadSelection)) {
            return downloadSelection;
        }

        MediaFileSelector.Selection gchatSelection = gchatImageSelection.select(request);
        if (hasFile(gchatSelection)) {
            return gchatSelection;
        }

        if (downloadSelection != null) {
            return downloadSelection;
        }
        if (gchatSelection != null) {
            return gchatSelection;
        }
        return baseResult == null ? null : baseResult.unsupportedFallback();
    }

    private MediaFileSelector.Selection selectDownloaded(MediaFileSelector.Request request) {
        if (imageDownloadResolver == null || request == null) {
            return null;
        }
        ImageDownloadResolution resolution = imageDownloadResolver.resolve(
                request.mediaHint(),
                request.msgId(),
                request.msgSvrId(),
                request.createTime(),
                request.talker());
        return WechatImageDownloadSelection.from(request, resolution, logger);
    }

    private static boolean hasFile(MediaFileSelector.Selection selection) {
        return selection != null && selection.hasFile();
    }
}
