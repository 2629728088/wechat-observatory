package cc.wechat.observatory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static cc.wechat.observatory.util.Strings.isBlank;
import static cc.wechat.observatory.util.Strings.shortError;

final class HookWechatVoiceFilePreparer {
    interface Reflection {
        Class<?> findClass(ClassLoader classLoader, String name) throws ClassNotFoundException;

        Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws NoSuchMethodException;

        Field findField(Class<?> cls, String name) throws NoSuchFieldException;
    }

    interface Logger {
        void log(String message);
    }

    private HookWechatVoiceFilePreparer() {
    }

    static String prepare(
            ClassLoader classLoader,
            String voiceFileName,
            File source,
            Reflection reflection,
            Logger logger) throws Exception {
        String audioPath = voiceStoragePath(classLoader, voiceFileName, true, reflection);
        copyFileToWeChatPath(classLoader, source, audioPath, reflection);
        try {
            String legacyPath = voiceStoragePath(classLoader, voiceFileName, false, reflection);
            if (!isBlank(legacyPath) && !legacyPath.equals(audioPath)) {
                copyFileToWeChatPath(classLoader, source, legacyPath, reflection);
            }
        } catch (Throwable t) {
            log(logger, "prepare legacy voice path skipped: " + shortError(t));
        }
        return audioPath;
    }

    private static String voiceStoragePath(
            ClassLoader classLoader,
            String voiceFileName,
            boolean audioScoped,
            Reflection reflection) throws Exception {
        if (reflection == null) {
            throw new IllegalStateException("voice reflection adapter is not available");
        }
        Class<?> storageInterface = reflection.findClass(classLoader, "tg3.u0");
        Object storage = reflection.findMethod(reflection.findClass(classLoader, "i95.n0"), "c", Class.class)
                .invoke(null, storageInterface);
        if (storage == null) {
            throw new IllegalStateException("voice storage service is not available");
        }
        Class<?> yClass = reflection.findClass(classLoader, "bm5.y");
        Object voiceResource;
        if (audioScoped) {
            Object builder = reflection.findField(yClass, "i").get(null);
            Class<?> oi3Class = reflection.findClass(classLoader, "oi3.g");
            Class<?> f0Class = reflection.findClass(classLoader, "bm5.f0");
            Object audioType = reflection.findField(f0Class, "u").get(null);
            voiceResource = reflection.findMethod(builder.getClass(), "d", oi3Class, f0Class)
                    .invoke(builder, null, audioType);
        } else {
            voiceResource = reflection.findField(yClass, "j").get(null);
        }
        Method fullPath = reflection.findMethod(storage.getClass(), "vj", yClass, String.class, boolean.class);
        Object path = fullPath.invoke(storage, voiceResource, voiceFileName, true);
        String value = path == null ? "" : String.valueOf(path);
        if (isBlank(value)) {
            throw new IOException("voice storage path is empty");
        }
        return value;
    }

    private static void copyFileToWeChatPath(
            ClassLoader classLoader,
            File source,
            String targetPath,
            Reflection reflection) throws Exception {
        if (source == null || !source.isFile()) {
            throw new IOException("voice source file is missing");
        }
        if (isBlank(targetPath)) {
            throw new IOException("voice target path is empty");
        }
        Throwable vfsFailure = null;
        try {
            Class<?> vfsClass = reflection.findClass(classLoader, "com.tencent.mm.vfs.w6");
            Method open = reflection.findMethod(vfsClass, "B", String.class, boolean.class);
            Object raf = open.invoke(null, targetPath, true);
            if (raf instanceof RandomAccessFile) {
                try (RandomAccessFile output = (RandomAccessFile) raf;
                     FileInputStream input = new FileInputStream(source)) {
                    output.setLength(0L);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            vfsFailure = t;
        }

        try {
            File target = new File(targetPath);
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("create voice target dir failed");
            }
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(target, false)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
        } catch (Throwable fileFailure) {
            if (vfsFailure != null) {
                throw new IOException("copy voice file failed via vfs: " + shortError(vfsFailure)
                        + "; file: " + shortError(fileFailure));
            }
            throw fileFailure;
        }
    }

    private static void log(Logger logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
