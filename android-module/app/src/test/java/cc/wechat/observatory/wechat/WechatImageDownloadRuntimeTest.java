package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadRuntimeTest {
    @Test
    public void requestEnqueuesMidAndHdScenesAndRegistersCallbacks() throws Exception {
        FakeQueue.reset();
        FakeCdnHolder.core = new FakeCdnCore();
        List<String> logs = new ArrayList<>();
        WechatImageDownloadRuntime runtime = new WechatImageDownloadRuntime(
                new WechatImageDownloadRuntime.ImageInfoIdResolver() {
                    @Override
                    public long resolve(ClassLoader classLoader, long msgId, long msgSvrId, String talker) {
                        return 333L;
                    }
                },
                logs::add,
                new FakeReflector(FakeImageScene.class));

        runtime.request(getClass().getClassLoader(), 10L, 20L, "room");

        assertEquals(2, FakeQueue.queue.scenes.size());
        assertEquals(0, FakeQueue.queue.scenes.get(0).downloadType);
        assertEquals(1, FakeQueue.queue.scenes.get(1).downloadType);
        assertEquals(333L, FakeQueue.queue.scenes.get(0).imageInfoId);
        assertEquals(10L, FakeQueue.queue.scenes.get(0).localMsgId);
        assertEquals("room", FakeQueue.queue.scenes.get(0).talker);
        assertEquals(1, FakeQueue.queue.scenes.get(0).C);
        assertEquals(2, FakeQueue.queue.registeredCallbacks.size());
        assertTrue(FakeCdnHolder.core.u.contains("image_10"));
        assertTrue(contains(logs, "type=0"));
        assertTrue(contains(logs, "type=1"));

        ((FakeSceneEndCallback) FakeQueue.queue.registeredCallbacks.get(0)).onSceneEnd(0, 0);

        assertEquals(1, FakeQueue.queue.unregisteredCallbacks.size());
        assertTrue(contains(logs, "onSceneEnd errType=0 errCode=0"));
    }

    @Test
    public void requestFallsBackToLegacyFiveArgumentSceneConstructor() throws Exception {
        FakeQueue.reset();
        FakeCdnHolder.core = new FakeCdnCore();
        WechatImageDownloadRuntime runtime = new WechatImageDownloadRuntime(
                new WechatImageDownloadRuntime.ImageInfoIdResolver() {
                    @Override
                    public long resolve(ClassLoader classLoader, long msgId, long msgSvrId, String talker) {
                        return msgId;
                    }
                },
                null,
                new FakeReflector(FakeLegacyImageScene.class));

        runtime.request(getClass().getClassLoader(), 11L, 0L, null);

        assertEquals(2, FakeQueue.queue.scenes.size());
        assertEquals("", FakeQueue.queue.scenes.get(0).talker);
        assertEquals(11L, FakeQueue.queue.scenes.get(0).imageInfoId);
    }

    private static boolean contains(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static final class FakeReflector implements WechatImageDownloadRuntime.Reflector {
        private final Class<?> sceneClass;

        FakeReflector(Class<?> sceneClass) {
            this.sceneClass = sceneClass;
        }

        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) {
            if ("m11.t0".equals(name)) {
                return sceneClass;
            }
            if ("com.tencent.mm.modelbase.v0".equals(name)) {
                return FakeProgressCallback.class;
            }
            if ("com.tencent.mm.modelbase.u0".equals(name)) {
                return FakeSceneEndCallback.class;
            }
            if ("com.tencent.mm.modelbase.m1".equals(name)) {
                return FakeModelScene.class;
            }
            if ("gm0.j1".equals(name)) {
                return FakeQueue.class;
            }
            if ("com.tencent.mm.modelcdntran.s1".equals(name)) {
                return FakeCdnHolder.class;
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
        public Constructor<?> findConstructor(Class<?> cls, Class<?>... parameterTypes) throws Exception {
            Constructor<?> ctor = cls.getDeclaredConstructor(parameterTypes);
            ctor.setAccessible(true);
            return ctor;
        }

        @Override
        public Field findFieldAny(Class<?> cls, String... names) throws Exception {
            for (String name : names) {
                try {
                    return findField(cls, name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new NoSuchFieldException();
        }

        @Override
        public void setIntFieldAny(Object target, int value, String... names) throws Exception {
            Field field = findFieldAny(target.getClass(), names);
            field.setInt(target, value);
        }

        private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
            Class<?> current = cls;
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }
    }

    public interface FakeProgressCallback {
    }

    public interface FakeSceneEndCallback {
        void onSceneEnd(int errType, int errCode);
    }

    public static class FakeModelScene {
        long imageInfoId;
        long localMsgId;
        String talker;
        int downloadType;
        Object callback;
        int C;
    }

    public static class FakeImageScene extends FakeModelScene {
        FakeImageScene(long imageInfoId, long localMsgId, String talker, int downloadType, FakeProgressCallback callback, int mode) {
            this.imageInfoId = imageInfoId;
            this.localMsgId = localMsgId;
            this.talker = talker;
            this.downloadType = downloadType;
            this.callback = callback;
        }
    }

    public static final class FakeLegacyImageScene extends FakeModelScene {
        FakeLegacyImageScene(long imageInfoId, long localMsgId, String talker, int downloadType, FakeProgressCallback callback) {
            this.imageInfoId = imageInfoId;
            this.localMsgId = localMsgId;
            this.talker = talker;
            this.downloadType = downloadType;
            this.callback = callback;
        }
    }

    public static final class FakeQueue {
        static FakeQueue queue = new FakeQueue();
        final List<FakeModelScene> scenes = new ArrayList<>();
        final List<FakeSceneEndCallback> registeredCallbacks = new ArrayList<>();
        final List<FakeSceneEndCallback> unregisteredCallbacks = new ArrayList<>();

        static void reset() {
            queue = new FakeQueue();
        }

        static FakeQueue d() {
            return queue;
        }

        void a(int type, FakeSceneEndCallback callback) {
            assertEquals(109, type);
            registeredCallbacks.add(callback);
        }

        String g(FakeModelScene scene) {
            scenes.add(scene);
            return "queued";
        }

        void q(int type, FakeSceneEndCallback callback) {
            assertEquals(109, type);
            unregisteredCallbacks.add(callback);
        }
    }

    public static final class FakeCdnHolder {
        static FakeCdnCore core = new FakeCdnCore();

        static FakeCdnCore fj() {
            return core;
        }
    }

    public static final class FakeCdnCore {
        Set<String> u = new HashSet<>();
    }
}
