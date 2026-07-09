package cc.wechat.observatory.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

final class WechatImageInfoCandidateNames {
    private final List<String> values;

    private WechatImageInfoCandidateNames(List<String> values) {
        this.values = values == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    static WechatImageInfoCandidateNames fromValues(List<String> imageInfoValues) {
        return new WechatImageInfoCandidateNames(
                candidateNames(imageInfoValues));
    }

    static boolean isSearchableValue(String value) {
        if (isBlank(value)) {
            return false;
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        return !(lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("http/")
                || lower.startsWith("https/")
                || trimmed.indexOf('<') >= 0);
    }

    private static List<String> candidateNames(List<String> imageInfoValues) {
        List<String> names = new ArrayList<>();
        if (imageInfoValues == null) {
            return names;
        }
        for (String value : imageInfoValues) {
            if (!isSearchableValue(value)) {
                continue;
            }
            String normalized = MediaSearchPlan.normalizeHint(value);
            if (isBlank(normalized)) {
                continue;
            }
            WechatImageSearchPlan.addCandidateVariants(names, normalized);
        }
        return names;
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    List<String> values() {
        return values;
    }
}
