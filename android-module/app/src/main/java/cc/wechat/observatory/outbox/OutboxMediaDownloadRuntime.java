package cc.wechat.observatory.outbox;

import java.io.File;
import java.io.IOException;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class OutboxMediaDownloadRuntime {
    public interface Environment {
        String baseUrl();

        String apiKey();

        File cacheDir() throws IOException;

        long mediaUploadLimitBytes();
    }

    private final OutboxMediaDownloader downloader;
    private final Environment environment;

    public OutboxMediaDownloadRuntime(Environment environment) {
        this(new OutboxMediaDownloader(), environment);
    }

    OutboxMediaDownloadRuntime(OutboxMediaDownloader downloader, Environment environment) {
        this.downloader = downloader == null ? new OutboxMediaDownloader() : downloader;
        this.environment = environment;
    }

    public File download(OutboxMediaSpec media, boolean preserveName) throws Exception {
        String baseUrl = environment == null ? "" : environment.baseUrl();
        String apiKey = environment == null ? "" : environment.apiKey();
        if (media == null || environment == null
                || isBlank(baseUrl)
                || isBlank(apiKey)) {
            throw new IOException("bridge config is missing");
        }
        return downloader.download(new OutboxMediaDownloader.Request(
                baseUrl,
                apiKey,
                media.mediaUrl,
                media.mediaName,
                environment.cacheDir(),
                environment.mediaUploadLimitBytes(),
                preserveName));
    }
}
