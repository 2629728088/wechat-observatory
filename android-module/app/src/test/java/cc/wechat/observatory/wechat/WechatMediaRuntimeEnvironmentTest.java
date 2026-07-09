package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatMediaRuntimeEnvironmentTest {
    @Test
    public void delegatesAllRuntimeCollaborators() throws Exception {
        Object database = new Object();
        File appRoot = Files.createTempDirectory("wxo-runtime-env").toFile();
        ClassLoader classLoader = getClass().getClassLoader();
        AtomicBoolean mainThreadCalled = new AtomicBoolean();
        AtomicLong slept = new AtomicLong();
        AtomicReference<String> logged = new AtomicReference<>();
        AtomicReference<String> diagnostic = new AtomicReference<>();

        WechatMediaRuntimeEnvironment environment = new WechatMediaRuntimeEnvironment(
                database,
                appRoot,
                classLoader,
                new WechatMediaRuntimeEnvironment.MainThreadRunner() {
                    @Override
                    public void run(Callable<Void> callable) throws Exception {
                        mainThreadCalled.set(true);
                        callable.call();
                    }
                },
                new WechatMediaRuntimeEnvironment.Sleeper() {
                    @Override
                    public void sleep(long millis) {
                        slept.set(millis);
                    }
                },
                new WechatMediaRuntimeEnvironment.Encoder() {
                    @Override
                    public String encode(byte[] bytes) {
                        return "encoded-" + bytes.length;
                    }
                },
                new WechatMediaRuntimeEnvironment.Logger() {
                    @Override
                    public void log(String message) {
                        logged.set(message);
                    }
                },
                new WechatMediaRuntimeEnvironment.EmojiDiagnosticReporter() {
                    @Override
                    public void report(String emojiMd5) {
                        diagnostic.set(emojiMd5);
                    }
                });

        assertSame(database, environment.database());
        assertEquals(appRoot.getCanonicalFile(), environment.appRoot().getCanonicalFile());
        assertSame(classLoader, environment.classLoader());
        environment.runOnMainThread(new Callable<Void>() {
            @Override
            public Void call() {
                return null;
            }
        });
        environment.sleep(123L);
        assertEquals("encoded-3", environment.encode(new byte[]{1, 2, 3}));
        environment.log("message");
        environment.onEmojiDiagnosticNeeded("emoji-md5");

        assertTrue(mainThreadCalled.get());
        assertEquals(123L, slept.get());
        assertEquals("message", logged.get());
        assertEquals("emoji-md5", diagnostic.get());
    }

    @Test
    public void missingCollaboratorsAreSafe() throws Exception {
        WechatMediaRuntimeEnvironment environment = new WechatMediaRuntimeEnvironment(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        AtomicBoolean callableCalled = new AtomicBoolean();

        environment.runOnMainThread(new Callable<Void>() {
            @Override
            public Void call() {
                callableCalled.set(true);
                return null;
            }
        });
        environment.sleep(1L);
        environment.log("ignored");
        environment.onEmojiDiagnosticNeeded("ignored");

        assertEquals("", environment.encode(new byte[]{1}));
        assertTrue(callableCalled.get());
    }
}
