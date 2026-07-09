package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import cc.wechat.observatory.media.MediaAttachmentEnvironment;
import cc.wechat.observatory.media.MediaResolverRuntime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatMediaAttachmentEnvironmentAdaptersTest {
    @Test
    public void mediaResolverEnvironmentReturnsNullWhenEnvironmentMissing() {
        assertNull(WechatMediaAttachmentEnvironmentAdapters.mediaResolverEnvironment(null));
    }

    @Test
    public void mediaResolverEnvironmentDelegatesMediaRuntimeMethods() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-resolver-adapter").toFile();
        List<String> logs = new ArrayList<>();
        TestEnvironment environment = new TestEnvironment(appRoot, logs);

        MediaResolverRuntime.Environment adapter =
                WechatMediaAttachmentEnvironmentAdapters.mediaResolverEnvironment(environment);

        assertEquals(appRoot.getCanonicalFile(), adapter.appRoot().getCanonicalFile());
        assertEquals("resolved", adapter.resolveMediaHint(
                3, Long.valueOf(10L), Long.valueOf(20L), "hint"));
        adapter.log("message");
        adapter.onEmojiDiagnosticNeeded("emoji");

        assertEquals(2, logs.size());
        assertEquals("message", logs.get(0));
        assertEquals("emoji:emoji", logs.get(1));
    }

    @Test
    public void emojiInfoProviderDelegatesToEnvironment() {
        File appRoot = new File("root");
        List<String> logs = new ArrayList<>();
        final Object emojiInfo = new Object();
        TestEnvironment environment = new TestEnvironment(appRoot, logs) {
            @Override
            public Object loadEmojiInfo(String emojiMd5) {
                assertEquals("emoji-md5", emojiMd5);
                return emojiInfo;
            }
        };

        MediaResolverRuntime.EmojiInfoProvider provider =
                WechatMediaAttachmentEnvironmentAdapters.emojiInfoProvider(environment);

        assertSame(emojiInfo, provider.load("emoji-md5"));
    }

    @Test
    public void emojiInfoProviderReturnsNullWhenEnvironmentMissing() {
        assertNull(WechatMediaAttachmentEnvironmentAdapters.emojiInfoProvider(null));
    }

    @Test
    public void imageDownloadServiceEnvironmentReturnsNullWhenEnvironmentMissing() {
        assertNull(WechatMediaAttachmentEnvironmentAdapters.imageDownloadServiceEnvironment(null));
    }

    @Test
    public void imageDownloadServiceEnvironmentDelegatesDownloadRuntimeMethods() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-image-download-adapter").toFile();
        List<String> logs = new ArrayList<>();
        AtomicBoolean callableCalled = new AtomicBoolean();
        AtomicBoolean slept = new AtomicBoolean();
        TestEnvironment environment = new TestEnvironment(appRoot, logs) {
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
        };

        WechatImageDownloadService.Environment adapter =
                WechatMediaAttachmentEnvironmentAdapters.imageDownloadServiceEnvironment(environment);

        assertSame(environment.classLoader(), adapter.classLoader());
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

    @Test
    public void imageDownloadRequesterReturnsNullWhenRequesterMissing() {
        assertNull(WechatMediaAttachmentEnvironmentAdapters.imageDownloadRequester(null));
    }

    @Test
    public void imageDownloadRequesterDelegatesRequest() {
        MediaAttachmentEnvironment.ImageDownloadRequester adapter =
                WechatMediaAttachmentEnvironmentAdapters.imageDownloadRequester(
                        new WechatMediaAttachmentServices.ImageDownloadRequester() {
                            @Override
                            public boolean request(long localId, long serverId, String talker) {
                                return localId == 10L && serverId == 20L && "talker".equals(talker);
                            }
                        });

        assertTrue(adapter.request(10L, 20L, "talker"));
        assertFalse(adapter.request(11L, 20L, "talker"));
    }

    @Test
    public void encoderAndLoggerReturnNullWhenEnvironmentMissing() {
        assertNull(WechatMediaAttachmentEnvironmentAdapters.encoder(null));
        assertNull(WechatMediaAttachmentEnvironmentAdapters.logger(null));
    }

    @Test
    public void encoderAndLoggerDelegateToEnvironment() {
        File appRoot = new File("root");
        List<String> logs = new ArrayList<>();
        final byte[] expected = new byte[]{1, 2, 3};
        TestEnvironment environment = new TestEnvironment(appRoot, logs) {
            @Override
            public String encode(byte[] bytes) {
                assertArrayEquals(expected, bytes);
                return "encoded";
            }
        };

        assertEquals("encoded", WechatMediaAttachmentEnvironmentAdapters.encoder(environment).encode(expected));
        WechatMediaAttachmentEnvironmentAdapters.logger(environment).log("message");

        assertEquals(1, logs.size());
        assertEquals("message", logs.get(0));
    }

    private static class TestEnvironment implements WechatMediaAttachmentServices.Environment {
        private final File appRoot;
        private final List<String> logs;

        TestEnvironment(File appRoot, List<String> logs) {
            this.appRoot = appRoot;
            this.logs = logs;
        }

        @Override
        public File appRoot() {
            return appRoot;
        }

        @Override
        public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
            assertEquals(3, type);
            assertEquals(Long.valueOf(10L), msgId);
            assertEquals(Long.valueOf(20L), msgSvrId);
            assertEquals("hint", mediaHint);
            return "resolved";
        }

        @Override
        public ClassLoader classLoader() {
            return getClass().getClassLoader();
        }

        @Override
        public long resolveImageInfoId(long localId, long serverId) {
            assertEquals(10L, localId);
            assertEquals(20L, serverId);
            return 30L;
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
            logs.add("emoji:" + emojiMd5);
        }
    }
}
