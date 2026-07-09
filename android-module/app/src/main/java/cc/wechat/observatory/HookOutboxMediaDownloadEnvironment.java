package cc.wechat.observatory;

import java.io.File;
import java.io.IOException;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.outbox.OutboxMediaDownloadRuntime;

final class HookOutboxMediaDownloadEnvironment implements OutboxMediaDownloadRuntime.Environment {
    interface CacheDirProvider {
        File cacheDir() throws IOException;
    }

    private final String baseUrl;
    private final String apiKey;
    private final long mediaUploadLimitBytes;
    private final CacheDirProvider cacheDirProvider;

    static HookOutboxMediaDownloadEnvironment fromConfig(
            BridgeConfig config,
            CacheDirProvider cacheDirProvider) {
        return new HookOutboxMediaDownloadEnvironment(
                config == null ? "" : config.baseUrl,
                config == null ? "" : config.apiKey,
                config == null ? 0L : config.mediaUploadLimitBytes,
                cacheDirProvider);
    }

    HookOutboxMediaDownloadEnvironment(
            String baseUrl,
            String apiKey,
            long mediaUploadLimitBytes,
            CacheDirProvider cacheDirProvider) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.mediaUploadLimitBytes = mediaUploadLimitBytes;
        this.cacheDirProvider = cacheDirProvider;
    }

    @Override
    public String baseUrl() {
        return baseUrl == null ? "" : baseUrl;
    }

    @Override
    public String apiKey() {
        return apiKey == null ? "" : apiKey;
    }

    @Override
    public File cacheDir() throws IOException {
        if (cacheDirProvider == null) {
            throw new IOException("cache dir is not available");
        }
        return cacheDirProvider.cacheDir();
    }

    @Override
    public long mediaUploadLimitBytes() {
        return mediaUploadLimitBytes;
    }
}
