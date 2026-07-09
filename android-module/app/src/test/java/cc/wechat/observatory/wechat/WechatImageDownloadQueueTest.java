package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class WechatImageDownloadQueueTest {
    @Test
    public void dispatchRegistersCallbackAndQueuesScene() throws Exception {
        FakeQueue.reset();
        FakeModelScene scene = new FakeModelScene();
        FakeSceneEndCallback callback = new FakeSceneEndCallback() {
            @Override
            public void onSceneEnd(int errType, int errCode) {
            }
        };

        Object result = new WechatImageDownloadQueue(new FakeReflector())
                .dispatch(getClass().getClassLoader(), scene, callback);

        assertEquals("queued", result);
        assertEquals(1, FakeQueue.queue.registeredCallbacks.size());
        assertSame(callback, FakeQueue.queue.registeredCallbacks.get(0));
        assertEquals(1, FakeQueue.queue.scenes.size());
        assertSame(scene, FakeQueue.queue.scenes.get(0));
        assertEquals(109, FakeQueue.queue.registeredTypes.get(0).intValue());
    }

    @Test
    public void unregisterRemovesCallbackWithImageSceneType() throws Exception {
        FakeQueue.reset();
        FakeSceneEndCallback callback = new FakeSceneEndCallback() {
            @Override
            public void onSceneEnd(int errType, int errCode) {
            }
        };

        new WechatImageDownloadQueue(new FakeReflector())
                .unregister(getClass().getClassLoader(), callback);

        assertEquals(1, FakeQueue.queue.unregisteredCallbacks.size());
        assertSame(callback, FakeQueue.queue.unregisteredCallbacks.get(0));
        assertEquals(109, FakeQueue.queue.unregisteredTypes.get(0).intValue());
    }

    private static final class FakeReflector implements WechatImageDownloadRuntime.Reflector {
        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) {
            if ("gm0.j1".equals(name)) {
                return FakeQueueHolder.class;
            }
            if ("com.tencent.mm.modelbase.u0".equals(name)) {
                return FakeSceneEndCallback.class;
            }
            if ("com.tencent.mm.modelbase.m1".equals(name)) {
                return FakeModelScene.class;
            }
            throw new IllegalArgumentException(name);
        }

        @Override
        public Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws Exception {
            Method method = cls.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }

        @Override
        public Constructor<?> findConstructor(Class<?> cls, Class<?>... parameterTypes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Field findFieldAny(Class<?> cls, String... names) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setIntFieldAny(Object target, int value, String... names) {
            throw new UnsupportedOperationException();
        }
    }

    public interface FakeSceneEndCallback {
        void onSceneEnd(int errType, int errCode);
    }

    public static final class FakeModelScene {
    }

    public static final class FakeQueueHolder {
        static FakeQueue d() {
            return FakeQueue.queue;
        }
    }

    public static final class FakeQueue {
        static FakeQueue queue = new FakeQueue();
        final List<FakeModelScene> scenes = new ArrayList<>();
        final List<FakeSceneEndCallback> registeredCallbacks = new ArrayList<>();
        final List<Integer> registeredTypes = new ArrayList<>();
        final List<FakeSceneEndCallback> unregisteredCallbacks = new ArrayList<>();
        final List<Integer> unregisteredTypes = new ArrayList<>();

        static void reset() {
            queue = new FakeQueue();
        }

        void a(int type, FakeSceneEndCallback callback) {
            registeredTypes.add(Integer.valueOf(type));
            registeredCallbacks.add(callback);
        }

        String g(FakeModelScene scene) {
            scenes.add(scene);
            return "queued";
        }

        void q(int type, FakeSceneEndCallback callback) {
            unregisteredTypes.add(Integer.valueOf(type));
            unregisteredCallbacks.add(callback);
        }
    }
}
