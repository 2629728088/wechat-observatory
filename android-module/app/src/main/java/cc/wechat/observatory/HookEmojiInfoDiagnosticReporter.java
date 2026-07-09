package cc.wechat.observatory;

import java.util.HashSet;
import java.util.Set;

import cc.wechat.observatory.media.EmojiInfoDiagnostics;
import cc.wechat.observatory.media.MediaDigests;

import static cc.wechat.observatory.util.Strings.shortError;

final class HookEmojiInfoDiagnosticReporter {
    interface ClassLoaderProvider {
        ClassLoader classLoader();
    }

    interface EmojiInfoLoader {
        Object load(ClassLoader classLoader, String emojiMd5) throws Exception;
    }

    interface Logger {
        void log(String message);
    }

    private static final int REPORTED_MD5_LIMIT = 80;

    private final Set<String> reportedMd5 = new HashSet<>();
    private final ClassLoaderProvider classLoaderProvider;
    private final EmojiInfoLoader emojiInfoLoader;
    private final Logger logger;

    HookEmojiInfoDiagnosticReporter(
            ClassLoaderProvider classLoaderProvider,
            EmojiInfoLoader emojiInfoLoader,
            Logger logger) {
        this.classLoaderProvider = classLoaderProvider;
        this.emojiInfoLoader = emojiInfoLoader;
        this.logger = logger;
    }

    void report(String emojiMd5) {
        String md5 = MediaDigests.normalizeMd5(emojiMd5);
        if (!MediaDigests.isMd5Hex(md5) || !remember(md5)) {
            return;
        }
        try {
            Object emojiInfo = emojiInfoLoader == null
                    ? null
                    : emojiInfoLoader.load(classLoader(), md5);
            if (emojiInfo == null) {
                log("emoji info missing md5=" + MediaDigests.shortMd5(md5));
                return;
            }
            log("emoji info found md5=" + MediaDigests.shortMd5(md5)
                    + " class=" + emojiInfo.getClass().getName()
                    + " fields=" + EmojiInfoDiagnostics.fieldSummary(emojiInfo));
        } catch (Throwable t) {
            log("emoji info diagnostic failed md5=" + MediaDigests.shortMd5(md5)
                    + " error=" + shortError(t));
        }
    }

    private boolean remember(String md5) {
        synchronized (reportedMd5) {
            if (!reportedMd5.add(md5)) {
                return false;
            }
            if (reportedMd5.size() > REPORTED_MD5_LIMIT) {
                reportedMd5.clear();
                reportedMd5.add(md5);
            }
            return true;
        }
    }

    private ClassLoader classLoader() {
        return classLoaderProvider == null ? HookEmojiInfoDiagnosticReporter.class.getClassLoader() : classLoaderProvider.classLoader();
    }

    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
