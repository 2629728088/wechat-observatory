package cc.wechat.observatory;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import cc.wechat.observatory.media.ImageDownloadRequestTracker;
import cc.wechat.observatory.wechat.WechatMediaRuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class HookWechatMediaRuntimeProviderTest {
    @Test
    public void environmentDelegatesHookRuntimeDependencies() throws Exception {
        Object database = new Object();
        File appRoot = new File("app-root");
        ClassLoader classLoader = getClass().getClassLoader();
        CapturingCallbacks callbacks = new CapturingCallbacks();
        HookWechatMediaRuntimeProvider provider = new HookWechatMediaRuntimeProvider(
                new ImageDownloadRequestTracker(1024),
                new FixedAppRootProvider(appRoot),
                new FixedClassLoaderProvider(classLoader),
                callbacks,
                callbacks,
                callbacks,
                callbacks,
                callbacks,
                callbacks);

        WechatMediaRuntimeEnvironment environment = provider.environment(database);

        assertSame(database, environment.database());
        assertSame(appRoot, environment.appRoot());
        assertSame(classLoader, environment.classLoader());
        environment.runOnMainThread(new Callable<Void>() {
            @Override
            public Void call() {
                callbacks.callableRan = true;
                return null;
            }
        });
        environment.sleep(123L);
        assertEquals("encoded:3", environment.encode(new byte[]{1, 2, 3}));
        environment.log("runtime-log");
        Object emojiInfo = environment.loadEmojiInfo("emoji-md5");
        environment.onEmojiDiagnosticNeeded("emoji-md5");

        assertEquals(1, callbacks.mainThreadCalls);
        assertEquals(123L, callbacks.sleepMillis);
        assertEquals("runtime-log", callbacks.logs.get(0));
        assertSame(callbacks.emojiInfo, emojiInfo);
        assertSame(classLoader, callbacks.emojiInfoClassLoader);
        assertEquals("emoji-md5", callbacks.loadedEmojiMd5);
        assertEquals("emoji-md5", callbacks.emojiMd5);
    }

    @Test
    public void environmentHasSafeFallbacksWhenCallbacksAreMissing() throws Exception {
        Object database = new Object();
        final boolean[] callableRan = new boolean[]{false};
        HookWechatMediaRuntimeProvider provider = new HookWechatMediaRuntimeProvider(
                new ImageDownloadRequestTracker(1024),
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        WechatMediaRuntimeEnvironment environment = provider.environment(database);

        assertSame(database, environment.database());
        assertNull(environment.appRoot());
        assertNotNull(environment.classLoader());
        environment.runOnMainThread(new Callable<Void>() {
            @Override
            public Void call() {
                callableRan[0] = true;
                return null;
            }
        });
        environment.sleep(1L);
        assertEquals("", environment.encode(new byte[]{1}));
        environment.log("ignored");
        assertNull(environment.loadEmojiInfo("ignored"));
        environment.onEmojiDiagnosticNeeded("ignored");
        assertEquals(true, callableRan[0]);
    }

    @Test
    public void runtimeCreatesBridgeRuntime() {
        HookWechatMediaRuntimeProvider provider = new HookWechatMediaRuntimeProvider(
                new ImageDownloadRequestTracker(1024),
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        HookMediaAttachmentBridge.Runtime runtime = provider.runtime(new Object());

        assertNotNull(runtime);
    }

    private static final class FixedAppRootProvider implements HookWechatMediaRuntimeProvider.AppRootProvider {
        private final File appRoot;

        FixedAppRootProvider(File appRoot) {
            this.appRoot = appRoot;
        }

        @Override
        public File appRoot() {
            return appRoot;
        }
    }

    private static final class FixedClassLoaderProvider implements HookWechatMediaRuntimeProvider.ClassLoaderProvider {
        private final ClassLoader classLoader;

        FixedClassLoaderProvider(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public ClassLoader classLoader() {
            return classLoader;
        }
    }

    private static final class CapturingCallbacks implements
            HookWechatMediaRuntimeProvider.MainThreadRunner,
            HookWechatMediaRuntimeProvider.Sleeper,
            HookWechatMediaRuntimeProvider.Encoder,
            HookWechatMediaRuntimeProvider.Logger,
            HookWechatMediaRuntimeProvider.EmojiInfoLoader,
            HookWechatMediaRuntimeProvider.EmojiDiagnosticReporter {
        int mainThreadCalls;
        boolean callableRan;
        long sleepMillis;
        final List<String> logs = new ArrayList<>();
        final Object emojiInfo = new Object();
        ClassLoader emojiInfoClassLoader;
        String loadedEmojiMd5;
        String emojiMd5;

        @Override
        public void run(Callable<Void> callable) throws Exception {
            mainThreadCalls++;
            if (callable != null) {
                callable.call();
            }
        }

        @Override
        public void sleep(long millis) {
            sleepMillis = millis;
        }

        @Override
        public String encode(byte[] bytes) {
            return "encoded:" + (bytes == null ? 0 : bytes.length);
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public Object load(ClassLoader classLoader, String emojiMd5) {
            this.emojiInfoClassLoader = classLoader;
            this.loadedEmojiMd5 = emojiMd5;
            return emojiInfo;
        }

        @Override
        public void report(String emojiMd5) {
            this.emojiMd5 = emojiMd5;
        }
    }
}
