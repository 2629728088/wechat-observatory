package cc.wechat.observatory.media;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaSearchPlan {
    private MediaSearchPlan() {
    }

    public static List<String> candidateNames(int type, String mediaHint, String emojiMd5) {
        List<String> seeds = hintSeeds(mediaHint);
        if (MediaFiles.isImageMessageType(type)) {
            return WechatImageSearchPlan.candidateNames(seeds);
        }
        if (MediaFiles.isVideoMessageType(type)) {
            return videoCandidateNames(seeds);
        }
        List<String> names = baseCandidateNames(seeds);
        if (MediaFiles.isVoiceMessageType(type)) {
            addVoiceCandidateVariants(names, new ArrayList<String>(names));
        }
        if (MediaFiles.isEmojiMessageType(type)) {
            addEmojiCandidateVariants(names, emojiMd5);
        }
        return names;
    }

    public static List<String> imageCandidateNames(String mediaHint) {
        List<String> names = new ArrayList<>();
        String normalized = normalizeHint(mediaHint);
        if (!isBlank(normalized)) {
            WechatImageSearchPlan.addCandidateVariants(names, normalized);
        }
        return names;
    }

    public static String normalizeHint(String mediaHint) {
        if (isBlank(mediaHint)) {
            return "";
        }
        String value = mediaHint.trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int scheme = value.indexOf("://");
        if (scheme >= 0 && scheme + 3 < value.length()) {
            value = value.substring(scheme + 3);
        }
        return value.trim();
    }

    public static String[] searchRoots(int type) {
        switch (type) {
            case MediaFiles.MESSAGE_TYPE_IMAGE:
                return new String[]{"image2"};
            case MediaFiles.MESSAGE_TYPE_VOICE:
                return new String[]{"voice2"};
            case MediaFiles.MESSAGE_TYPE_VIDEO:
            case MediaFiles.MESSAGE_TYPE_APP_VIDEO:
                return new String[]{"video", "c2c_temp/origin/video", "c2c_temp"};
            case MediaFiles.MESSAGE_TYPE_EMOJI:
                return emojiSearchRoots();
            case MediaFiles.MESSAGE_TYPE_FILE:
                return new String[]{"attachment", "openapi", "image2"};
            default:
                return new String[]{"image2", "voice2", "video", "attachment"};
        }
    }

    public static String[] imageSearchRoots() {
        return searchRoots(MediaFiles.MESSAGE_TYPE_IMAGE);
    }

    public static String[] videoCacheSearchRoots() {
        return new String[]{"finder/video", "video"};
    }

    public static String[] emojiFallbackSearchRoots() {
        return new String[]{"files/public/emoji", "cache"};
    }

    public static int emojiFallbackSearchDepth(String root) {
        return "cache".equals(root) ? 4 : 6;
    }

    private static String[] emojiSearchRoots() {
        return new String[]{"emoji", "emoji/egg", "emoji/custom", "emoji/panel", "emoticon-user-files", "emoticon-user-core"};
    }

    private static List<String> hintSeeds(String mediaHint) {
        List<String> seeds = new ArrayList<>();
        String normalized = normalizeHint(mediaHint);
        addCandidate(seeds, normalized);
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf(File.separatorChar));
        if (slash >= 0 && slash + 1 < normalized.length()) {
            addCandidate(seeds, normalized.substring(slash + 1));
        }
        return seeds;
    }

    private static List<String> baseCandidateNames(List<String> seeds) {
        List<String> names = new ArrayList<>();
        if (seeds == null) {
            return names;
        }
        for (String seed : seeds) {
            addCandidate(names, seed);
        }
        return names;
    }

    private static List<String> videoCandidateNames(List<String> seeds) {
        List<String> videoNames = new ArrayList<>();
        List<String> snapshot = baseCandidateNames(seeds);
        for (String candidate : snapshot) {
            if (isBlank(candidate)) {
                continue;
            }
            if (candidate.startsWith("th_") && candidate.length() > 3) {
                addVideoCandidateVariants(videoNames, candidate.substring(3));
            }
            addVideoCandidateVariants(videoNames, candidate);
        }
        for (String candidate : snapshot) {
            addCandidate(videoNames, candidate);
        }
        return videoNames;
    }

    private static void addVoiceCandidateVariants(List<String> names, List<String> candidates) {
        if (candidates == null) {
            return;
        }
        for (String candidate : candidates) {
            String base = MediaFiles.stripKnownMediaExtension(candidate);
            if (!base.equals(candidate)) {
                addCandidate(names, base);
            }
            addCandidate(names, base + ".amr");
            addCandidate(names, base + ".silk");
        }
    }

    private static void addVideoCandidateVariants(List<String> names, String candidate) {
        if (isBlank(candidate)) {
            return;
        }
        String value = candidate.trim();
        addCandidate(names, value);
        String base = MediaFiles.stripKnownMediaExtension(value);
        if (!base.equals(value)) {
            addCandidate(names, base);
        }
        addCandidate(names, base + ".mp4");
        if (!base.startsWith("video_")) {
            addCandidate(names, "video_" + base);
            addCandidate(names, "video_" + base + ".mp4");
        }
    }

    private static void addEmojiCandidateVariants(List<String> names, String emojiMd5) {
        if (isBlank(emojiMd5)) {
            return;
        }
        String md5 = emojiMd5.trim().toLowerCase(Locale.US);
        addCandidate(names, md5);
        addCandidate(names, md5 + ".gif");
        addCandidate(names, md5 + ".png");
        addCandidate(names, md5 + ".webp");
        addCandidate(names, md5 + ".jpg");
        addCandidate(names, "emoji_" + md5);
        addCandidate(names, "emoji_" + md5 + ".gif");
        addCandidate(names, "emoji_" + md5 + ".png");
        addCandidate(names, "emoji_" + md5 + ".webp");
        addCandidate(names, "thumb_" + md5);
        addCandidate(names, "thumb_" + md5 + ".gif");
        addCandidate(names, "thumb_" + md5 + ".png");
        addCandidate(names, "thumb_" + md5 + ".webp");
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
