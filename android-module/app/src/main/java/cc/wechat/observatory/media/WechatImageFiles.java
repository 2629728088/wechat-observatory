package cc.wechat.observatory.media;

import java.io.File;

public final class WechatImageFiles {
    public static final String REF_SUFFIX = "\u2316";

    private WechatImageFiles() {
    }

    public static boolean isReferencePointerName(String name) {
        return name != null && name.endsWith(REF_SUFFIX);
    }

    public static String stripReferencePointerSuffix(String name) {
        if (name == null) {
            return "";
        }
        return isReferencePointerName(name)
                ? name.substring(0, name.length() - REF_SUFFIX.length())
                : name;
    }

    public static boolean isReferencePointerFile(File file) {
        return MediaFiles.isExistingFile(file) && isReferencePointerName(file.getName());
    }

    public static boolean isLowQualityThumbnailFile(File file) {
        if (!MediaFiles.isExistingFile(file) || !isUnderImage2(file)) {
            return false;
        }
        String name = file.getName() == null ? "" : file.getName();
        if (!name.startsWith("th_") || isReferencePointerName(name)) {
            return false;
        }
        return name.startsWith("th_");
    }

    static File image2RootFor(File file) {
        File current = file;
        while (current != null) {
            if ("image2".equals(current.getName())) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private static boolean isUnderImage2(File file) {
        return image2RootFor(file) != null;
    }
}
