package cc.wechat.observatory.media;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class WechatImageInfo {
    private WechatImageInfo() {
    }

    public static long localId(Object info) {
        return firstPositiveLong(
                optionalLongField(info, "a", "field_id", "id", "localId", "localid"),
                optionalLongMethod(info, "getId"),
                optionalLongMethod(info, "getMsgLocalId"));
    }

    public static List<String> stringValues(Object info) {
        List<String> values = new ArrayList<>();
        if (info == null) {
            return values;
        }
        Set<String> seen = new HashSet<>();
        Class<?> current = info.getClass();
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (field == null
                        || field.getType() != String.class
                        || Modifier.isStatic(field.getModifiers())
                        || !seen.add(field.getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(info);
                    if (value != null && !isBlank(String.valueOf(value))) {
                        values.add(String.valueOf(value));
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return values;
    }

    public static List<String> stringFieldDebug(Object info, int fieldLimit, int valueLimit) {
        List<String> values = new ArrayList<>();
        if (info == null || fieldLimit <= 0) {
            return values;
        }
        Set<String> seen = new HashSet<>();
        Class<?> current = info.getClass();
        while (current != null && current != Object.class && values.size() < fieldLimit) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (field == null
                        || field.getType() != String.class
                        || Modifier.isStatic(field.getModifiers())
                        || !seen.add(field.getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object raw = field.get(info);
                    if (raw != null && !isBlank(String.valueOf(raw))) {
                        values.add(field.getName() + "=" + debugValue(field.getName(), String.valueOf(raw), valueLimit));
                        if (values.size() >= fieldLimit) {
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return values;
    }

    private static long firstPositiveLong(long... values) {
        if (values == null) {
            return 0L;
        }
        for (long value : values) {
            if (value > 0L) {
                return value;
            }
        }
        return 0L;
    }

    private static long optionalLongField(Object target, String... names) {
        if (target == null || names == null) {
            return 0L;
        }
        for (String name : names) {
            if (isBlank(name)) {
                continue;
            }
            try {
                Field field = findField(target.getClass(), name);
                Object value = field.get(target);
                return longValue(value);
            } catch (Throwable ignored) {
            }
        }
        return 0L;
    }

    private static long optionalLongMethod(Object target, String name) {
        if (target == null || isBlank(name)) {
            return 0L;
        }
        try {
            Method method = findNoArgMethod(target.getClass(), name);
            Object value = method.invoke(target);
            return longValue(value);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findNoArgMethod(Class<?> cls, String name) throws NoSuchMethodException {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            Method[] methods = current.getDeclaredMethods();
            for (Method method : methods) {
                if (method != null && method.getParameterTypes().length == 0 && name.equals(method.getName())) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(name);
    }

    private static long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String compact(String value, int limit) {
        if (value == null) {
            return "";
        }
        String compact = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (limit > 0 && compact.length() > limit) {
            return compact.substring(0, limit) + "...";
        }
        return compact;
    }

    private static String debugValue(String fieldName, String value, int limit) {
        if (isSensitiveDebugValue(fieldName, value)) {
            return "<redacted>";
        }
        return compact(value, limit);
    }

    private static boolean isSensitiveDebugValue(String fieldName, String value) {
        String field = fieldName == null ? "" : fieldName.toLowerCase();
        String raw = value == null ? "" : value.toLowerCase();
        return containsSensitiveToken(field)
                || containsSensitiveToken(raw)
                || looksLikeBase64Payload(value);
    }

    private static boolean containsSensitiveToken(String value) {
        if (value == null) {
            return false;
        }
        return value.contains("token")
                || value.contains("key")
                || value.contains("secret")
                || value.contains("password")
                || value.contains("cookie")
                || value.contains("session")
                || value.contains("credential")
                || value.contains("auth")
                || value.contains("wxid")
                || value.contains("base64");
    }

    private static boolean looksLikeBase64Payload(String value) {
        if (value == null) {
            return false;
        }
        String compact = value.replace("\n", "").replace("\r", "").replace(" ", "").trim();
        return compact.length() > 256 && compact.matches("^[A-Za-z0-9+/=]+$");
    }
}
