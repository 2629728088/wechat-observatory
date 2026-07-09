package cc.wechat.observatory.media;

import java.io.File;
import java.util.List;

import static cc.wechat.observatory.util.Strings.isBlank;

final class NamedMediaFileFinder {
    private static final int NAMED_FILE_VISIT_LIMIT = 6000;

    private NamedMediaFileFinder() {
    }

    static File findInProfileRoots(File profileRoot, String[] roots, List<String> names) {
        File[] profiles = MediaFileEntries.sortedChildren(profileRoot);
        if (profiles.length == 0) {
            return null;
        }
        for (File profile : profiles) {
            if (profile == null || !profile.isDirectory()) {
                continue;
            }
            for (String rootName : roots) {
                File found = findInRoot(new File(profile, rootName), names, 6);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static File findInRoot(File root, List<String> names, int depth) {
        return findInRoot(root, names, depth, new int[]{0});
    }

    private static File findInRoot(File root, List<String> names, int depth, int[] visited) {
        if (root == null || !root.isDirectory() || depth < 0 || visited[0] > NAMED_FILE_VISIT_LIMIT) {
            return null;
        }
        File[] files = MediaFileEntries.sortedChildren(root);
        if (files.length == 0) {
            return null;
        }
        for (File file : files) {
            if (file == null) {
                continue;
            }
            visited[0]++;
            if (MediaFiles.isExistingFile(file) && nameMatches(file.getName(), names)) {
                return file;
            }
        }
        for (File file : files) {
            if (file != null && file.isDirectory()) {
                File found = findInRoot(file, names, depth - 1, visited);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean nameMatches(String fileName, List<String> names) {
        if (isBlank(fileName) || names == null) {
            return false;
        }
        for (String name : names) {
            if (fileName.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
