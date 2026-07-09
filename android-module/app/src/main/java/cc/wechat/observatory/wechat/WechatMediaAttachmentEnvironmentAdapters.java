package cc.wechat.observatory.wechat;

import java.io.File;
import java.util.concurrent.Callable;

import cc.wechat.observatory.media.MediaAttachmentEnvironment;
import cc.wechat.observatory.media.MediaResolverRuntime;

final class WechatMediaAttachmentEnvironmentAdapters {
    private WechatMediaAttachmentEnvironmentAdapters() {
    }

    static MediaResolverRuntime.Environment mediaResolverEnvironment(
            final WechatMediaAttachmentServices.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new MediaResolverRuntime.Environment() {
            @Override
            public File appRoot() {
                return environment.appRoot();
            }

            @Override
            public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
                return environment.resolveMediaHint(type, msgId, msgSvrId, mediaHint);
            }

            @Override
            public void log(String message) {
                environment.log(message);
            }

            @Override
            public void onEmojiDiagnosticNeeded(String emojiMd5) {
                environment.onEmojiDiagnosticNeeded(emojiMd5);
            }
        };
    }

    static MediaResolverRuntime.EmojiInfoProvider emojiInfoProvider(
            final WechatMediaAttachmentServices.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new MediaResolverRuntime.EmojiInfoProvider() {
            @Override
            public Object load(String emojiMd5) {
                return environment.loadEmojiInfo(emojiMd5);
            }
        };
    }

    static WechatImageDownloadService.Environment imageDownloadServiceEnvironment(
            final WechatMediaAttachmentServices.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new WechatImageDownloadService.Environment() {
            @Override
            public ClassLoader classLoader() {
                return environment.classLoader();
            }

            @Override
            public long resolveImageInfoId(long localId, long serverId) {
                return environment.resolveImageInfoId(localId, serverId);
            }

            @Override
            public void runOnMainThread(Callable<Void> callable) throws Exception {
                environment.runOnMainThread(callable);
            }

            @Override
            public void sleep(long millis) {
                environment.sleep(millis);
            }

            @Override
            public void log(String message) {
                environment.log(message);
            }
        };
    }

    static MediaAttachmentEnvironment.ImageDownloadRequester imageDownloadRequester(
            final WechatMediaAttachmentServices.ImageDownloadRequester imageDownloadRequester) {
        if (imageDownloadRequester == null) {
            return null;
        }
        return new MediaAttachmentEnvironment.ImageDownloadRequester() {
            @Override
            public boolean request(long localId, long serverId, String talker) {
                return imageDownloadRequester.request(localId, serverId, talker);
            }
        };
    }

    static MediaAttachmentEnvironment.Encoder encoder(
            final WechatMediaAttachmentServices.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new MediaAttachmentEnvironment.Encoder() {
            @Override
            public String encode(byte[] bytes) {
                return environment.encode(bytes);
            }
        };
    }

    static MediaAttachmentEnvironment.Logger logger(
            final WechatMediaAttachmentServices.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new MediaAttachmentEnvironment.Logger() {
            @Override
            public void log(String message) {
                environment.log(message);
            }
        };
    }
}
