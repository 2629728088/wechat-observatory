package cc.wechat.observatory.outbox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;
import static cc.wechat.observatory.util.Strings.trimRight;

public final class OutboxMediaDownloader {
    public static final long DEFAULT_DOWNLOAD_LIMIT_BYTES = 50L * 1024L * 1024L;

    private final ConnectionFactory connections;
    private final long defaultLimitBytes;

    public OutboxMediaDownloader() {
        this(new ConnectionFactory() {
            @Override
            public HttpURLConnection open(URL url) throws IOException {
                return (HttpURLConnection) url.openConnection();
            }
        }, DEFAULT_DOWNLOAD_LIMIT_BYTES);
    }

    OutboxMediaDownloader(ConnectionFactory connections, long defaultLimitBytes) {
        this.connections = connections;
        this.defaultLimitBytes = defaultLimitBytes;
    }

    public File download(Request request) throws Exception {
        if (request == null || isBlank(request.baseUrl) || isBlank(request.apiKey)) {
            throw new IOException("bridge config is missing");
        }
        String path = normalizedMediaPath(request.mediaUrl);
        String separator = path.indexOf('?') >= 0 ? "&" : "?";
        URL url = new URL(trimRight(request.baseUrl, "/") + path + separator + "api_key=" + urlEncode(request.apiKey));
        HttpURLConnection connection = connections.open(url);
        if (connection == null) {
            throw new IOException("media download connection is not available");
        }
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Connection", "close");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("media download HTTP " + status + ": " + readResponse(connection.getErrorStream()));
            }
            File dir = request.cacheDir;
            if (dir == null) {
                throw new IOException("cache dir is not available");
            }
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("create outbox media dir failed");
            }
            String suffix = request.preserveName
                    ? "-" + safeCacheFileName(request.mediaName, path)
                    : safeCacheExtension(request.mediaName, path);
            File target = new File(dir, "outbox-" + System.currentTimeMillis() + suffix);
            copyLimited(connection.getInputStream(), target, limitBytes(request.mediaUploadLimitBytes));
            return target;
        } finally {
            connection.disconnect();
        }
    }

    private static String normalizedMediaPath(String mediaUrl) throws IOException {
        if (isBlank(mediaUrl)) {
            throw new IOException("unsupported media_url");
        }
        String path = mediaUrl.trim();
        if (path.startsWith("/api/media/")) {
            path = "/module/media/" + path.substring("/api/media/".length());
        }
        if (!path.startsWith("/module/media/")) {
            throw new IOException("unsupported media_url");
        }
        return path;
    }

    private long limitBytes(long mediaUploadLimitBytes) {
        return Math.max(defaultLimitBytes, mediaUploadLimitBytes);
    }

    private static void copyLimited(InputStream input, File target, long limit) throws IOException {
        long total = 0L;
        byte[] buffer = new byte[8192];
        try (InputStream in = input; FileOutputStream output = new FileOutputStream(target, false)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IOException("media download exceeds limit");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static String safeCacheExtension(String mediaName, String mediaUrl) {
        String value = firstNonBlank(mediaName, mediaUrl).trim().toLowerCase(Locale.US);
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int dot = value.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= value.length()) {
            return ".img";
        }
        String ext = value.substring(dot);
        if (ext.length() > 8) {
            return ".img";
        }
        for (int i = 1; i < ext.length(); i++) {
            char ch = ext.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'))) {
                return ".img";
            }
        }
        return ext;
    }

    private static String safeCacheFileName(String mediaName, String mediaUrl) {
        String value = firstNonBlank(mediaName, mediaUrl).trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < value.length()) {
            value = value.substring(slash + 1);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && out.length() < 80; i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else if (ch == '.' || ch == '_' || ch == '-') {
                out.append(ch);
            }
        }
        String name = out.toString();
        if (isBlank(name) || ".".equals(name) || "..".equals(name) || name.startsWith(".")) {
            String ext = safeCacheExtension(mediaName, mediaUrl);
            if (".img".equals(ext)) {
                ext = ".bin";
            }
            name = "file" + ext;
        }
        return name;
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : (second == null ? "" : second);
    }

    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                out.append(ch);
            } else {
                byte[] bytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    out.append('%');
                    int v = b & 0xFF;
                    char high = Character.toUpperCase(Character.forDigit((v >> 4) & 0xF, 16));
                    char low = Character.toUpperCase(Character.forDigit(v & 0xF, 16));
                    out.append(high).append(low);
                }
            }
        }
        return out.toString();
    }

    private static String readResponse(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
        }
        return out.toString();
    }

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public static final class Request {
        final String baseUrl;
        final String apiKey;
        final String mediaUrl;
        final String mediaName;
        final File cacheDir;
        final long mediaUploadLimitBytes;
        final boolean preserveName;

        public Request(
                String baseUrl,
                String apiKey,
                String mediaUrl,
                String mediaName,
                File cacheDir,
                long mediaUploadLimitBytes,
                boolean preserveName) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.mediaUrl = mediaUrl;
            this.mediaName = mediaName;
            this.cacheDir = cacheDir;
            this.mediaUploadLimitBytes = mediaUploadLimitBytes;
            this.preserveName = preserveName;
        }
    }
}
