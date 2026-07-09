package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadServiceRequesterTest {
    @Test
    public void createReturnsNullWhenEnvironmentMissing() {
        assertNull(WechatImageDownloadServiceRequester.create(null));
    }

    @Test
    public void fromServiceReturnsNullWhenServiceMissing() {
        assertNull(WechatImageDownloadServiceRequester.fromService(null));
    }

    @Test
    public void requesterDelegatesToImageDownloadService() {
        final AtomicInteger bridgeCalls = new AtomicInteger();
        WechatMediaAttachmentServices.ImageDownloadRequester requester =
                WechatImageDownloadServiceRequester.fromService(new WechatImageDownloadService(
                        new TestDownloadEnvironment(),
                        new WechatImageDownloadService.BridgeFactory() {
                            @Override
                            public WechatImageDownloadService.Bridge create(WechatImageDownloadBridge.Environment environment) {
                                return new WechatImageDownloadService.Bridge() {
                                    @Override
                                    public boolean request(long localId, long serverId, String talker) {
                                        bridgeCalls.incrementAndGet();
                                        return localId == 10L && serverId == 20L && "talker".equals(talker);
                                    }
                                };
                            }
                        }));

        assertTrue(requester.request(10L, 20L, "talker"));
        assertFalse(requester.request(11L, 20L, "talker"));
        assertEquals(2, bridgeCalls.get());
    }

    private static final class TestDownloadEnvironment implements WechatImageDownloadService.Environment {
        @Override
        public ClassLoader classLoader() {
            return getClass().getClassLoader();
        }

        @Override
        public long resolveImageInfoId(long localId, long serverId) {
            return localId + serverId;
        }

        @Override
        public void runOnMainThread(Callable<Void> callable) throws Exception {
            callable.call();
        }

        @Override
        public void sleep(long millis) {
        }

        @Override
        public void log(String message) {
        }
    }
}
