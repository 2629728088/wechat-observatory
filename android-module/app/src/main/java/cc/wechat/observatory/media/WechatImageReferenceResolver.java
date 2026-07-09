package cc.wechat.observatory.media;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

final class WechatImageReferenceResolver {
    interface Logger {
        void log(String message);
    }

    enum Status {
        NOT_REFERENCE_POINTER,
        INVALID_REFERENCE_ID,
        IMAGE2_ROOT_MISSING,
        TARGET_MISSING,
        TARGET_FOUND
    }

    static final class Result {
        private final Status status;
        private final File pointer;
        private final File target;
        private final String referenceId;

        private Result(Status status, File pointer, File target, String referenceId) {
            this.status = status;
            this.pointer = pointer;
            this.target = target;
            this.referenceId = referenceId;
        }

        boolean found() {
            return MediaFiles.isExistingFile(target);
        }

        Status status() {
            return status;
        }

        File pointer() {
            return pointer;
        }

        File target() {
            return target;
        }

        String referenceId() {
            return referenceId;
        }
    }

    private WechatImageReferenceResolver() {
    }

    static Result resolve(File pointer, Logger logger) {
        if (!WechatImageFiles.isReferencePointerFile(pointer)) {
            return new Result(Status.NOT_REFERENCE_POINTER, pointer, null, "");
        }
        String refId = readSmallTextFile(pointer, 96).trim();
        if (!isUuidText(refId)) {
            return new Result(Status.INVALID_REFERENCE_ID, pointer, null, refId);
        }
        File image2Root = WechatImageFiles.image2RootFor(pointer);
        if (image2Root == null) {
            return new Result(Status.IMAGE2_ROOT_MISSING, pointer, null, refId);
        }
        File target = new File(new File(new File(image2Root, ".ref"), "d"), refId);
        if (!MediaFiles.isLikelyImageMediaFile(target)) {
            return new Result(Status.TARGET_MISSING, pointer, null, refId);
        }
        log(logger, "image ref resolved pointer=" + pointer.getName() + " ref=" + refId + " size=" + target.length());
        return new Result(Status.TARGET_FOUND, pointer, target, refId);
    }

    private static String readSmallTextFile(File file, int limit) {
        if (file == null || limit <= 0) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[Math.min(64, limit)];
        int total = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer, 0, Math.min(buffer.length, limit - total))) != -1) {
                out.write(buffer, 0, read);
                total += read;
                if (total >= limit) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            return "";
        }
        return new String(out.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static boolean isUuidText(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (c != '-') {
                    return false;
                }
                continue;
            }
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static void log(Logger logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}
