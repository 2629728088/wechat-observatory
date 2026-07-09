package cc.wechat.observatory.media;

import java.io.File;

import static cc.wechat.observatory.util.Strings.isBlank;

final class MediaDirectFileResolver {
    private MediaDirectFileResolver() {
    }

    static File resolve(File appRoot, String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = MediaSearchPlan.normalizeHint(value);
        String[] candidates = new String[]{value.trim(), normalized};
        for (String candidate : candidates) {
            File resolved = resolveCandidate(appRoot, candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static File resolveCandidate(File appRoot, String candidate) {
        if (isBlank(candidate)) {
            return null;
        }
        File direct = new File(candidate);
        if (MediaFiles.isExistingFile(direct)) {
            return direct;
        }
        if (appRoot == null) {
            return null;
        }
        File relative = new File(appRoot, candidate);
        return MediaFiles.isExistingFile(relative) ? relative : null;
    }
}
