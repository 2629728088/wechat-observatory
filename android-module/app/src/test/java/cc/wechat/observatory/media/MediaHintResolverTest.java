package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class MediaHintResolverTest {
    @Test
    public void resolveImageUsesInfoTableAndPrefersLocalIdBeforeServerId() {
        FakeStore store = new FakeStore()
                .columns("ImgInfo2", "MsgLocalId", "MsgSvrId", "thumbImgPath", "bigImgPath")
                .value("ImgInfo2", "MsgSvrId", 20L, "bigImgPath", "server.jpg")
                .value("ImgInfo2", "MsgLocalId", 10L, "bigImgPath", "local.jpg");

        String hint = MediaHintResolver.resolve(store, 3, 10L, 20L, "fallback.jpg");

        assertEquals("local.jpg", hint);
        assertEquals("ImgInfo2:MsgLocalId:10:[bigImgPath, thumbImgPath]", store.queries.get(0));
    }

    @Test
    public void resolveVoiceFallsBackToSecondVoiceTable() {
        FakeStore store = new FakeStore()
                .columns("voiceinfo", "MsgLocalId", "MsgId")
                .columns("voiceinfo2", "MsgLocalId", "FileName")
                .value("voiceinfo2", "MsgLocalId", 10L, "FileName", "voice.amr");

        String hint = MediaHintResolver.resolve(store, 34, 10L, 0L, "");

        assertEquals("voice.amr", hint);
    }

    @Test
    public void resolveVideoChecksServerIdWhenLocalIdMisses() {
        FakeStore store = new FakeStore()
                .columns("videoinfo2", "msgLocalId", "msgSvrId", "filename")
                .value("videoinfo2", "msgSvrId", 20L, "filename", "video.mp4");

        String hint = MediaHintResolver.resolve(store, 43, 10L, 20L, "fallback.mp4");

        assertEquals("video.mp4", hint);
    }

    @Test
    public void resolveReturnsFallbackForUnsupportedTypeOrMiss() {
        FakeStore store = new FakeStore()
                .columns("videoinfo2", "msgLocalId", "filename");

        assertEquals("text", MediaHintResolver.resolve(store, 1, 10L, 20L, "text"));
        assertEquals("fallback", MediaHintResolver.resolve(store, 62, 10L, 20L, "fallback"));
    }

    @Test
    public void resolveImageInfoIdUsesImgInfoLocalRowId() {
        FakeStore store = new FakeStore()
                .columns("ImgInfo2", "id", "MsgLocalId", "MsgSvrId")
                .value("ImgInfo2", "MsgLocalId", 10L, "id", "500");

        long id = MediaHintResolver.resolveImageInfoId(store, 10L, 20L);

        assertEquals(500L, id);
        assertEquals("ImgInfo2:MsgLocalId:10:[id]", store.queries.get(0));
    }

    @Test
    public void resolveImageInfoIdFallsBackToServerIdAndIgnoresNonNumericValue() {
        FakeStore store = new FakeStore()
                .columns("ImgInfo2", "id", "MsgLocalId", "MsgSvrId")
                .value("ImgInfo2", "MsgLocalId", 10L, "id", "not-a-number")
                .value("ImgInfo2", "MsgSvrId", 20L, "id", "600");

        long id = MediaHintResolver.resolveImageInfoId(store, 10L, 20L);

        assertEquals(600L, id);
    }

    private static final class FakeStore implements MediaHintResolver.Store {
        private final Map<String, List<String>> columns = new HashMap<>();
        private final Map<String, String> values = new HashMap<>();
        final List<String> queries = new ArrayList<>();

        FakeStore columns(String table, String... columns) {
            this.columns.put(table, Arrays.asList(columns));
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
            queries.add(table + ":" + idColumn + ":" + id + ":" + valueColumns);
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
