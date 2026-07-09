package cc.wechat.observatory.media;

final class ImageDownloadRequestAttempt {
    private final ImageDownloadCoordinator.Downloader downloader;

    ImageDownloadRequestAttempt(ImageDownloadCoordinator.Downloader downloader) {
        this.downloader = downloader;
    }

    boolean request(ImageDownloadRequestGate.Decision requestDecision, String talker) {
        if (requestDecision == null || !requestDecision.shouldRequestDownload() || downloader == null) {
            return false;
        }
        return downloader.request(requestDecision.localId(), requestDecision.serverId(), talker);
    }
}
