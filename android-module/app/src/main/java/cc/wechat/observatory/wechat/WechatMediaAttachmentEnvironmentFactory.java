package cc.wechat.observatory.wechat;

import cc.wechat.observatory.media.MediaAttachmentEnvironment;
import cc.wechat.observatory.media.MediaResolverRuntime;

final class WechatMediaAttachmentEnvironmentFactory {
    private WechatMediaAttachmentEnvironmentFactory() {
    }

    static MediaAttachmentEnvironment create(
            final WechatMediaAttachmentServices.Environment environment,
            WechatMediaAttachmentServices.ImageDownloadRequester imageDownloadRequester) {
        return new MediaAttachmentEnvironment(
                mediaResolverRuntime(environment),
                WechatMediaAttachmentEnvironmentAdapters.imageDownloadRequester(imageDownloadRequester),
                WechatMediaAttachmentEnvironmentAdapters.encoder(environment),
                WechatMediaAttachmentEnvironmentAdapters.logger(environment));
    }

    static WechatMediaAttachmentServices.ImageDownloadRequester defaultImageDownloadRequester(
            final WechatMediaAttachmentServices.Environment environment) {
        return WechatImageDownloadServiceRequester.create(environment);
    }

    private static MediaResolverRuntime mediaResolverRuntime(
            final WechatMediaAttachmentServices.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new MediaResolverRuntime(
                WechatMediaAttachmentEnvironmentAdapters.mediaResolverEnvironment(environment),
                WechatMediaAttachmentEnvironmentAdapters.emojiInfoProvider(environment));
    }
}
