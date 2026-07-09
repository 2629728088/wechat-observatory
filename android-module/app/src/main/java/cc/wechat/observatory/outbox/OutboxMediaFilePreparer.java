package cc.wechat.observatory.outbox;

import org.json.JSONObject;

import java.io.File;

import cc.wechat.observatory.media.MediaFiles;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class OutboxMediaFilePreparer {
    public interface Downloader {
        File download(OutboxMediaSpec media, boolean preserveName) throws Exception;
    }

    public interface Logger {
        void log(String message);
    }

    public static final class PreparedMedia {
        public final OutboxMediaSpec media;
        public final File file;
        public final String error;
        private final Logger logger;
        private boolean retained;

        private PreparedMedia(OutboxMediaSpec media, File file, String error, Logger logger) {
            this.media = media;
            this.file = file;
            this.error = error;
            this.logger = logger;
        }

        public boolean ok() {
            return isBlank(error);
        }

        public void retain() {
            retained = true;
        }

        public void cleanup(String kind) {
            if (retained || file == null || !file.isFile()) {
                return;
            }
            if (!file.delete() && logger != null) {
                logger.log("delete outbox " + kind + " temp file skipped");
            }
        }

        private PreparedMedia withError(String error) {
            return new PreparedMedia(media, file, error, logger);
        }
    }

    private final Downloader downloader;
    private final Logger logger;

    public static OutboxMediaFilePreparer fromRuntime(
            final OutboxMediaDownloadRuntime runtime,
            Logger logger) {
        return new OutboxMediaFilePreparer(new Downloader() {
            @Override
            public File download(OutboxMediaSpec media, boolean preserveName) throws Exception {
                return runtime.download(media, preserveName);
            }
        }, logger);
    }

    public OutboxMediaFilePreparer(Downloader downloader, Logger logger) {
        this.downloader = downloader;
        this.logger = logger;
    }

    public PreparedMedia prepare(JSONObject item, String kind, boolean preserveName) throws Exception {
        return prepare(OutboxMediaSpec.from(item), kind, preserveName);
    }

    public PreparedMedia prepare(OutboxMediaSpec media, String kind, boolean preserveName) throws Exception {
        if (media == null || isBlank(media.mediaUrl)) {
            return new PreparedMedia(media, null, kind + " media_url is required", logger);
        }
        File file = downloader == null ? null : downloader.download(media, preserveName);
        PreparedMedia prepared = new PreparedMedia(media, file, "", logger);
        if (file == null || !file.isFile() || file.length() <= 0) {
            return prepared.withError(kind + " media download produced empty file");
        }
        return prepared;
    }

    public PreparedMedia prepareVoice(JSONObject item) throws Exception {
        return prepareVoice(OutboxMediaSpec.from(item));
    }

    public PreparedMedia prepareVoice(OutboxMediaSpec media) throws Exception {
        PreparedMedia prepared = prepare(media, "voice", false);
        if (!prepared.ok()) {
            return prepared;
        }
        if (!MediaFiles.isSupportedVoiceMediaFile(prepared.file, prepared.media.mediaName)) {
            return prepared.withError("voice media must be AMR or SILK");
        }
        return prepared;
    }
}
