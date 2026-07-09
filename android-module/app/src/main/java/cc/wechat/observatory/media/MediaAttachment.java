package cc.wechat.observatory.media;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class MediaAttachment {
    private final String mime;
    private final String name;
    private final int size;
    private final byte[] bytes;

    private MediaAttachment(String mime, String name, byte[] bytes) {
        this.mime = mime;
        this.name = name;
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
        this.size = this.bytes.length;
    }

    public static MediaAttachment fromFile(File file, int type, String id, long limit) throws IOException {
        byte[] bytes = readFileBytes(file, limit);
        String mime = MediaFiles.detectMime(type, file, bytes);
        return new MediaAttachment(mime, MediaFiles.uploadName(file, mime, id), bytes);
    }

    public String mime() {
        return mime;
    }

    public String name() {
        return name;
    }

    public int size() {
        return size;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    private static byte[] readFileBytes(File file, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IOException("media file exceeds limit");
                }
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }
}
