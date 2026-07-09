package cc.wechat.observatory.wechat;

import cc.wechat.observatory.media.MediaHintRuntime;

final class WechatMediaRuntimeFactory {
    interface HintResolver {
        String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint);

        long resolveImageInfoId(long localId, long serverId);
    }

    private WechatMediaRuntimeFactory() {
    }

    static MediaHintRuntime mediaHintRuntime(final WechatMediaRuntime.Environment environment) {
        return new MediaHintRuntime(
                WechatMediaRuntimeEnvironmentAdapters.mediaHintEnvironment(environment));
    }

    static WechatMediaAttachmentServices mediaAttachmentServices(
            final WechatMediaRuntime.Environment environment,
            final HintResolver hintResolver) {
        if (environment == null) {
            return null;
        }
        return new WechatMediaAttachmentServices(
                WechatMediaRuntimeEnvironmentAdapters.mediaAttachmentEnvironment(environment, hintResolver));
    }
}
