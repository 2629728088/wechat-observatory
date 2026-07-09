package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import cc.wechat.observatory.media.MediaHintRuntime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatMediaRuntimeEnvironmentAdaptersTest {
    @Test
    public void mediaHintEnvironmentReturnsNullWhenEnvironmentMissing() {
        assertNull(WechatMediaRuntimeEnvironmentAdapters.mediaHintEnvironment(null));
    }

    @Test
    public void mediaHintEnvironmentDelegatesDatabaseAndLogs() {
        Object database = new Object();
        List<String> logs = new ArrayList<>();

        MediaHintRuntime.Environment adapter =
                WechatMediaRuntimeEnvironmentAdapters.mediaHintEnvironment(
                        new TestRuntimeEnvironment(database, null, logs));

        assertSame(database, adapter.database());
        adapter.log("message");
        assertEquals(1, logs.size());
        assertEquals("message", logs.get(0));
    }

    @Test
    public void mediaAttachmentEnvironmentReturnsNullWhenEnvironmentMissing() {
        assertNull(WechatMediaRuntimeEnvironmentAdapters.mediaAttachmentEnvironment(null, null));
    }

    @Test
    public void mediaAttachmentEnvironmentFallsBackWhenHintResolverMissing() {
        WechatMediaAttachmentServices.Environment adapter =
                WechatMediaRuntimeEnvironmentAdapters.mediaAttachmentEnvironment(
                        new TestRuntimeEnvironment(null, null, new ArrayList<String>()),
                        null);

        assertEquals("fallback.jpg", adapter.resolveMediaHint(
                3, Long.valueOf(10L), Long.valueOf(20L), "fallback.jpg"));
        assertEquals(0L, adapter.resolveImageInfoId(10L, 20L));
    }

    @Test
    public void mediaAttachmentEnvironmentDelegatesHintResolverAndRuntimeCollaborators() throws Exception {
        Object database = new Object();
        File appRoot = Files.createTempDirectory("wxo-runtime-adapter").toFile();
        List<String> logs = new ArrayList<>();
        AtomicBoolean callableCalled = new AtomicBoolean();
        AtomicBoolean slept = new AtomicBoolean();
        AtomicBoolean diagnosed = new AtomicBoolean();
        ClassLoader classLoader = getClass().getClassLoader();
        final byte[] expected = new byte[]{1, 2, 3};
        TestRuntimeEnvironment environment = new TestRuntimeEnvironment(database, appRoot, logs) {
            @Override
            public ClassLoader classLoader() {
                return classLoader;
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
            public String encode(byte[] bytes) {
                assertArrayEquals(expected, bytes);
                return "encoded";
            }

            @Override
            public void onEmojiDiagnosticNeeded(String emojiMd5) {
                assertEquals("emoji", emojiMd5);
                diagnosed.set(true);
            }
        };

        WechatMediaAttachmentServices.Environment adapter =
                WechatMediaRuntimeEnvironmentAdapters.mediaAttachmentEnvironment(
                        environment,
                        new WechatMediaRuntimeFactory.HintResolver() {
                            @Override
                            public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
                                assertEquals(3, type);
                                assertEquals(Long.valueOf(10L), msgId);
                                assertEquals(Long.valueOf(20L), msgSvrId);
                                assertEquals("hint", mediaHint);
                                return "resolved";
                            }

                            @Override
                            public long resolveImageInfoId(long localId, long serverId) {
                                assertEquals(10L, localId);
                                assertEquals(20L, serverId);
                                return 30L;
                            }
                        });

        assertEquals(appRoot.getCanonicalFile(), adapter.appRoot().getCanonicalFile());
        assertSame(classLoader, adapter.classLoader());
        assertEquals("resolved", adapter.resolveMediaHint(3, Long.valueOf(10L), Long.valueOf(20L), "hint"));
        assertEquals(30L, adapter.resolveImageInfoId(10L, 20L));
        adapter.runOnMainThread(new Callable<Void>() {
            @Override
            public Void call() {
                return null;
            }
        });
        adapter.sleep(123L);
        assertEquals("encoded", adapter.encode(expected));
        adapter.log("message");
        adapter.onEmojiDiagnosticNeeded("emoji");

        assertTrue(callableCalled.get());
        assertTrue(slept.get());
        assertTrue(diagnosed.get());
        assertEquals(1, logs.size());
        assertEquals("message", logs.get(0));
    }

    private static class TestRuntimeEnvironment implements WechatMediaRuntime.Environment {
        private final Object database;
        private final File appRoot;
        private final List<String> logs;

        TestRuntimeEnvironment(Object database, File appRoot, List<String> logs) {
            this.database = database;
            this.appRoot = appRoot;
            this.logs = logs;
        }

        @Override
        public Object database() {
            return database;
        }

        @Override
        public File appRoot() {
            return appRoot;
        }

        @Override
        public ClassLoader classLoader() {
            return getClass().getClassLoader();
        }

        @Override
        public void runOnMainThread(Callable<Void> callable) throws Exception {
            callable.call();
        }

        @Override
        public void sleep(long millis) {
        }

        @Override
        public String encode(byte[] bytes) {
            return "";
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public void onEmojiDiagnosticNeeded(String emojiMd5) {
        }
    }
}
