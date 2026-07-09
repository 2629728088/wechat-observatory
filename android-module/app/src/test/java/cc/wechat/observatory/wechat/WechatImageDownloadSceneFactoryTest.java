package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class WechatImageDownloadSceneFactoryTest {
    @Test
    public void createsSixArgumentScene() throws Exception {
        Object callback = new FakeProgressCallback() {
        };

        FakeModelScene scene = (FakeModelScene) new WechatImageDownloadSceneFactory(new ConstructorOnlyReflector())
                .create(FakeImageScene.class, 30L, 10L, "room", 1, FakeProgressCallback.class, callback);

        assertEquals(30L, scene.imageInfoId);
        assertEquals(10L, scene.localMsgId);
        assertEquals("room", scene.talker);
        assertEquals(1, scene.downloadType);
        assertSame(callback, scene.callback);
        assertEquals(0, scene.mode);
    }

    @Test
    public void fallsBackToLegacyFiveArgumentSceneAndNormalizesNullTalker() throws Exception {
        Object callback = new FakeProgressCallback() {
        };

        FakeModelScene scene = (FakeModelScene) new WechatImageDownloadSceneFactory(new ConstructorOnlyReflector())
                .create(FakeLegacyImageScene.class, 31L, 11L, null, 0, FakeProgressCallback.class, callback);

        assertEquals(31L, scene.imageInfoId);
        assertEquals(11L, scene.localMsgId);
        assertEquals("", scene.talker);
        assertEquals(0, scene.downloadType);
        assertSame(callback, scene.callback);
    }

    private static final class ConstructorOnlyReflector implements WechatImageDownloadRuntime.Reflector {
        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Constructor<?> findConstructor(Class<?> cls, Class<?>... parameterTypes) throws Exception {
            Constructor<?> ctor = cls.getDeclaredConstructor(parameterTypes);
            ctor.setAccessible(true);
            return ctor;
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

    public interface FakeProgressCallback {
    }

    public static class FakeModelScene {
        long imageInfoId;
        long localMsgId;
        String talker;
        int downloadType;
        Object callback;
        int mode = -1;
    }

    public static final class FakeImageScene extends FakeModelScene {
        FakeImageScene(long imageInfoId, long localMsgId, String talker, int downloadType, FakeProgressCallback callback, int mode) {
            this.imageInfoId = imageInfoId;
            this.localMsgId = localMsgId;
            this.talker = talker;
            this.downloadType = downloadType;
            this.callback = callback;
            this.mode = mode;
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
}
