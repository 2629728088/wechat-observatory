package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class WechatImageDownloadQueueClassesTest {
    @Test
    public void resolveUsesWechatQueueClassNames() throws Exception {
        FakeReflector reflector = new FakeReflector();
        ClassLoader classLoader = getClass().getClassLoader();

        WechatImageDownloadQueueClasses classes =
                WechatImageDownloadQueueClasses.resolve(reflector, classLoader);

        assertSame(FakeQueueHolder.class, classes.queueClass);
        assertSame(FakeSceneEndCallback.class, classes.sceneEndCallbackClass);
        assertSame(FakeModelScene.class, classes.modelSceneClass);
        assertSame(classLoader, reflector.classLoader);
        assertEquals(3, reflector.names.size());
        assertEquals("gm0.j1", reflector.names.get(0));
        assertEquals("com.tencent.mm.modelbase.u0", reflector.names.get(1));
        assertEquals("com.tencent.mm.modelbase.m1", reflector.names.get(2));
    }

    private static final class FakeReflector implements WechatImageDownloadRuntime.Reflector {
        final List<String> names = new ArrayList<>();
        ClassLoader classLoader;

        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) {
            this.classLoader = classLoader;
            names.add(name);
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
        public Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) {
            throw new UnsupportedOperationException();
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

    private static final class FakeQueueHolder {
    }

    private interface FakeSceneEndCallback {
    }

    private static final class FakeModelScene {
    }
}
