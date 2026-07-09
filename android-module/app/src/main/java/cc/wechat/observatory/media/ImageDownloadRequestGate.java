package cc.wechat.observatory.media;

final class ImageDownloadRequestGate {
    static final class Decision {
        private final long localId;
        private final long serverId;
        private final boolean canResolve;
        private final boolean shouldRequestDownload;

        private Decision(
                long localId,
                long serverId,
                boolean canResolve,
                boolean shouldRequestDownload) {
            this.localId = localId;
            this.serverId = serverId;
            this.canResolve = canResolve;
            this.shouldRequestDownload = shouldRequestDownload;
        }

        long localId() {
            return localId;
        }

        long serverId() {
            return serverId;
        }

        boolean canResolve() {
            return canResolve;
        }

        boolean shouldRequestDownload() {
            return shouldRequestDownload;
        }
    }

    private final ImageDownloadRequestTracker tracker;

    ImageDownloadRequestGate(ImageDownloadRequestTracker tracker) {
        this.tracker = tracker;
    }

    Decision evaluate(Long msgId, Long msgSvrId) {
        long localId = msgId == null ? 0L : msgId.longValue();
        long serverId = msgSvrId == null ? 0L : msgSvrId.longValue();
        long requestId = ImageDownloadRequestTracker.requestId(localId, serverId);
        if (requestId <= 0L) {
            return new Decision(localId, serverId, false, false);
        }
        boolean shouldRequestDownload = localId > 0L
                && tracker != null
                && tracker.remember(requestId);
        return new Decision(localId, serverId, true, shouldRequestDownload);
    }
}
