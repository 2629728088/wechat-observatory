package cc.wechat.observatory.media;

import static cc.wechat.observatory.util.Strings.shortError;

public final class MediaHintRuntime {
    public interface Environment {
        Object database();

        void log(String message);
    }

    interface StoreFactory {
        MediaHintResolver.Store create(Object database);
    }

    private final Environment environment;
    private final StoreFactory storeFactory;

    public MediaHintRuntime(Environment environment) {
        this(environment, new StoreFactory() {
            @Override
            public MediaHintResolver.Store create(Object database) {
                return new WechatMediaHintStore(database);
            }
        });
    }

    MediaHintRuntime(Environment environment, StoreFactory storeFactory) {
        this.environment = environment;
        this.storeFactory = storeFactory;
    }

    public String resolve(int type, Long msgId, Long msgSvrId, String fallbackHint) {
        return MediaHintResolver.resolve(store(), type, msgId, msgSvrId, fallbackHint);
    }

    public long resolveImageInfoId(long localId, long serverId) {
        try {
            return MediaHintResolver.resolveImageInfoId(
                    store(),
                    localId > 0L ? Long.valueOf(localId) : null,
                    serverId > 0L ? Long.valueOf(serverId) : null);
        } catch (Throwable t) {
            log("image info id resolve failed msgId=" + localId
                    + " msgSvrId=" + serverId
                    + " error=" + shortError(rootCause(t)));
            return 0L;
        }
    }

    private MediaHintResolver.Store store() {
        if (environment == null || storeFactory == null) {
            return null;
        }
        return storeFactory.create(environment.database());
    }

    private void log(String message) {
        if (environment != null) {
            environment.log(message);
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        int depth = 0;
        while (current != null && current.getCause() != null && current.getCause() != current && depth < 8) {
            current = current.getCause();
            depth++;
        }
        return current == null ? t : current;
    }
}
