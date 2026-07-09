package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatMediaHintStoreTest {
    @Test
    public void tableColumnsReadsPragmaColumnsAndDeduplicates() {
        FakeCursor cursor = new FakeCursor(new String[][]{
                {"0", "bigImgPath"},
                {"1", "bigImgPath"},
                {"2", "thumbImgPath"},
                {"3", " "}
        });
        FakeDb db = new FakeDb(cursor);

        List<String> columns = new WechatMediaHintStore(db).tableColumns("ImgInfo2");

        assertEquals(Arrays.asList("bigImgPath", "thumbImgPath"), columns);
        assertEquals("PRAGMA table_info(`ImgInfo2`)", db.lastSql);
        assertTrue(cursor.closed);
    }

    @Test
    public void queryFirstValueReturnsFirstNonBlankColumnAndEscapesIdentifiers() {
        FakeCursor cursor = new FakeCursor(new String[][]{{"", "big.jpg"}});
        FakeDb db = new FakeDb(cursor);

        String value = new WechatMediaHintStore(db)
                .queryFirstValue("Img`Info", "Msg`Id", 10L, Arrays.asList("thumb", "big"));

        assertEquals("big.jpg", value);
        assertEquals("SELECT `thumb`,`big` FROM `Img``Info` WHERE `Msg``Id`=? LIMIT 1", db.lastSql);
        assertArrayEquals(new Object[]{"10"}, db.lastArgs);
        assertTrue(cursor.closed);
    }

    public static final class FakeDb {
        final FakeCursor cursor;
        String lastSql;
        Object[] lastArgs;

        FakeDb(FakeCursor cursor) {
            this.cursor = cursor;
        }

        public Object rawQuery(String sql, Object[] args) {
            this.lastSql = sql;
            this.lastArgs = args;
            return cursor;
        }
    }

    public static final class FakeCursor {
        final String[][] rows;
        int index = -1;
        boolean closed;

        FakeCursor(String[][] rows) {
            this.rows = rows;
        }

        public boolean moveToFirst() {
            if (rows.length == 0) {
                return false;
            }
            index = 0;
            return true;
        }

        public boolean moveToNext() {
            if (index + 1 >= rows.length) {
                return false;
            }
            index++;
            return true;
        }

        public String getString(int column) {
            if (index < 0 || index >= rows.length || column < 0 || column >= rows[index].length) {
                return "";
            }
            return rows[index][column];
        }

        public void close() {
            closed = true;
        }
    }
}
