package cc.wechat.observatory.wechat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadCallbackFactoryTest {
    @Test
    public void callbackImplementsWechatCallbackContractsAndObjectMethods() {
        final Object[] capturedCallback = new Object[1];
        final Object[][] capturedArgs = new Object[1][];
        Object callback = new WechatImageDownloadCallbackFactory()
                .create(
                        getClass().getClassLoader(),
                        FakeProgressCallback.class,
                        FakeSceneEndCallback.class,
                        new WechatImageDownloadCallbackFactory.Handler() {
                            @Override
                            public void onSceneEnd(Object callback, Object[] args) {
                                capturedCallback[0] = callback;
                                capturedArgs[0] = args;
                            }
                        });

        assertTrue(callback instanceof FakeProgressCallback);
        assertTrue(callback instanceof FakeSceneEndCallback);
        assertEquals("WechatObservatoryImageDownloadCallback", callback.toString());
        assertEquals(System.identityHashCode(callback), callback.hashCode());
        assertTrue(callback.equals(callback));
        assertFalse(callback.equals(new Object()));
        assertNull(((FakeProgressCallback) callback).onProgress(50));

        ((FakeSceneEndCallback) callback).onSceneEnd(0, 0);

        assertSame(callback, capturedCallback[0]);
        assertEquals(2, capturedArgs[0].length);
        assertEquals(Integer.valueOf(0), capturedArgs[0][0]);
        assertEquals(Integer.valueOf(0), capturedArgs[0][1]);
    }

    @Test
    public void callbackAllowsMissingHandler() {
        Object callback = new WechatImageDownloadCallbackFactory()
                .create(
                        getClass().getClassLoader(),
                        FakeProgressCallback.class,
                        FakeSceneEndCallback.class,
                        null);

        ((FakeSceneEndCallback) callback).onSceneEnd(4, -1);
    }

    public interface FakeProgressCallback {
        Object onProgress(int percent);
    }

    public interface FakeSceneEndCallback {
        void onSceneEnd(int errType, int errCode);
    }
}
