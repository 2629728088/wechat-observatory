package cc.wechat.observatory.media;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

final class WechatImageSearchPlan {
    private WechatImageSearchPlan() {
    }

    static List<String> candidateNames(List<String> seeds) {
        List<String> names = new ArrayList<>();
        if (seeds == null) {
            return names;
        }
        for (String seed : seeds) {
            addCandidateVariants(names, seed);
        }
        return names;
    }

    static List<String> candidateNames(String seed) {
        List<String> names = new ArrayList<>();
        addCandidateVariants(names, seed);
        return names;
    }

    static void addCandidateVariants(List<String> names, String candidate) {
        if (names == null || isBlank(candidate)) {
            return;
        }
        String value = fileName(candidate.trim());
        if (WechatImageFiles.isReferencePointerName(value)) {
            addCandidate(names, value);
            value = WechatImageFiles.stripReferencePointerSuffix(value);
            if (isBlank(value)) {
                return;
            }
        }
        if (value.startsWith("th_") && value.length() > 3) {
            String withoutThumb = normalizeThumbnailBase(value.substring(3));
            addImageBaseCandidateVariants(names, withoutThumb);
            String thumbBase = MediaFiles.stripKnownMediaExtension(withoutThumb);
            addCandidate(names, "th_" + thumbBase);
            addCandidate(names, "th_" + thumbBase + "hd");
            addCandidate(names, value);
            return;
        }
        addImageBaseCandidateVariants(names, value);
        String base = MediaFiles.stripKnownMediaExtension(value);
        addCandidate(names, "th_" + base);
        addCandidate(names, "th_" + base + "hd");
    }

    static List<String> referencePointerNames(String fileName) {
        List<String> names = new ArrayList<>();
        if (isBlank(fileName)) {
            return names;
        }
        String value = fileName.trim();
        addReferencePointerName(names, canonicalThumbnailReferenceName(value));
        addReferencePointerName(names, value);
        String base = MediaFiles.stripKnownMediaExtension(value);
        if (!base.equals(value)) {
            addReferencePointerName(names, base);
        }
        return names;
    }

    static String bucketKey(String fileName) {
        if (isBlank(fileName)) {
            return "";
        }
        String value = fileName.trim();
        value = WechatImageFiles.stripReferencePointerSuffix(value);
        value = fileName(value);
        if (value.startsWith("th_") && value.length() > 3) {
            value = value.substring(3);
            if (value.endsWith("hd") && value.length() > 32) {
                value = value.substring(0, value.length() - 2);
            }
        }
        value = MediaFiles.stripKnownMediaExtension(value);
        if (value.length() < 4) {
            return "";
        }
        for (int i = 0; i < 4; i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return "";
            }
        }
        return value.toLowerCase(Locale.US);
    }

    static boolean matchesCandidateName(String fileName, List<String> candidateNames) {
        if (isBlank(fileName) || candidateNames == null || candidateNames.isEmpty()) {
            return false;
        }
        for (String candidate : candidateNames) {
            if (isBlank(candidate)) {
                continue;
            }
            String name = candidate.trim();
            if (fileName.equals(name)
                    || (!name.endsWith(WechatImageFiles.REF_SUFFIX)
                    && fileName.equals(name + WechatImageFiles.REF_SUFFIX))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeThumbnailBase(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        String base = MediaFiles.stripKnownMediaExtension(trimmed);
        if (base.endsWith("hd") && base.length() > 32) {
            return base.substring(0, base.length() - 2);
        }
        return trimmed;
    }

    private static String canonicalThumbnailReferenceName(String fileName) {
        if (isBlank(fileName)) {
            return "";
        }
        String base = MediaFiles.stripKnownMediaExtension(fileName.trim());
        if (base.startsWith("th_") && base.endsWith("hd") && base.length() > 35) {
            return base.substring(0, base.length() - 2);
        }
        return "";
    }

    private static void addReferencePointerName(List<String> names, String name) {
        if (isBlank(name)) {
            return;
        }
        String value = name.trim();
        addCandidate(names, value.endsWith(WechatImageFiles.REF_SUFFIX)
                ? value
                : value + WechatImageFiles.REF_SUFFIX);
    }

    private static void addImageBaseCandidateVariants(List<String> names, String candidate) {
        if (isBlank(candidate)) {
            return;
        }
        String value = candidate.trim();
        String base = MediaFiles.stripKnownMediaExtension(value);
        addCandidate(names, value);
        if (!base.equals(value)) {
            addCandidate(names, base);
        }
        addCandidate(names, base + ".jpg");
        addCandidate(names, base + ".jpeg");
        addCandidate(names, base + ".png");
        addCandidate(names, base + ".webp");
    }

    private static String fileName(String value) {
        if (isBlank(value)) {
            return "";
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf(File.separatorChar));
        if (slash >= 0 && slash + 1 < value.length()) {
            return value.substring(slash + 1);
        }
        return value;
    }

    private static void addCandidate(List<String> names, String name) {
        if (isBlank(name)) {
            return;
        }
        String value = name.trim();
        for (String existing : names) {
            if (existing.equals(value)) {
                return;
            }
        }
        names.add(value);
    }
}
