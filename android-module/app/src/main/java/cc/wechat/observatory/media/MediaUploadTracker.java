package cc.wechat.observatory.media;

import java.util.HashSet;
import java.util.Set;

public final class MediaUploadTracker {
    private final int capacity;
    private final Set<Long> scheduledRetryIds = new HashSet<>();
    private final Set<Long> uploadedIds = new HashSet<>();

    public MediaUploadTracker(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized boolean markRetryScheduled(long chatRecordId) {
        if (chatRecordId <= 0L) {
            return false;
        }
        trimIfNeeded(scheduledRetryIds);
        if (scheduledRetryIds.contains(chatRecordId)) {
            return false;
        }
        scheduledRetryIds.add(chatRecordId);
        return true;
    }

    public synchronized void rememberUploaded(long chatRecordId) {
        if (chatRecordId <= 0L) {
            return;
        }
        trimIfNeeded(uploadedIds);
        uploadedIds.add(chatRecordId);
    }

    public synchronized boolean hasUploaded(long chatRecordId) {
        return chatRecordId > 0L && uploadedIds.contains(chatRecordId);
    }

    private void trimIfNeeded(Set<Long> ids) {
        if (ids.size() > capacity) {
            ids.clear();
        }
    }
}
