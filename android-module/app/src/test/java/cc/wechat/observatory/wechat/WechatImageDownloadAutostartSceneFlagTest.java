package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class WechatImageDownloadAutostartSceneFlagTest {
    @Test
    public void enableSetsSceneCFlag() throws Exception {
        FakeScene scene = new FakeScene();
        FakeReflector reflector = new FakeReflector(false);

        boolean enabled = new WechatImageDownloadAutostartSceneFlag(reflector).enable(scene);

        assertTrue(enabled);
        assertEquals(1, scene.C);
        assertSame(scene, reflector.target);
        assertEquals(1, reflector.value);
        assertEquals("C", reflector.name);
    }

    @Test
    public void enablePropagatesReflectorFailure() {
        try {
            new WechatImageDownloadAutostartSceneFlag(new FakeReflector(true))
                    .enable(new FakeScene());
            fail("expected reflector failure");
        } catch (Exception e) {
            assertEquals("scene-fail", e.getMessage());
        }
    }

    private static final class FakeReflector implements WechatImageDownloadRuntime.Reflector {
        private final boolean fail;
        Object target;
        int value;
        String name;

        FakeReflector(boolean fail) {
            this.fail = fail;
        }

        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) {
            throw new UnsupportedOperationException();
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
        public Field findFieldAny(Class<?> cls, String... names) throws Exception {
            for (String candidate : names) {
                try {
                    Field field = cls.getDeclaredField(candidate);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new NoSuchFieldException();
        }

        @Override
        public void setIntFieldAny(Object target, int value, String... names) throws Exception {
            if (fail) {
                throw new IllegalStateException("scene-fail");
            }
            this.target = target;
            this.value = value;
            this.name = names.length == 0 ? "" : names[0];
            findFieldAny(target.getClass(), names).setInt(target, value);
        }
    }

    public static final class FakeScene {
        int C;
    }
}
