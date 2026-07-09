package cc.wechat.observatory;

import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class HookWechatVoiceFilePreparerTest {
    @Test
    public void prepareCopiesSourceToScopedAndLegacyVoicePaths() throws Exception {
        File root = Files.createTempDirectory("wxo-voice-preparer").toFile();
        byte[] voiceBytes = new byte[]{0x02, 0x23, 0x21, 0x53, 0x49, 0x4c, 0x4b};
        File source = new File(root, "source.silk");
        Files.write(source.toPath(), voiceBytes);
        FakeStorage.root = root;
        List<String> logs = new ArrayList<>();

        String preparedPath = HookWechatVoiceFilePreparer.prepare(
                getClass().getClassLoader(),
                "voice-test.silk",
                source,
                new FakeReflection(),
                logs::add);

        File audioFile = new File(new File(root, "audio"), "voice-test.silk");
        File legacyFile = new File(new File(root, "legacy"), "voice-test.silk");
        assertEquals(audioFile.getAbsolutePath(), preparedPath);
        assertArrayEquals(voiceBytes, Files.readAllBytes(audioFile.toPath()));
        assertArrayEquals(voiceBytes, Files.readAllBytes(legacyFile.toPath()));
        assertTrue(logs.isEmpty());
    }

    private static final class FakeReflection implements HookWechatVoiceFilePreparer.Reflection {
        @Override
        public Class<?> findClass(ClassLoader classLoader, String name) throws ClassNotFoundException {
            if ("tg3.u0".equals(name)) {
                return FakeStorageInterface.class;
            }
            if ("i95.n0".equals(name)) {
                return FakeServiceLocator.class;
            }
            if ("bm5.y".equals(name)) {
                return FakeY.class;
            }
            if ("oi3.g".equals(name)) {
                return FakeOi3.class;
            }
            if ("bm5.f0".equals(name)) {
                return FakeF0.class;
            }
            if ("com.tencent.mm.vfs.w6".equals(name)) {
                return FakeVfs.class;
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
            return cls.getDeclaredMethod(name, parameterTypes);
        }

        @Override
        public Field findField(Class<?> cls, String name) throws NoSuchFieldException {
            return cls.getDeclaredField(name);
        }
    }

    private interface FakeStorageInterface {
    }

    public static final class FakeServiceLocator {
        public static Object c(Class<?> ignored) {
            return FakeStorage.INSTANCE;
        }
    }

    public static final class FakeStorage {
        static final FakeStorage INSTANCE = new FakeStorage();
        static File root;

        public String vj(FakeY resource, String voiceFileName, boolean create) {
            return new File(new File(root, resource.directory), voiceFileName).getAbsolutePath();
        }
    }

    public static final class FakeY {
        public static final FakeBuilder i = new FakeBuilder();
        public static final FakeY j = new FakeY("legacy");

        final String directory;

        FakeY(String directory) {
            this.directory = directory;
        }
    }

    public static final class FakeBuilder {
        public FakeY d(FakeOi3 ignored, FakeF0 audioType) {
            return new FakeY("audio");
        }
    }

    public static final class FakeOi3 {
    }

    public static final class FakeF0 {
        public static final FakeF0 u = new FakeF0();
    }

    public static final class FakeVfs {
        public static RandomAccessFile B(String path, boolean write) throws Exception {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null) {
                assertTrue(parent.mkdirs() || parent.isDirectory());
            }
            return new RandomAccessFile(file, write ? "rw" : "r");
        }
    }
}
