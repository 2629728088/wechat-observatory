package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MediaHintRuntimeTest {
    @Test
    public void resolveUsesStoreFactoryAndFallbackWhenMissing() {
        FakeStore store = new FakeStore()
                .columns("ImgInfo2", "MsgLocalId", "bigImgPath")
                .value("ImgInfo2", "MsgLocalId", 10L, "bigImgPath", "image.jpg");

        MediaHintRuntime runtime = new MediaHintRuntime(
                new TestEnvironment(new Object()),
                database -> store);

        assertEquals("image.jpg", runtime.resolve(3, 10L, 20L, "fallback.jpg"));
        assertEquals("fallback", runtime.resolve(3, 99L, 0L, "fallback"));
    }

    @Test
    public void resolveImageInfoIdReturnsPositiveValue() {
        FakeStore store = new FakeStore()
                .columns("ImgInfo2", "id", "MsgLocalId")
                .value("ImgInfo2", "MsgLocalId", 10L, "id", "500");

        long id = new MediaHintRuntime(new TestEnvironment(new Object()), database -> store)
                .resolveImageInfoId(10L, 20L);

        assertEquals(500L, id);
    }

    @Test
    public void resolveImageInfoIdLogsAndReturnsZeroWhenStoreFails() {
        TestEnvironment environment = new TestEnvironment(new Object());
        MediaHintRuntime runtime = new MediaHintRuntime(
                environment,
                database -> new FailingStore());

        long id = runtime.resolveImageInfoId(10L, 20L);

        assertEquals(0L, id);
        assertTrue(contains(environment.logs, "image info id resolve failed msgId=10"));
        assertTrue(contains(environment.logs, "boom"));
    }

    @Test
    public void resolveUsesFallbackWithoutEnvironment() {
        assertEquals("fallback", new MediaHintRuntime(null).resolve(3, 10L, 20L, "fallback"));
        assertEquals(0L, new MediaHintRuntime(null).resolveImageInfoId(10L, 20L));
    }

    private static boolean contains(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static final class TestEnvironment implements MediaHintRuntime.Environment {
        private final Object database;
        final List<String> logs = new ArrayList<>();

        TestEnvironment(Object database) {
            this.database = database;
        }

        @Override
        public Object database() {
            return database;
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }
    }

    private static final class FailingStore implements MediaHintResolver.Store {
        @Override
        public List<String> tableColumns(String table) {
            throw new IllegalStateException("boom");
        }

        @Override
        public String queryFirstValue(String table, String idColumn, long id, List<String> valueColumns) {
            return "";
        }
    }

    private static final class FakeStore implements MediaHintResolver.Store {
        private final Map<String, List<String>> columns = new HashMap<>();
        private final Map<String, String> values = new HashMap<>();

        FakeStore columns(String table, String... tableColumns) {
            columns.put(table, Arrays.asList(tableColumns));
            return this;
        }

        FakeStore value(String table, String idColumn, long id, String valueColumn, String value) {
            values.put(key(table, idColumn, id, valueColumn), value);
            return this;
        }

        @Override
        public List<String> tableColumns(String table) {
            List<String> result = columns.get(table);
            return result == null ? new ArrayList<String>() : result;
        }

        @Override
        public String queryFirstValue(String table, String idColumn, long id, List<String> valueColumns) {
            for (String valueColumn : valueColumns) {
                String value = values.get(key(table, idColumn, id, valueColumn));
                if (value != null && value.trim().length() > 0) {
                    return value;
                }
            }
            return "";
        }

        private static String key(String table, String idColumn, long id, String valueColumn) {
            return table + ":" + idColumn + ":" + id + ":" + valueColumn;
        }
    }
}
