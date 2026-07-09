package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class WechatImageDownloadRuntimeClassesTest {
    @Test
    public void resolveUsesWechatImageDownloadClassNames() throws Exception {
        FakeReflector reflector = new FakeReflector();
        ClassLoader classLoader = getClass().getClassLoader();

        WechatImageDownloadRuntimeClasses classes =
                WechatImageDownloadRuntimeClasses.resolve(reflector, classLoader);

        assertSame(FakeScene.class, classes.sceneClass);
        assertSame(FakeProgressCallback.class, classes.progressCallbackClass);
        assertSame(FakeSceneEndCallback.class, classes.sceneEndCallbackClass);
        assertSame(classLoader, reflector.classLoader);
        assertEquals(3, reflector.names.size());
        assertEquals("m11.t0", reflector.names.get(0));
        assertEquals("com.tencent.mm.modelbase.v0", reflector.names.get(1));
        assertEquals("com.tencent.mm.modelbase.u0", reflector.names.get(2));
    }

    private static final class FakeReflector implements WechatImageDownloadRuntime.Reflector {
        final List<String> names = new ArrayList<>();
        ClassLoader classLoader;

        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) {
            this.classLoader = classLoader;
            names.add(name);
            if ("m11.t0".equals(name)) {
                return FakeScene.class;
            }
            if ("com.tencent.mm.modelbase.v0".equals(name)) {
                return FakeProgressCallback.class;
            }
            if ("com.tencent.mm.modelbase.u0".equals(name)) {
                return FakeSceneEndCallback.class;
            }
            throw new IllegalArgumentException(name);
        }

        @Override
        public Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public Constructor<?> findConstructor(Class<?> cls, Class<?>... parameterTypes) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public Field findFieldAny(Class<?> cls, String... names) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setIntFieldAny(Object target, int value, String... names) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeScene {
    }

    private interface FakeProgressCallback {
    }

    private interface FakeSceneEndCallback {
    }
}
