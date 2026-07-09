package cc.wechat.observatory.media;

import java.util.HashSet;
import java.util.Set;

public final class ImageDownloadRequestTracker {
    private final int maxEntries;
    private final Set<Long> requestedIds = new HashSet<>();

    public ImageDownloadRequestTracker(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    public static long requestId(long localId, long serverId) {
        if (localId > 0L) {
            return localId;
        }
        if (serverId > 0L) {
            return serverId;
        }
        return 0L;
    }

    public synchronized boolean remember(long requestId) {
        if (requestId <= 0L) {
            return false;
        }
        if (requestedIds.size() > maxEntries) {
            requestedIds.clear();
        }
        if (requestedIds.contains(requestId)) {
            return false;
        }
        requestedIds.add(requestId);
        return true;
    }
}
