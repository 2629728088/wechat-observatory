package cc.wechat.observatory;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class HookOutboxMediaDownloadEnvironmentTest {
    @Test
    public void exposesConfiguredDownloadSettingsAndCacheDir() throws Exception {
        File cacheDir = Files.createTempDirectory("wxo-hook-outbox-media").toFile();
        AtomicInteger cacheCalls = new AtomicInteger();
        HookOutboxMediaDownloadEnvironment environment = new HookOutboxMediaDownloadEnvironment(
                "https://bridge.test",
                "api-key",
                4096L,
                new HookOutboxMediaDownloadEnvironment.CacheDirProvider() {
                    @Override
                    public File cacheDir() {
                        cacheCalls.incrementAndGet();
                        return cacheDir;
                    }
                });

        assertEquals("https://bridge.test", environment.baseUrl());
        assertEquals("api-key", environment.apiKey());
        assertEquals(4096L, environment.mediaUploadLimitBytes());
        assertEquals(cacheDir.getCanonicalFile(), environment.cacheDir().getCanonicalFile());
        assertEquals(1, cacheCalls.get());
    }

    @Test
    public void nullStringsBecomeEmptyValues() {
        HookOutboxMediaDownloadEnvironment environment = new HookOutboxMediaDownloadEnvironment(
                null,
                null,
                0L,
                null);

        assertEquals("", environment.baseUrl());
        assertEquals("", environment.apiKey());
        assertEquals(0L, environment.mediaUploadLimitBytes());
    }

    @Test
    public void missingCacheDirProviderFailsClearly() throws Exception {
        HookOutboxMediaDownloadEnvironment environment = new HookOutboxMediaDownloadEnvironment(
                "https://bridge.test",
                "api-key",
                4096L,
                null);

        try {
            environment.cacheDir();
            fail("expected cache dir is not available");
        } catch (IOException e) {
            assertEquals("cache dir is not available", e.getMessage());
        }
    }
}
