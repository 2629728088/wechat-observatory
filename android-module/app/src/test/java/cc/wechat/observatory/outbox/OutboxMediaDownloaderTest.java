package cc.wechat.observatory.outbox;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class OutboxMediaDownloaderTest {
    @Test
    public void downloadsApiMediaWithModulePathAndEncodedApiKey() throws Exception {
        File cacheDir = Files.createTempDirectory("wxo-outbox").toFile();
        CapturingFactory factory = CapturingFactory.ok(new byte[]{1, 2, 3});

        File file = downloader(factory, 1024L).download(new OutboxMediaDownloader.Request(
                "https://example.test/root/",
                "key with/slash",
                "/api/media/photo.jpg?source=admin",
                "photo.jpg",
                cacheDir,
                0L,
                false));

        assertEquals("https://example.test/root/module/media/photo.jpg?source=admin&api_key=key%20with%2Fslash",
                factory.url.toString());
        assertTrue(file.getName().endsWith(".jpg"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file.toPath()));
        assertTrue(factory.connection.disconnected);
    }

    @Test
    public void rejectsUnsupportedMediaUrlBeforeOpeningConnection() throws Exception {
        CapturingFactory factory = CapturingFactory.ok(new byte[]{1});

        try {
            downloader(factory, 1024L).download(new OutboxMediaDownloader.Request(
                    "https://example.test",
                    "key",
                    "/other/media/photo.jpg",
                    "photo.jpg",
                    Files.createTempDirectory("wxo-outbox").toFile(),
                    0L,
                    false));
            fail("expected unsupported media_url");
        } catch (IOException e) {
            assertEquals("unsupported media_url", e.getMessage());
        }
        assertNull(factory.url);
    }

    @Test
    public void preserveNameUsesSanitizedOriginalName() throws Exception {
        File cacheDir = Files.createTempDirectory("wxo-outbox").toFile();
        CapturingFactory factory = CapturingFactory.ok("file-body".getBytes(StandardCharsets.UTF_8));

        File file = downloader(factory, 1024L).download(new OutboxMediaDownloader.Request(
                "https://example.test",
                "key",
                "/module/media/raw.bin",
                "../bad 名字.png?x=1",
                cacheDir,
                0L,
                true));

        assertTrue(file.getName().endsWith("-bad.png"));
    }

    @Test
    public void httpFailureIncludesResponseBody() throws Exception {
        CapturingFactory factory = CapturingFactory.error(404, "missing");

        try {
            downloader(factory, 1024L).download(new OutboxMediaDownloader.Request(
                    "https://example.test",
                    "key",
                    "/module/media/missing.jpg",
                    "missing.jpg",
                    Files.createTempDirectory("wxo-outbox").toFile(),
                    0L,
                    false));
            fail("expected media download HTTP error");
        } catch (IOException e) {
            assertEquals("media download HTTP 404: missing", e.getMessage());
        }
        assertTrue(factory.connection.disconnected);
    }

    @Test
    public void enforcesDownloadLimit() throws Exception {
        CapturingFactory factory = CapturingFactory.ok(new byte[]{1, 2, 3});

        try {
            downloader(factory, 2L).download(new OutboxMediaDownloader.Request(
                    "https://example.test",
                    "key",
                    "/module/media/large.jpg",
                    "large.jpg",
                    Files.createTempDirectory("wxo-outbox").toFile(),
                    0L,
                    false));
            fail("expected media download exceeds limit");
        } catch (IOException e) {
            assertEquals("media download exceeds limit", e.getMessage());
        }
        assertTrue(factory.connection.disconnected);
    }

    private static OutboxMediaDownloader downloader(CapturingFactory factory, long defaultLimitBytes) {
        return new OutboxMediaDownloader(factory, defaultLimitBytes);
    }

    private static final class CapturingFactory implements OutboxMediaDownloader.ConnectionFactory {
        private final int status;
        private final byte[] body;
        private final byte[] error;
        private URL url;
        private FakeConnection connection;

        private CapturingFactory(int status, byte[] body, byte[] error) {
            this.status = status;
            this.body = body;
            this.error = error;
        }

        static CapturingFactory ok(byte[] body) {
            return new CapturingFactory(200, body, new byte[0]);
        }

        static CapturingFactory error(int status, String body) {
            return new CapturingFactory(status, new byte[0], body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public HttpURLConnection open(URL url) {
            this.url = url;
            this.connection = new FakeConnection(url, status, body, error);
            return connection;
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        private final int status;
        private final byte[] body;
        private final byte[] error;
        private boolean disconnected;

        private FakeConnection(URL url, int status, byte[] body, byte[] error) {
            super(url);
            this.status = status;
            this.body = body;
            this.error = error;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(error);
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }
}
