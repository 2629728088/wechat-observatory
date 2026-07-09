package cc.wechat.observatory.wechat;

final class WechatImageDownloadServiceRequester implements WechatMediaAttachmentServices.ImageDownloadRequester {
    private final WechatImageDownloadService service;

    private WechatImageDownloadServiceRequester(WechatImageDownloadService service) {
        this.service = service;
    }

    static WechatMediaAttachmentServices.ImageDownloadRequester create(
            WechatMediaAttachmentServices.Environment environment) {
        if (environment == null) {
            return null;
        }
        return new WechatImageDownloadServiceRequester(new WechatImageDownloadService(
                WechatMediaAttachmentEnvironmentAdapters.imageDownloadServiceEnvironment(environment)));
    }

    static WechatMediaAttachmentServices.ImageDownloadRequester fromService(WechatImageDownloadService service) {
        if (service == null) {
            return null;
        }
        return new WechatImageDownloadServiceRequester(service);
    }

    @Override
    public boolean request(long localId, long serverId, String talker) {
        return service != null && service.request(localId, serverId, talker);
    }
}
