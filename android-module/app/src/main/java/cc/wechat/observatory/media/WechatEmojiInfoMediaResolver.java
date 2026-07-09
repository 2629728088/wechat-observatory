package cc.wechat.observatory.media;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

final class WechatEmojiInfoMediaResolver {
    private static final int FIELD_LIMIT = 64;
    private static final int SEARCH_DEPTH = 4;

    private WechatEmojiInfoMediaResolver() {
    }

    static MediaResolver.Result resolve(
            File appRoot,
            Object emojiInfo,
            String emojiMd5,
            MediaResolver.Logger logger) {
        String md5 = MediaDigests.normalizeMd5(emojiMd5);
        if (appRoot == null || emojiInfo == null || !MediaDigests.isMd5Hex(md5)) {
            return MediaResolver.Result.notFound();
        }
        List<String> values = stringFieldValues(emojiInfo);
        if (values.isEmpty()) {
            return MediaResolver.Result.notFound();
        }
        List<String> names = MediaSearchPlan.candidateNames(MediaFiles.MESSAGE_TYPE_EMOJI, "", md5);
        MediaResolver.Result direct = resolveDirectFile(appRoot, values, logger);
        if (!direct.isNotFound()) {
            return direct;
        }
        return resolveDirectoryCandidate(appRoot, values, names, logger);
    }

    private static MediaResolver.Result resolveDirectFile(
            File appRoot,
            List<String> values,
            MediaResolver.Logger logger) {
        for (String value : values) {
            if (isRemoteUrl(value)) {
                continue;
            }
            File file = MediaDirectFileResolver.resolve(appRoot, value);
            if (file != null) {
                logSelected(logger, file);
                return MediaResolver.Result.found(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, file);
            }
        }
        return MediaResolver.Result.notFound();
    }

    private static MediaResolver.Result resolveDirectoryCandidate(
            File appRoot,
            List<String> values,
            List<String> names,
            MediaResolver.Logger logger) {
        if (names == null || names.isEmpty()) {
            return MediaResolver.Result.notFound();
        }
        for (String value : values) {
            File directory = directoryCandidate(appRoot, value);
            File found = NamedMediaFileFinder.findInRoot(directory, names, SEARCH_DEPTH);
            if (found != null) {
                logSelected(logger, found);
                return MediaResolver.Result.found(MediaResolver.ResolutionStatus.EMOJI_MEDIA_FILE, found);
            }
        }
        return MediaResolver.Result.notFound();
    }

    private static File directoryCandidate(File appRoot, String value) {
        if (isBlank(value) || isRemoteUrl(value)) {
            return null;
        }
        String trimmed = value.trim();
        File direct = new File(trimmed);
        if (direct.isDirectory()) {
            return direct;
        }
        if (appRoot == null) {
            return null;
        }
        File relative = new File(appRoot, trimmed);
        return relative.isDirectory() ? relative : null;
    }

    private static List<String> stringFieldValues(Object emojiInfo) {
        List<String> values = new ArrayList<>();
        Class<?> current = emojiInfo.getClass();
        while (current != null && values.size() < FIELD_LIMIT) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (values.size() >= FIELD_LIMIT) {
                    break;
                }
                if (field == null
                        || field.getType() != String.class
                        || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(emojiInfo);
                    add(values, value == null ? "" : String.valueOf(value));
                } catch (Throwable ignored) {
                    // Field visibility differs across WeChat builds; unreadable fields are simply not evidence.
                }
            }
            current = current.getSuperclass();
        }
        return values;
    }

    private static void add(List<String> values, String value) {
        if (isBlank(value)) {
            return;
        }
        String trimmed = value.trim();
        for (String existing : values) {
            if (existing.equals(trimmed)) {
                return;
            }
        }
        values.add(trimmed);
    }

    private static boolean isRemoteUrl(String value) {
        if (isBlank(value)) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static void logSelected(MediaResolver.Logger logger, File file) {
        if (logger != null && file != null) {
            logger.log("emoji media selected from EmojiInfo file=" + file.getName() + " size=" + file.length());
        }
    }
}
