package cc.wechat.observatory.media;

import java.util.Locale;

import static cc.wechat.observatory.util.Strings.isBlank;

public final class EmojiMediaParser {
    private EmojiMediaParser() {
    }

    public static String md5FromWechatContent(String talker, String content) {
        String normalizedText = normalizeWechatText(talker, content);
        String embeddedXml = embeddedXmlPayload(normalizedText);
        String normalizedContent = isBlank(embeddedXml) ? normalizedText : embeddedXml;
        return md5FromContent(normalizedContent, content, normalizedText);
    }

    public static String md5FromContent(String normalizedContent, String rawContent, String normalizedText) {
        return firstNonBlank(
                MediaDigests.normalizeMd5(extractXmlAttribute(normalizedContent, "md5")),
                MediaDigests.normalizeMd5(extractXmlAttribute(normalizedContent, "androidmd5")),
                MediaDigests.normalizeMd5(extractXmlAttribute(normalizedContent, "externmd5")),
                MediaDigests.normalizeMd5(extractXmlAttribute(rawContent, "md5")),
                MediaDigests.normalizeMd5(extractXmlAttribute(rawContent, "androidmd5")),
                MediaDigests.normalizeMd5(extractXmlAttribute(rawContent, "externmd5")),
                md5FromColonPayload(normalizedText));
    }

    static String md5FromColonPayload(String content) {
        if (isBlank(content)) {
            return "";
        }
        String text = content.trim();
        String prefix = text;
        int xmlStart = embeddedXmlStart(text);
        if (xmlStart > 0) {
            prefix = text.substring(0, xmlStart);
        }
        String[] parts = prefix.split(":");
        for (String part : parts) {
            String candidate = MediaDigests.normalizeMd5(part);
            if (MediaDigests.isMd5Hex(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    static String extractXmlAttribute(String xml, String attribute) {
        if (isBlank(xml) || isBlank(attribute)) {
            return "";
        }
        String text = xml.trim();
        String lower = text.toLowerCase(Locale.US);
        String name = attribute.trim().toLowerCase(Locale.US);
        int searchFrom = 0;
        while (searchFrom < lower.length()) {
            int start = lower.indexOf(name, searchFrom);
            if (start < 0) {
                return "";
            }
            int before = start - 1;
            int afterName = start + name.length();
            boolean boundaryBefore = before < 0 || !isXmlNameChar(lower.charAt(before));
            boolean boundaryAfter = afterName < lower.length() && lower.charAt(afterName) == '=';
            if (!boundaryBefore || !boundaryAfter) {
                searchFrom = afterName;
                continue;
            }
            int valueStart = afterName + 1;
            while (valueStart < text.length() && Character.isWhitespace(text.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart >= text.length()) {
                return "";
            }
            char quote = text.charAt(valueStart);
            int valueEnd;
            if (quote == '"' || quote == '\'') {
                valueStart++;
                valueEnd = text.indexOf(quote, valueStart);
                if (valueEnd < 0) {
                    return "";
                }
            } else {
                valueEnd = valueStart;
                while (valueEnd < text.length()) {
                    char ch = text.charAt(valueEnd);
                    if (Character.isWhitespace(ch) || ch == '/' || ch == '>') {
                        break;
                    }
                    valueEnd++;
                }
            }
            return stripCData(text.substring(valueStart, valueEnd)).trim();
        }
        return "";
    }

    private static String normalizeWechatText(String talker, String content) {
        if (isBlank(content)) {
            return "";
        }
        String text = content.trim();
        if (!isChatroomTalker(talker)) {
            return text;
        }
        int newline = text.indexOf(":\n");
        if (newline <= 0) {
            return text;
        }
        String prefix = text.substring(0, newline).trim();
        String body = text.substring(newline + 2).trim();
        if (looksLikeWxid(prefix) && !isBlank(body)) {
            return body;
        }
        return text;
    }

    private static String embeddedXmlPayload(String value) {
        int start = embeddedXmlStart(value);
        if (start < 0) {
            return "";
        }
        return value.substring(start).trim();
    }

    private static int embeddedXmlStart(String value) {
        if (isBlank(value)) {
            return -1;
        }
        String lower = value.toLowerCase(Locale.US);
        int best = -1;
        String[] markers = new String[]{"<msg", "<emoji", "<?xml", "<sysmsg", "<appmsg"};
        for (String marker : markers) {
            int index = lower.indexOf(marker);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static boolean isChatroomTalker(String talker) {
        return !isBlank(talker) && talker.toLowerCase(Locale.US).endsWith("@chatroom");
    }

    private static boolean looksLikeWxid(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.length() < 3
                || normalized.indexOf(' ') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            return false;
        }
        return normalized.startsWith("wxid_")
                || normalized.startsWith("gh_")
                || normalized.contains("@chatroom")
                || normalized.contains("_");
    }

    private static boolean isXmlNameChar(char ch) {
        return (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9')
                || ch == '_'
                || ch == '-'
                || ch == ':';
    }

    private static String stripCData(String value) {
        value = value == null ? "" : value.trim();
        if (value.startsWith("<![CDATA[") && value.endsWith("]]>")) {
            return value.substring("<![CDATA[".length(), value.length() - "]]>".length()).trim();
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
