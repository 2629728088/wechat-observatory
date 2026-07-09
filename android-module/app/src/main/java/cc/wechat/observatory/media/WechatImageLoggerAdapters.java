package cc.wechat.observatory.media;

final class WechatImageLoggerAdapters {
    private WechatImageLoggerAdapters() {
    }

    static WechatImageFileResolver.Logger fileLogger(final MediaResolver.Logger logger) {
        if (logger == null) {
            return null;
        }
        return new WechatImageFileResolver.Logger() {
            @Override
            public void log(String message) {
                logger.log(message);
            }
        };
    }

    static WechatImageReferenceResolver.Logger referenceLogger(final WechatImageFileResolver.Logger logger) {
        if (logger == null) {
            return null;
        }
        return new WechatImageReferenceResolver.Logger() {
            @Override
            public void log(String message) {
                logger.log(message);
            }
        };
    }
}
