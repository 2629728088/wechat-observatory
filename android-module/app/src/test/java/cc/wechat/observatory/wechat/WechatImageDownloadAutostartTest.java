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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadAutostartTest {
    @Test
    public void enableSetsSceneFlagAndCdnPendingKey() {
        FakeCdnHolder.core = new FakeCdnCore();
        FakeScene scene = new FakeScene();
        List<String> logs = new ArrayList<>();

        new WechatImageDownloadAutostart(new FakeReflector(false, false), logs::add)
                .enable(getClass().getClassLoader(), scene, 10L);

        assertEquals(1, scene.C);
        assertTrue(FakeCdnHolder.core.u.contains("image_10"));
        assertTrue(contains(logs, "autostart enabled msgId=10"));
        assertFalse(contains(logs, "autostart scene flag failed"));
        assertFalse(contains(logs, "autostart queue flag failed"));
    }

    @Test
    public void enableLogsSceneFailureButStillUsesCdnPendingKey() {
        FakeCdnHolder.core = new FakeCdnCore();
        List<String> logs = new ArrayList<>();

        new WechatImageDownloadAutostart(new FakeReflector(true, false), logs::add)
                .enable(getClass().getClassLoader(), new FakeScene(), 11L);

        assertTrue(FakeCdnHolder.core.u.contains("image_11"));
        assertTrue(contains(logs, "autostart scene flag failed msgId=11"));
        assertTrue(contains(logs, "scene-fail"));
        assertTrue(contains(logs, "autostart enabled msgId=11"));
    }

    @Test
    public void enableLogsCdnFailureButStillUsesSceneFlag() {
        List<String> logs = new ArrayList<>();
        FakeScene scene = new FakeScene();

        new WechatImageDownloadAutostart(new FakeReflector(false, true), logs::add)
                .enable(getClass().getClassLoader(), scene, 12L);

        assertEquals(1, scene.C);
        assertTrue(contains(logs, "autostart queue flag failed msgId=12"));
        assertTrue(contains(logs, "cdn-fail"));
        assertTrue(contains(logs, "autostart enabled msgId=12"));
    }

    @Test
    public void enableDoesNotAddPendingKeyWhenMsgIdIsMissing() {
        FakeCdnHolder.core = new FakeCdnCore();
        FakeScene scene = new FakeScene();

        new WechatImageDownloadAutostart(new FakeReflector(false, false), null)
                .enable(getClass().getClassLoader(), scene, 0L);

        assertEquals(1, scene.C);
        assertTrue(FakeCdnHolder.core.u.isEmpty());
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
        private final boolean failSceneFlag;
        private final boolean failCdn;

        FakeReflector(boolean failSceneFlag, boolean failCdn) {
            this.failSceneFlag = failSceneFlag;
            this.failCdn = failCdn;
        }

        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) {
            if (failCdn) {
                throw new IllegalStateException("cdn-fail");
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
        public void setIntFieldAny(Object target, int value, String... names) throws Exception {
            if (failSceneFlag) {
                throw new IllegalStateException("scene-fail");
            }
            findFieldAny(target.getClass(), names).setInt(target, value);
        }
    }

    public static final class FakeScene {
        int C;
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
