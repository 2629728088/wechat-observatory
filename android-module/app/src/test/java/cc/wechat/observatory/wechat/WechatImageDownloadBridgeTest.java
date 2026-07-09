package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadBridgeTest {
    @Test
    public void requestWiresEnvironmentIntoRequester() {
        ClassLoader loader = getClass().getClassLoader();
        AtomicInteger runnerCalls = new AtomicInteger();
        AtomicLong resolvedInfoId = new AtomicLong();
        AtomicLong sleptMs = new AtomicLong();
        List<String> logs = new ArrayList<>();
        CaptureRequester requester = new CaptureRequester();

        boolean requested = new WechatImageDownloadBridge(
                new WechatImageDownloadBridge.Environment() {
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
                        runnerCalls.incrementAndGet();
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
                captureFactory(requester, 1234L))
                .request(10L, 20L, "talker");

        assertTrue(requested);
        assertSame(loader, requester.classLoader);
        assertEquals(10L, requester.localId);
        assertEquals(20L, requester.serverId);
        assertEquals("talker", requester.talker);
        assertEquals(30L, resolvedInfoId.get());
        assertEquals(1, runnerCalls.get());
        assertEquals(1234L, sleptMs.get());
        assertTrue(logs.contains("bridge-log"));
    }

    @Test
    public void requestFallsBackToBridgeClassLoaderWhenEnvironmentHasNoLoader() {
        AtomicReference<ClassLoader> loader = new AtomicReference<>();

        boolean requested = new WechatImageDownloadBridge(
                new NoopEnvironment(null),
                new WechatImageDownloadBridge.RequesterFactory() {
                    @Override
                    public WechatImageDownloadBridge.Requester create(
                            WechatImageDownloadRequester.ImageInfoIdResolver imageInfoIdResolver,
                            WechatImageDownloadRequester.MainThreadRunner mainThreadRunner,
                            WechatImageDownloadRequester.Sleeper sleeper,
                            WechatImageDownloadRequester.Logger logger) {
                        return new WechatImageDownloadBridge.Requester() {
                            @Override
                            public boolean request(ClassLoader classLoader, long localId, long serverId, String talker) {
                                loader.set(classLoader);
                                return true;
                            }
                        };
                    }
                })
                .request(1L, 2L, "talker");

        assertTrue(requested);
        assertSame(WechatImageDownloadBridge.class.getClassLoader(), loader.get());
    }

    @Test
    public void requestReturnsFalseWithoutEnvironment() {
        boolean requested = new WechatImageDownloadBridge(null, captureFactory(new CaptureRequester(), 1L))
                .request(1L, 2L, "talker");

        assertFalse(requested);
    }

    private static WechatImageDownloadBridge.RequesterFactory captureFactory(final CaptureRequester requester, final long sleepMs) {
        return new WechatImageDownloadBridge.RequesterFactory() {
            @Override
            public WechatImageDownloadBridge.Requester create(
                    final WechatImageDownloadRequester.ImageInfoIdResolver imageInfoIdResolver,
                    final WechatImageDownloadRequester.MainThreadRunner mainThreadRunner,
                    final WechatImageDownloadRequester.Sleeper sleeper,
                    final WechatImageDownloadRequester.Logger logger) {
                return new WechatImageDownloadBridge.Requester() {
                    @Override
                    public boolean request(ClassLoader classLoader, long localId, long serverId, String talker) {
                        requester.classLoader = classLoader;
                        requester.localId = localId;
                        requester.serverId = serverId;
                        requester.talker = talker;
                        long resolved = imageInfoIdResolver.resolve(localId, serverId);
                        try {
                            mainThreadRunner.run(new Callable<Void>() {
                                @Override
                                public Void call() {
                                    return null;
                                }
                            });
                        } catch (Exception e) {
                            return false;
                        }
                        sleeper.sleep(sleepMs);
                        logger.log("bridge-log");
                        return resolved > 0L;
                    }
                };
            }
        };
    }

    private static final class CaptureRequester {
        ClassLoader classLoader;
        long localId;
        long serverId;
        String talker;
    }

    private static final class NoopEnvironment implements WechatImageDownloadBridge.Environment {
        private final ClassLoader classLoader;

        NoopEnvironment(ClassLoader classLoader) {
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
        public void runOnMainThread(Callable<Void> callable) {
        }

        @Override
        public void sleep(long millis) {
        }

        @Override
        public void log(String message) {
        }
    }
}
