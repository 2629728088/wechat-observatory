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

public final class WechatImageDownloadBridgeEnvironmentAdaptersTest {
    @Test
    public void classLoaderFallsBackWhenEnvironmentOrLoaderIsMissing() {
        assertSame(
                WechatImageDownloadBridge.class.getClassLoader(),
                WechatImageDownloadBridgeEnvironmentAdapters.classLoader(null));
        assertSame(
                WechatImageDownloadBridge.class.getClassLoader(),
                WechatImageDownloadBridgeEnvironmentAdapters.classLoader(new TestEnvironment(null)));
    }

    @Test
    public void classLoaderDelegatesWhenPresent() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertSame(
                classLoader,
                WechatImageDownloadBridgeEnvironmentAdapters.classLoader(new TestEnvironment(classLoader)));
    }

    @Test
    public void requesterCollaboratorsReturnNullWhenEnvironmentMissing() {
        assertNull(WechatImageDownloadBridgeEnvironmentAdapters.imageInfoIdResolver(null));
        assertNull(WechatImageDownloadBridgeEnvironmentAdapters.mainThreadRunner(null));
        assertNull(WechatImageDownloadBridgeEnvironmentAdapters.sleeper(null));
        assertNull(WechatImageDownloadBridgeEnvironmentAdapters.logger(null));
    }

    @Test
    public void requesterCollaboratorsDelegateToEnvironment() throws Exception {
        final AtomicBoolean callableCalled = new AtomicBoolean();
        final AtomicBoolean slept = new AtomicBoolean();
        final List<String> logs = new ArrayList<>();
        TestEnvironment environment = new TestEnvironment(getClass().getClassLoader()) {
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
        };

        assertEquals(30L, WechatImageDownloadBridgeEnvironmentAdapters
                .imageInfoIdResolver(environment)
                .resolve(10L, 20L));
        WechatImageDownloadBridgeEnvironmentAdapters
                .mainThreadRunner(environment)
                .run(new Callable<Void>() {
                    @Override
                    public Void call() {
                        return null;
                    }
                });
        WechatImageDownloadBridgeEnvironmentAdapters.sleeper(environment).sleep(123L);
        WechatImageDownloadBridgeEnvironmentAdapters.logger(environment).log("message");

        assertTrue(callableCalled.get());
        assertTrue(slept.get());
        assertEquals(1, logs.size());
        assertEquals("message", logs.get(0));
    }

    private static class TestEnvironment implements WechatImageDownloadBridge.Environment {
        private final ClassLoader classLoader;

        TestEnvironment(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public ClassLoader classLoader() {
            return classLoader;
        }

        @Override
        public long resolveImageInfoId(long localId, long serverId) {
            return 0L;
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
