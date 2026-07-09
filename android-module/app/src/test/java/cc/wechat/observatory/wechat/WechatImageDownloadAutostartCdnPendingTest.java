package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadAutostartCdnPendingTest {
    @Test
    public void enableAddsPendingImageKey() throws Exception {
        FakeCdnHolder.core = new FakeCdnCore(new HashSet<String>());

        boolean enabled = new WechatImageDownloadAutostartCdnPending(new FakeReflector())
                .enable(getClass().getClassLoader(), 10L);

        assertTrue(enabled);
        assertTrue(((Set) FakeCdnHolder.core.u).contains("image_10"));
    }

    @Test
    public void enableReturnsFalseWhenMsgIdIsMissing() throws Exception {
        FakeCdnHolder.core = new FakeCdnCore(new HashSet<String>());

        boolean enabled = new WechatImageDownloadAutostartCdnPending(new FakeReflector())
                .enable(getClass().getClassLoader(), 0L);

        assertFalse(enabled);
        assertTrue(((Set) FakeCdnHolder.core.u).isEmpty());
    }

    @Test
    public void enableReturnsFalseWhenPendingFieldIsNotSet() throws Exception {
        FakeCdnHolder.core = new FakeCdnCore("not-a-set");

        boolean enabled = new WechatImageDownloadAutostartCdnPending(new FakeReflector())
                .enable(getClass().getClassLoader(), 10L);

        assertFalse(enabled);
    }

    private static final class FakeReflector implements WechatImageDownloadRuntime.Reflector {
        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) {
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
        public Constructor<?> findConstructor(Class<?> cls, Class<?>... parameterTypes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Field findFieldAny(Class<?> cls, String... names) throws Exception {
            for (String name : names) {
                try {
                    Field field = cls.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new NoSuchFieldException();
        }

        @Override
        public void setIntFieldAny(Object target, int value, String... names) {
            throw new UnsupportedOperationException();
        }
    }

    public static final class FakeCdnHolder {
        static FakeCdnCore core = new FakeCdnCore(new HashSet<String>());

        static FakeCdnCore fj() {
            return core;
        }
    }

    public static final class FakeCdnCore {
        Object u;

        FakeCdnCore(Object pending) {
            this.u = pending;
        }
    }
}
