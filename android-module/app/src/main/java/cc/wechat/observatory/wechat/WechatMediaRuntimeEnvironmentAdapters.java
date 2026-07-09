package cc.wechat.observatory.wechat;

import java.io.File;
import java.util.concurrent.Callable;

import cc.wechat.observatory.media.MediaHintRuntime;

final class WechatMediaRuntimeEnvironmentAdapters {
    private WechatMediaRuntimeEnvironmentAdapters() {
    }

    static MediaHintRuntime.Environment mediaHintEnvironment(
            final WechatMediaRuntime.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new MediaHintRuntime.Environment() {
            @Override
            public Object database() {
                return environment.database();
            }

            @Override
            public void log(String message) {
                environment.log(message);
            }
        };
    }

    static WechatMediaAttachmentServices.Environment mediaAttachmentEnvironment(
            final WechatMediaRuntime.Environment environment,
            final WechatMediaRuntimeFactory.HintResolver hintResolver) {
        if (environment == null) {
            return null;
        }
        return new WechatMediaAttachmentServices.Environment() {
            @Override
            public File appRoot() {
                return environment.appRoot();
            }

            @Override
            public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
                return hintResolver == null
                        ? mediaHint
                        : hintResolver.resolveMediaHint(type, msgId, msgSvrId, mediaHint);
            }

            @Override
            public ClassLoader classLoader() {
                return environment.classLoader();
            }

            @Override
            public long resolveImageInfoId(long localId, long serverId) {
                return hintResolver == null ? 0L : hintResolver.resolveImageInfoId(localId, serverId);
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
            public String encode(byte[] bytes) {
                return environment.encode(bytes);
            }

            @Override
            public void log(String message) {
                environment.log(message);
            }

            @Override
            public Object loadEmojiInfo(String emojiMd5) {
                return environment.loadEmojiInfo(emojiMd5);
            }

            @Override
            public void onEmojiDiagnosticNeeded(String emojiMd5) {
                environment.onEmojiDiagnosticNeeded(emojiMd5);
            }
        };
    }
}
