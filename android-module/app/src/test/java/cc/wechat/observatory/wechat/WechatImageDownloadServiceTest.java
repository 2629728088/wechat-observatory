package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadServiceTest {
    @Test
    public void requestDelegatesEnvironmentIntoBridge() {
        final ClassLoader loader = getClass().getClassLoader();
        final AtomicInteger mainThreadRuns = new AtomicInteger();
        final AtomicLong resolvedInfoId = new AtomicLong();
        final AtomicLong sleptMs = new AtomicLong();
        final List<String> logs = new ArrayList<>();
        final CaptureBridge bridge = new CaptureBridge();

        boolean requested = new WechatImageDownloadService(
                new WechatImageDownloadService.Environment() {
                    @Override
                    public ClassLoader classLoader() {
                        return loader;
                    }

                    @Override
                    public long resolveImageInfoId(long localId, long serverId) {
                        resolvedInfoId.set(localId + serverId);
                        return resolvedInfoId.get();
                    }

                    @Override
                    public void runOnMainThread(Callable<Void> callable) throws Exception {
                        mainThreadRuns.incrementAndGet();
                        callable.call();
                    }

                    @Override
                    public void sleep(long millis) {
                        sleptMs.set(millis);
                    }

                    @Override
                    public void log(String message) {
                        logs.add(message);
                    }
                },
                new WechatImageDownloadService.BridgeFactory() {
                    @Override
                    public WechatImageDownloadService.Bridge create(final WechatImageDownloadBridge.Environment environment) {
                        return new WechatImageDownloadService.Bridge() {
                            @Override
                            public boolean request(long localId, long serverId, String talker) {
                                bridge.classLoader = environment.classLoader();
                                bridge.localId = localId;
                                bridge.serverId = serverId;
                                bridge.talker = talker;
                                long imageInfoId = environment.resolveImageInfoId(localId, serverId);
                                try {
                                    environment.runOnMainThread(new Callable<Void>() {
                                        @Override
                                        public Void call() {
                                            return null;
                                        }
                                    });
                                } catch (Exception e) {
                                    return false;
                                }
                                environment.sleep(789L);
                                environment.log("service-log");
                                return imageInfoId > 0L;
                            }
                        };
                    }
                })
                .request(10L, 20L, "talker");

        assertTrue(requested);
        assertSame(loader, bridge.classLoader);
        assertEquals(10L, bridge.localId);
        assertEquals(20L, bridge.serverId);
        assertEquals("talker", bridge.talker);
        assertEquals(30L, resolvedInfoId.get());
        assertEquals(1, mainThreadRuns.get());
        assertEquals(789L, sleptMs.get());
        assertTrue(logs.contains("service-log"));
    }

    @Test
    public void requestReturnsFalseWithoutEnvironment() {
        boolean requested = new WechatImageDownloadService(null)
                .request(1L, 2L, "talker");

        assertFalse(requested);
    }

    private static final class CaptureBridge {
        ClassLoader classLoader;
        long localId;
        long serverId;
        String talker;
    }
}
