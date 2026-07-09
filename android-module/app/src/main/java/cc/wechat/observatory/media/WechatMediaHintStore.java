package cc.wechat.observatory.media;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class WechatMediaHintStore implements MediaHintResolver.Store {
    private final Object db;

    public WechatMediaHintStore(Object db) {
        this.db = db;
    }

    @Override
    public List<String> tableColumns(String table) {
        List<String> columns = new ArrayList<>();
        if (db == null) {
            return columns;
        }
        Object cursor = null;
        try {
            cursor = rawQuery("PRAGMA table_info(" + sqlIdentifier(table) + ")", new String[]{});
            if (cursor == null) {
                return columns;
            }
            Method moveToNext = findNoArgMethod(cursor.getClass(), "moveToNext");
            while (Boolean.TRUE.equals(moveToNext.invoke(cursor))) {
                addUnique(columns, stringColumn(cursor, 1));
            }
        } catch (Throwable ignored) {
            return columns;
        } finally {
            closeQuietly(cursor);
        }
        return columns;
    }

    @Override
    public String queryFirstValue(String table, String idColumn, long id, List<String> valueColumns) {
        if (db == null || id <= 0L || valueColumns == null || valueColumns.isEmpty()) {
            return "";
        }
        String sql = "SELECT " + joinIdentifiers(valueColumns)
                + " FROM " + sqlIdentifier(table)
                + " WHERE " + sqlIdentifier(idColumn) + "=? LIMIT 1";
        Object cursor = null;
        try {
            cursor = rawQuery(sql, new String[]{String.valueOf(id)});
            if (cursor == null) {
                return "";
            }
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (!Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return "";
            }
            for (int i = 0; i < valueColumns.size(); i++) {
                String value = stringColumn(cursor, i);
                if (!isBlank(value)) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
            return "";
        } finally {
            closeQuietly(cursor);
        }
        return "";
    }

    private Object rawQuery(String sql, String[] args) throws Exception {
        try {
            Method rawQuery = findMethod(db.getClass(), "rawQuery", String.class, Object[].class);
            return rawQuery.invoke(db, sql, (Object) args);
        } catch (NoSuchMethodException ignored) {
            try {
                Method rawQuery = findMethod(
                        db.getClass(),
                        "rawQuery",
                        String.class,
                        Object[].class,
                        Class.forName("com.tencent.wcdb.support.CancellationSignal", false, db.getClass().getClassLoader()));
                return rawQuery.invoke(db, sql, (Object) args, null);
            } catch (Throwable ignoredObjectOverload) {
                try {
                    Method rawQuery = findMethod(db.getClass(), "rawQuery", String.class, String[].class);
                    return rawQuery.invoke(db, sql, (Object) args);
                } catch (NoSuchMethodException ignoredStringOverload) {
                    Method rawQuery = findMethod(db.getClass(), "rawQuery", String.class, String[].class, int.class);
                    return rawQuery.invoke(db, sql, (Object) args, 0);
                }
            }
        }
    }

    private static String stringColumn(Object cursor, int index) {
        try {
            Object value = findMethod(cursor.getClass(), "getString", int.class).invoke(cursor, index);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void closeQuietly(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            findNoArgMethod(closeable.getClass(), "close").invoke(closeable);
        } catch (Throwable ignored) {
            // Ignore close failures from WeChat cursor implementations.
        }
    }

    private static Method findNoArgMethod(Class<?> cls, String name) throws NoSuchMethodException {
        return findMethod(cls, name);
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = cls;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        Method method = cls.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static String joinIdentifiers(List<String> identifiers) {
        StringBuilder builder = new StringBuilder();
        if (identifiers == null) {
            return "";
        }
        for (String identifier : identifiers) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(sqlIdentifier(identifier));
        }
        return builder.toString();
    }

    private static String sqlIdentifier(String name) {
        if (name == null) {
            return "``";
        }
        return "`" + name.replace("`", "``") + "`";
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
