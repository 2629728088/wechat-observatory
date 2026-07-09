package cc.wechat.observatory.media;

import java.util.ArrayList;
import java.util.List;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class MediaHintResolver {
    public interface Store {
        List<String> tableColumns(String table);

        String queryFirstValue(String table, String idColumn, long id, List<String> valueColumns);
    }

    private MediaHintResolver() {
    }

    public static String resolve(Store store, int type, Long msgId, Long msgSvrId, String fallbackHint) {
        if (store == null) {
            return fallbackHint;
        }
        String hint = "";
        if (MediaFiles.isImageMessageType(type)) {
            hint = imageHint(store, msgId, msgSvrId);
        } else if (MediaFiles.isVoiceMessageType(type)) {
            hint = voiceHint(store, msgId, msgSvrId);
        } else if (MediaFiles.isVideoMessageType(type)) {
            hint = videoHint(store, msgId, msgSvrId);
        }
        return isBlank(hint) ? fallbackHint : hint;
    }

    public static long resolveImageInfoId(Store store, Long msgId, Long msgSvrId) {
        if (store == null) {
            return 0L;
        }
        return positiveLongFromTables(
                store,
                new String[]{"ImgInfo2", "imginfo2", "ImgInfo", "imginfo"},
                new String[]{
                        "id", "Id", "ID",
                        "imgId", "imgid", "ImgId",
                        "localImgId", "localimgid", "imageInfoId", "imageinfoid"
                },
                new String[]{
                        "msgLocalId", "MsgLocalId", "msglocalid",
                        "msgId", "MsgId", "msgid",
                        "msgSvrId", "MsgSvrId", "MsgSvrID", "msgsvrid", "svrId", "svrid"
                },
                msgId,
                msgSvrId);
    }

    static String imageHint(Store store, Long msgId, Long msgSvrId) {
        return hintFromTables(
                store,
                new String[]{"ImgInfo2", "imginfo2", "ImgInfo", "imginfo"},
                new String[]{
                        "bigImgPath", "bigimgpath", "BigImgPath", "big_img_path",
                        "midImgPath", "midimgpath", "MidImgPath", "middleImgPath", "middleimgpath",
                        "fullPath", "fullpath", "path", "localPath", "filePath", "filepath",
                        "filename", "fileName", "FileName",
                        "thumbImgPath", "thumbimgpath", "ThumbImgPath", "thumbnailPath"
                },
                new String[]{
                        "msgLocalId", "MsgLocalId", "msglocalid", "localId", "localid",
                        "msgId", "MsgId", "msgid",
                        "msgSvrId", "MsgSvrId", "MsgSvrID", "msgsvrid", "svrId", "svrid"
                },
                msgId,
                msgSvrId);
    }

    static String voiceHint(Store store, Long msgId, Long msgSvrId) {
        return hintFromTables(
                store,
                new String[]{"voiceinfo", "voiceinfo2"},
                new String[]{
                        "FileName", "filename", "fileName", "file_name", "voicePath", "voicepath",
                        "fullPath", "fullpath", "path", "localPath", "filePath", "filepath",
                        "amrPath", "silkPath"
                },
                new String[]{
                        "MsgLocalId", "msgLocalId", "msglocalid",
                        "MsgId", "msgId", "msgid", "msgSvrId", "MsgSvrId", "MsgSvrID",
                        "msgsvrid", "svrId", "svrid"
                },
                msgId,
                msgSvrId);
    }

    static String videoHint(Store store, Long msgId, Long msgSvrId) {
        return hintFromTables(
                store,
                new String[]{"videoinfo2"},
                new String[]{"filename", "fileName", "videoPath", "videopath", "fullPath", "fullpath", "path", "thumbPath", "thumbpath"},
                new String[]{"msglocalid", "msgLocalId", "msgid", "msgId", "msgsvrid", "msgSvrId", "MsgSvrID", "svrid", "svrId"},
                msgId,
                msgSvrId);
    }

    private static String hintFromTables(
            Store store,
            String[] tables,
            String[] valueCandidates,
            String[] idCandidates,
            Long msgId,
            Long msgSvrId) {
        for (String table : tables) {
            List<String> columns = store.tableColumns(table);
            if (columns == null || columns.isEmpty()) {
                continue;
            }
            List<String> valueColumns = existingColumns(columns, valueCandidates);
            if (valueColumns.isEmpty()) {
                continue;
            }
            List<String> idColumns = existingColumns(columns, idCandidates);
            if (idColumns.isEmpty()) {
                continue;
            }
            List<Long> ids = ids(msgId, msgSvrId);
            for (String idColumn : idColumns) {
                for (Long id : ids) {
                    String hint = store.queryFirstValue(table, idColumn, id.longValue(), valueColumns);
                    if (!isBlank(hint)) {
                        return hint;
                    }
                }
            }
        }
        return "";
    }

    private static long positiveLongFromTables(
            Store store,
            String[] tables,
            String[] valueCandidates,
            String[] idCandidates,
            Long msgId,
            Long msgSvrId) {
        for (String table : tables) {
            List<String> columns = store.tableColumns(table);
            if (columns == null || columns.isEmpty()) {
                continue;
            }
            List<String> valueColumns = existingColumns(columns, valueCandidates);
            if (valueColumns.isEmpty()) {
                continue;
            }
            List<String> idColumns = existingColumns(columns, idCandidates);
            if (idColumns.isEmpty()) {
                continue;
            }
            List<Long> ids = ids(msgId, msgSvrId);
            for (String idColumn : idColumns) {
                for (Long id : ids) {
                    long parsed = positiveLong(store.queryFirstValue(table, idColumn, id.longValue(), valueColumns));
                    if (parsed > 0L) {
                        return parsed;
                    }
                }
            }
        }
        return 0L;
    }

    private static long positiveLong(String value) {
        if (isBlank(value)) {
            return 0L;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0L ? parsed : 0L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static List<Long> ids(Long msgId, Long msgSvrId) {
        List<Long> ids = new ArrayList<>();
        addPositiveId(ids, msgId);
        addPositiveId(ids, msgSvrId);
        return ids;
    }

    private static void addPositiveId(List<Long> ids, Long value) {
        if (value == null || value.longValue() <= 0L) {
            return;
        }
        for (Long existing : ids) {
            if (existing.longValue() == value.longValue()) {
                return;
            }
        }
        ids.add(value);
    }

    private static List<String> existingColumns(List<String> columns, String[] candidates) {
        List<String> existing = new ArrayList<>();
        if (columns == null || candidates == null) {
            return existing;
        }
        for (String candidate : candidates) {
            String column = existingColumn(columns, candidate);
            if (!isBlank(column)) {
                addUnique(existing, column);
            }
        }
        return existing;
    }

    private static String existingColumn(List<String> columns, String candidate) {
        if (isBlank(candidate)) {
            return "";
        }
        for (String column : columns) {
            if (!isBlank(column) && column.equalsIgnoreCase(candidate)) {
                return column;
            }
        }
        return "";
    }

    private static void addUnique(List<String> values, String value) {
        if (isBlank(value)) {
            return;
        }
        String trimmed = value.trim();
        for (String existing : values) {
            if (existing.equals(trimmed)) {
                return;
            }
        }
        values.add(trimmed);
    }
}
