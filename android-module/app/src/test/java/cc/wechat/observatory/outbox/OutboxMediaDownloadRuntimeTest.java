package cc.wechat.observatory.outbox;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class OutboxMediaDownloadRuntimeTest {
    @Test
    public void downloadsUsingEnvironmentAndMediaSpec() throws Exception {
        File cacheDir = Files.createTempDirectory("wxo-runtime").toFile();
        CapturingFactory factory = CapturingFactory.ok(new byte[]{7, 8, 9});
        OutboxMediaDownloadRuntime runtime = runtime(factory, environment(
                "https://bridge.test/root/",
                "key/slash",
                cacheDir,
                1024L));

        File file = runtime.download(OutboxMediaSpec.fromValues(
                "/api/media/photo.jpg?source=admin",
                "photo.jpg",
                0,
                0,
                "",
                "",
                0,
                0), false);

        assertEquals("https://bridge.test/root/module/media/photo.jpg?source=admin&api_key=key%2Fslash",
                factory.url.toString());
        assertEquals(cacheDir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
        assertTrue(file.getName().endsWith(".jpg"));
        assertArrayEquals(new byte[]{7, 8, 9}, Files.readAllBytes(file.toPath()));
    }

    @Test
    public void preservesFileActionNameWhenRequested() throws Exception {
        File cacheDir = Files.createTempDirectory("wxo-runtime").toFile();
        CapturingFactory factory = CapturingFactory.ok(new byte[]{1});
        OutboxMediaDownloadRuntime runtime = runtime(factory, environment(
                "https://bridge.test",
                "key",
                cacheDir,
                1024L));

        File file = runtime.download(OutboxMediaSpec.fromValues(
                "/module/media/raw.bin",
                "../report.txt?download=1",
                0,
                0,
                "",
                "",
                0,
                0), true);

        assertTrue(file.getName().endsWith("-report.txt"));
    }

    @Test
    public void rejectsMissingBridgeConfigBeforeOpeningConnection() throws Exception {
        CapturingFactory factory = CapturingFactory.ok(new byte[]{1});
        OutboxMediaDownloadRuntime runtime = runtime(factory, environment(
                "",
                "key",
                Files.createTempDirectory("wxo-runtime").toFile(),
                1024L));

        try {
            runtime.download(OutboxMediaSpec.fromValues(
                    "/module/media/photo.jpg",
                    "photo.jpg",
                    0,
                    0,
                    "",
                    "",
                    0,
                    0), false);
            fail("expected bridge config is missing");
        } catch (IOException e) {
            assertEquals("bridge config is missing", e.getMessage());
        }
        assertNull(factory.url);
    }

    private static OutboxMediaDownloadRuntime runtime(
            CapturingFactory factory,
            OutboxMediaDownloadRuntime.Environment environment) {
        return new OutboxMediaDownloadRuntime(new OutboxMediaDownloader(factory, 4096L), environment);
    }

    private static OutboxMediaDownloadRuntime.Environment environment(
            final String baseUrl,
            final String apiKey,
            final File cacheDir,
            final long limitBytes) {
        return new OutboxMediaDownloadRuntime.Environment() {
            @Override
            public String baseUrl() {
                return baseUrl;
            }

            @Override
            public String apiKey() {
                return apiKey;
            }

            @Override
            public File cacheDir() {
                return cacheDir;
            }

            @Override
            public long mediaUploadLimitBytes() {
                return limitBytes;
            }
        };
    }

    private static final class CapturingFactory implements OutboxMediaDownloader.ConnectionFactory {
        private final byte[] body;
        private URL url;

        private CapturingFactory(byte[] body) {
            this.body = body;
        }

        static CapturingFactory ok(byte[] body) {
            return new CapturingFactory(body);
        }

        @Override
        public HttpURLConnection open(URL url) {
            this.url = url;
            return new FakeConnection(url, body);
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        private final byte[] body;

        private FakeConnection(URL url, byte[] body) {
            super(url);
            this.body = body;
        }

        @Override
        public int getResponseCode() {
            return 200;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void disconnect() {
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
