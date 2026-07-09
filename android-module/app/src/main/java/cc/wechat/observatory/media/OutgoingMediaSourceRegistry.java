package cc.wechat.observatory.media;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class OutgoingMediaSourceRegistry {
    private static final long MAX_AGE_MS = 2L * 60L * 1000L;
    private static final int MAX_ENTRIES = 64;

    private final List<Entry> entries = new ArrayList<>();

    public synchronized void rememberPending(String talker, int type, File file, long afterMsgId) {
        if (isBlank(talker) || !MediaFiles.isSupportedMessageType(type) || !MediaFiles.isExistingFile(file)) {
            return;
        }
        pruneExpired(System.currentTimeMillis());
        entries.add(new Entry(talker.trim(), type, file, Math.max(0L, afterMsgId), 0L));
        trim();
    }

    public synchronized void bind(String talker, int type, long msgId, File file) {
        if (isBlank(talker) || msgId <= 0L || !MediaFiles.isSupportedMessageType(type)
                || !MediaFiles.isExistingFile(file)) {
            return;
        }
        String normalizedTalker = talker.trim();
        long now = System.currentTimeMillis();
        pruneExpired(now);
        for (Entry entry : entries) {
            if (entry.matchesSource(normalizedTalker, type, file)) {
                entry.msgId = msgId;
                entry.createdAtMs = now;
                return;
            }
        }
        entries.add(new Entry(normalizedTalker, type, file, 0L, msgId));
        trim();
    }

    public synchronized String resolveHint(
            Integer isSend,
            int type,
            String talker,
            Long msgId,
            Long msgSvrId,
            String mediaHint) {
        if (!isOutgoingMediaCandidate(isSend, type, talker, msgId, msgSvrId)) {
            return mediaHint;
        }
        long now = System.currentTimeMillis();
        pruneExpired(now);
        String normalizedTalker = talker.trim();
        long localId = msgId.longValue();
        for (Entry entry : entries) {
            if (entry.matchesMessage(normalizedTalker, type, localId)) {
                entry.msgId = localId;
                entry.createdAtMs = now;
                return entry.file.getAbsolutePath();
            }
        }
        return mediaHint;
    }

    private static boolean isOutgoingMediaCandidate(
            Integer isSend,
            int type,
            String talker,
            Long msgId,
            Long msgSvrId) {
        return isSend != null
                && isSend.intValue() == 1
                && MediaFiles.isSupportedMessageType(type)
                && !isBlank(talker)
                && msgId != null
                && msgId.longValue() > 0L
                && (msgSvrId == null || msgSvrId.longValue() <= 0L);
    }

    private void pruneExpired(long now) {
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (now - entry.createdAtMs > MAX_AGE_MS || !MediaFiles.isExistingFile(entry.file)) {
                iterator.remove();
            }
        }
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    private static final class Entry {
        private final String talker;
        private final int type;
        private final File file;
        private final long afterMsgId;
        private long msgId;
        private long createdAtMs;

        Entry(String talker, int type, File file, long afterMsgId, long msgId) {
            this.talker = talker;
            this.type = type;
            this.file = file;
            this.afterMsgId = afterMsgId;
            this.msgId = msgId;
            this.createdAtMs = System.currentTimeMillis();
        }

        boolean matchesSource(String expectedTalker, int expectedType, File expectedFile) {
            return type == expectedType
                    && talker.equals(expectedTalker)
                    && sameFile(file, expectedFile);
        }

        boolean matchesMessage(String expectedTalker, int expectedType, long localMsgId) {
            if (type != expectedType || !talker.equals(expectedTalker)) {
                return false;
            }
            if (msgId > 0L) {
                return msgId == localMsgId;
            }
            return localMsgId > afterMsgId;
        }

        private static boolean sameFile(File first, File second) {
            if (first == null || second == null) {
                return false;
            }
            return first.getAbsolutePath().equals(second.getAbsolutePath());
        }
    }
}
