package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadServiceEnvironmentAdaptersTest {
    @Test
    public void bridgeEnvironmentReturnsNullWhenEnvironmentMissing() {
        assertNull(WechatImageDownloadServiceEnvironmentAdapters.bridgeEnvironment(null));
    }

    @Test
    public void bridgeEnvironmentDelegatesRuntimeMethods() throws Exception {
        final ClassLoader classLoader = getClass().getClassLoader();
        final AtomicBoolean callableCalled = new AtomicBoolean();
        final AtomicBoolean slept = new AtomicBoolean();
        final List<String> logs = new ArrayList<>();

        WechatImageDownloadBridge.Environment adapter =
                WechatImageDownloadServiceEnvironmentAdapters.bridgeEnvironment(
                        new WechatImageDownloadService.Environment() {
                            @Override
                            public ClassLoader classLoader() {
                                return classLoader;
                            }

                            @Override
                            public long resolveImageInfoId(long localId, long serverId) {
                                assertEquals(10L, localId);
                                assertEquals(20L, serverId);
                                return 30L;
                            }

                            @Override
                            public void runOnMainThread(Callable<Void> callable) throws Exception {
                                callableCalled.set(true);
                                callable.call();
                            }

                            @Override
                            public void sleep(long millis) {
                                assertEquals(123L, millis);
                                slept.set(true);
                            }

                            @Override
                            public void log(String message) {
                                logs.add(message);
                            }
                        });

        assertSame(classLoader, adapter.classLoader());
        assertEquals(30L, adapter.resolveImageInfoId(10L, 20L));
        adapter.runOnMainThread(new Callable<Void>() {
            @Override
            public Void call() {
                return null;
            }
        });
        adapter.sleep(123L);
        adapter.log("message");

        assertTrue(callableCalled.get());
        assertTrue(slept.get());
        assertEquals(1, logs.size());
        assertEquals("message", logs.get(0));
    }
}
