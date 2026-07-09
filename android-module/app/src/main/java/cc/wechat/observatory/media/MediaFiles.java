package cc.wechat.observatory.media;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaFiles {
    static final int MESSAGE_TYPE_IMAGE = 3;
    static final int MESSAGE_TYPE_VOICE = 34;
    static final int MESSAGE_TYPE_VIDEO = 43;
    static final int MESSAGE_TYPE_EMOJI = 47;
    static final int MESSAGE_TYPE_LOCATION = 48;
    static final int MESSAGE_TYPE_FILE = 49;
    static final int MESSAGE_TYPE_APP_VIDEO = 62;
    private static final int MESSAGE_TYPE_FILE_TRANSFER = 1090519089;

    private MediaFiles() {
    }

    public static String kindForMessageType(int type) {
        switch (type) {
            case MESSAGE_TYPE_IMAGE:
                return "image";
            case MESSAGE_TYPE_VOICE:
                return "voice";
            case MESSAGE_TYPE_VIDEO:
            case MESSAGE_TYPE_APP_VIDEO:
                return "video";
            case MESSAGE_TYPE_EMOJI:
                return "emoji";
            case MESSAGE_TYPE_LOCATION:
                return "location";
            case MESSAGE_TYPE_FILE:
            case MESSAGE_TYPE_FILE_TRANSFER:
                return "file";
            default:
                return "";
        }
    }

    public static boolean isSupportedMessageType(int type) {
        return !isBlank(kindForMessageType(type));
    }

    public static boolean isImageMessageType(int type) {
        return type == MESSAGE_TYPE_IMAGE;
    }

    public static boolean isVoiceMessageType(int type) {
        return type == MESSAGE_TYPE_VOICE;
    }

    public static boolean isVideoMessageType(int type) {
        return type == MESSAGE_TYPE_VIDEO || type == MESSAGE_TYPE_APP_VIDEO;
    }

    public static boolean isEmojiMessageType(int type) {
        return type == MESSAGE_TYPE_EMOJI;
    }

    public static boolean shouldLogMissingMedia(int type) {
        return isImageMessageType(type)
                || isVoiceMessageType(type)
                || isVideoMessageType(type)
                || isEmojiMessageType(type);
    }

    public static boolean isExistingFile(File file) {
        return file != null && file.isFile();
    }

    public static boolean isLikelyVoiceMediaFile(File file) {
        if (!isExistingFile(file) || file.length() <= 0L) {
            return false;
        }
        String name = file.getName() == null ? "" : file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".amr") || name.endsWith(".silk")) {
            return true;
        }
        File parent = file.getParentFile();
        while (parent != null) {
            if ("voice2".equals(parent.getName())) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }

    public static boolean isSupportedVoiceMediaFile(File file, String mediaName) {
        String name = firstNonBlank(mediaName, file == null ? "" : file.getName()).trim().toLowerCase(Locale.US);
        if (name.endsWith(".amr") || name.endsWith(".silk")) {
            return true;
        }
        return hasVoiceHeader(file);
    }

    public static boolean isLikelyImageMediaFile(File file) {
        if (!isExistingFile(file) || file.length() < 512L) {
            return false;
        }
        return hasImageHeader(file);
    }

    public static String detectMime(int type, File file, byte[] bytes) {
        String name = file == null ? "" : file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8})) {
            return "image/jpeg";
        }
        if (name.endsWith(".png") || startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47})) {
            return "image/png";
        }
        if (name.endsWith(".gif") || startsWith(bytes, new byte[]{0x47, 0x49, 0x46})) {
            return "image/gif";
        }
        if (name.endsWith(".webp") || containsAsciiAt(bytes, "WEBP", 8)) {
            return "image/webp";
        }
        if (name.endsWith(".mp4") || containsAsciiAt(bytes, "ftyp", 4)) {
            return "video/mp4";
        }
        if (name.endsWith(".amr") || startsWith(bytes, "#!AMR".getBytes(StandardCharsets.US_ASCII))) {
            return "audio/amr";
        }
        if (name.endsWith(".silk") || startsWith(bytes, "#!SILK".getBytes(StandardCharsets.US_ASCII))) {
            return "audio/silk";
        }
        if (isVoiceMessageType(type)) {
            return "audio/amr";
        }
        if (isImageMessageType(type)) {
            return "image/jpeg";
        }
        if (isVideoMessageType(type)) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }

    public static String uploadName(File file, String mime, String id) {
        String name = file == null ? "" : file.getName();
        if (isBlank(name)) {
            name = isBlank(id) ? "media" : "media-" + id;
        }
        if (name.indexOf('.') >= 0) {
            return name;
        }
        return name + extensionForMime(mime);
    }

    public static String stripKnownMediaExtension(String value) {
        if (isBlank(value)) {
            return "";
        }
        String lower = value.toLowerCase(Locale.US);
        String[] extensions = new String[]{".mp4", ".jpg", ".jpeg", ".png", ".webp", ".amr", ".silk"};
        for (String extension : extensions) {
            if (lower.endsWith(extension) && value.length() > extension.length()) {
                return value.substring(0, value.length() - extension.length());
            }
        }
        return value;
    }

    private static boolean hasImageHeader(File file) {
        byte[] header = new byte[16];
        int read;
        try (FileInputStream input = new FileInputStream(file)) {
            read = input.read(header);
        } catch (Throwable ignored) {
            return false;
        }
        if (read < 4) {
            return false;
        }
        if ((header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8) {
            return true;
        }
        if ((header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47) {
            return true;
        }
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46) {
            return true;
        }
        return read >= 12 && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
    }

    private static boolean hasVoiceHeader(File file) {
        if (!isExistingFile(file)) {
            return false;
        }
        byte[] header = new byte[16];
        int read;
        try (FileInputStream input = new FileInputStream(file)) {
            read = input.read(header);
        } catch (Throwable ignored) {
            return false;
        }
        if (read <= 0) {
            return false;
        }
        String head = new String(header, 0, read, StandardCharsets.US_ASCII);
        return head.startsWith("#!AMR") || head.startsWith("#!SILK");
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : (second == null ? "" : second);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || prefix == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAsciiAt(byte[] bytes, String text, int offset) {
        if (bytes == null || text == null || offset < 0 || bytes.length < offset + text.length()) {
            return false;
        }
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionForMime(String mime) {
        if ("image/png".equals(mime)) {
            return ".png";
        }
        if ("image/gif".equals(mime)) {
            return ".gif";
        }
        if ("image/webp".equals(mime)) {
            return ".webp";
        }
        if ("audio/amr".equals(mime)) {
            return ".amr";
        }
        if ("audio/silk".equals(mime)) {
            return ".silk";
        }
        if ("video/mp4".equals(mime)) {
            return ".mp4";
        }
        if ("image/jpeg".equals(mime)) {
            return ".jpg";
        }
        return ".bin";
    }
}
