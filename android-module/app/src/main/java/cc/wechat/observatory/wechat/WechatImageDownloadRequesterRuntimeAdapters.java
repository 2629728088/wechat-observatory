package cc.wechat.observatory.wechat;

final class WechatImageDownloadRequesterRuntimeAdapters {
    private WechatImageDownloadRequesterRuntimeAdapters() {
    }

    static WechatImageDownloadRuntime.ImageInfoIdResolver imageInfoIdResolver(final long imageInfoId) {
        return new WechatImageDownloadRuntime.ImageInfoIdResolver() {
            @Override
            public long resolve(ClassLoader classLoader, long msgId, long msgSvrId, String talker) {
                return imageInfoId;
            }
        };
    }

    static WechatImageDownloadRuntime.Logger logger(final WechatImageDownloadRequester.Logger logger) {
        return new WechatImageDownloadRuntime.Logger() {
            @Override
            public void log(String message) {
                if (logger != null) {
                    logger.log(message);
                }
            }
        };
    }
}
