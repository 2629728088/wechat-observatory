package cc.wechat.observatory;

import java.io.File;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.media.EmojiMediaParser;
import cc.wechat.observatory.media.ImageDownloadRequestTracker;
import cc.wechat.observatory.media.MediaRetryRuntime;
import cc.wechat.observatory.media.MediaUploadTracker;
import cc.wechat.observatory.media.OutboundMediaNames;
import cc.wechat.observatory.outbox.OutboxMediaDownloadRuntime;
import cc.wechat.observatory.outbox.OutboxMediaFilePreparer;

final class HookMediaServices {
    private static final ImageDownloadRequestTracker IMAGE_DOWNLOAD_REQUEST_TRACKER =
            new ImageDownloadRequestTracker(1024);

    private HookMediaServices() {
    }

    static HookMediaAttachmentController attachmentController(
            HookWechatMediaRuntimeProvider.AppRootProvider appRootProvider,
            HookWechatMediaRuntimeProvider.ClassLoaderProvider classLoaderProvider,
            HookWechatMediaRuntimeProvider.MainThreadRunner mainThreadRunner,
            HookWechatMediaRuntimeProvider.Sleeper sleeper,
            HookWechatMediaRuntimeProvider.Encoder encoder,
            HookWechatMediaRuntimeProvider.Logger logger,
            HookWechatMediaRuntimeProvider.EmojiInfoLoader emojiInfoLoader,
            HookWechatMediaRuntimeProvider.EmojiDiagnosticReporter emojiDiagnosticReporter) {
        return new HookMediaAttachmentController(attachmentBridge(
                appRootProvider,
                classLoaderProvider,
                mainThreadRunner,
                sleeper,
                encoder,
                logger,
                emojiInfoLoader,
                emojiDiagnosticReporter));
    }

    static HookMediaAttachmentBridge attachmentBridge(
            HookWechatMediaRuntimeProvider.AppRootProvider appRootProvider,
            HookWechatMediaRuntimeProvider.ClassLoaderProvider classLoaderProvider,
            HookWechatMediaRuntimeProvider.MainThreadRunner mainThreadRunner,
            HookWechatMediaRuntimeProvider.Sleeper sleeper,
            HookWechatMediaRuntimeProvider.Encoder encoder,
            HookWechatMediaRuntimeProvider.Logger logger,
            HookWechatMediaRuntimeProvider.EmojiInfoLoader emojiInfoLoader,
            HookWechatMediaRuntimeProvider.EmojiDiagnosticReporter emojiDiagnosticReporter) {
        return new HookMediaAttachmentBridge(
                new HookWechatMediaRuntimeProvider(
                        IMAGE_DOWNLOAD_REQUEST_TRACKER,
                        appRootProvider,
                        classLoaderProvider,
                        mainThreadRunner,
                        sleeper,
                        encoder,
                        logger,
                        emojiInfoLoader,
                        emojiDiagnosticReporter),
                logger == null ? null : new HookMediaAttachmentBridge.Logger() {
                    @Override
                    public void log(String message) {
                        logger.log(message);
                    }
                });
    }

    static HookMediaRetryEnvironment retryEnvironment(
            HookMediaRetryEnvironmentFactory.ConfigLoader configLoader,
            HookMediaRetryEnvironmentFactory.DatabaseProvider databaseProvider,
            HookMediaRetryEnvironmentFactory.TargetUserChecker targetUserChecker,
            HookMediaRetryEnvironmentFactory.RuntimeIdentityBinder runtimeIdentityBinder,
            HookMediaRetryEnvironmentFactory.RegistrationEnsurer registrationEnsurer,
            HookMediaRetryEnvironmentFactory.Sleeper sleeper,
            HookMediaRetryEnvironmentFactory.Logger logger,
            HookMediaRetryEnvironment.MessageBridge messageBridge) {
        return new HookMediaRetryEnvironmentFactory(
                configLoader,
                databaseProvider,
                targetUserChecker,
                runtimeIdentityBinder,
                registrationEnsurer,
                sleeper,
                logger)
                .create(messageBridge);
    }

    static MediaRetryRuntime mediaRetryRuntime(MediaRetryRuntime.Environment environment) {
        return new MediaRetryRuntime(
                new MediaUploadTracker(1024),
                new long[]{3000L, 10000L, 30000L, 120000L},
                environment);
    }

    static HookMediaRetryScheduler mediaRetryScheduler(
            MediaRetryRuntime.Environment environment,
            HookMediaAttachmentController mediaAttachmentController) {
        return new HookMediaRetryScheduler(mediaRetryRuntime(environment), mediaAttachmentController);
    }

    static String emojiMd5FromWechatContent(String talker, String content) {
        return EmojiMediaParser.md5FromWechatContent(talker, content);
    }

    static String videoBaseName(File file) {
        return OutboundMediaNames.videoBaseName(file);
    }

    static String voiceBaseName(File file) {
        return OutboundMediaNames.voiceBaseName(file);
    }

    static OutboxMediaFilePreparer outboxMediaFilePreparer(
            BridgeConfig config,
            HookOutboxMediaDownloadEnvironment.CacheDirProvider cacheDirProvider,
            OutboxMediaFilePreparer.Logger logger) {
        return OutboxMediaFilePreparer.fromRuntime(
                new OutboxMediaDownloadRuntime(
                        HookOutboxMediaDownloadEnvironment.fromConfig(config, cacheDirProvider)),
                logger);
    }
}
