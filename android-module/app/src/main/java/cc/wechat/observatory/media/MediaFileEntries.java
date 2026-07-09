package cc.wechat.observatory.media;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

final class MediaFileEntries {
    private static final File[] EMPTY = new File[0];

    private MediaFileEntries() {
    }

    static File[] sortedChildren(File root) {
        File[] files = root == null ? null : root.listFiles();
        if (files == null || files.length == 0) {
            return EMPTY;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                String leftName = left == null || left.getName() == null ? "" : left.getName();
                String rightName = right == null || right.getName() == null ? "" : right.getName();
                int byName = leftName.compareTo(rightName);
                if (byName != 0) {
                    return byName;
                }
                String leftPath = left == null || left.getAbsolutePath() == null ? "" : left.getAbsolutePath();
                String rightPath = right == null || right.getAbsolutePath() == null ? "" : right.getAbsolutePath();
                return leftPath.compareTo(rightPath);
            }
        });
        return files;
    }
}
