package cc.wechat.observatory.media;

import java.io.File;

final class WechatVoiceFileResolver {
    interface Logger {
        void log(String message);
    }

    private static final int VOICE_FILE_VISIT_LIMIT = 8000;

    private WechatVoiceFileResolver() {
    }

    static File findRecent(File microMsgRoot, long createTimeSeconds, Logger logger) {
        File[] profiles = microMsgRoot == null ? null : microMsgRoot.listFiles();
        if (profiles == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long targetMs = createTimeSeconds > 0L ? createTimeSeconds * 1000L : now;
        long minMs = Math.max(0L, targetMs - 10L * 60L * 1000L);
        long maxMs = targetMs + 10L * 60L * 1000L;
        RecentVoiceCandidate best = new RecentVoiceCandidate();
        int[] visited = new int[]{0};
        for (File profile : profiles) {
            if (profile == null || !profile.isDirectory()) {
                continue;
            }
            findRecent(new File(profile, "voice2"), minMs, maxMs, targetMs, 6, visited, best);
        }
        if (best.file != null) {
            log(logger, "voice media fallback selected file=" + best.file.getName()
                    + " size=" + best.file.length()
                    + " modified=" + best.modifiedMs
                    + " distanceMs=" + best.distanceMs);
        }
        return best.file;
    }

    private static void findRecent(
            File root,
            long minMs,
            long maxMs,
            long targetMs,
            int depth,
            int[] visited,
            RecentVoiceCandidate best) {
        if (root == null || !root.isDirectory() || depth < 0 || visited[0] > VOICE_FILE_VISIT_LIMIT) {
            return;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!MediaFiles.isExistingFile(file)) {
                continue;
            }
            visited[0]++;
            if (!MediaFiles.isLikelyVoiceMediaFile(file)) {
                continue;
            }
            long modified = file.lastModified();
            if (modified < minMs || modified > maxMs) {
                continue;
            }
            long distance = Math.abs(modified - targetMs);
            if (best.file == null
                    || distance < best.distanceMs
                    || (distance == best.distanceMs && modified > best.modifiedMs)) {
                best.file = file;
                best.distanceMs = distance;
                best.modifiedMs = modified;
            }
        }
        for (File file : files) {
            if (file != null && file.isDirectory()) {
                findRecent(file, minMs, maxMs, targetMs, depth - 1, visited, best);
            }
        }
    }

    private static void log(Logger logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

    private static final class RecentVoiceCandidate {
        File file;
        long distanceMs = Long.MAX_VALUE;
        long modifiedMs = 0L;
    }
}
