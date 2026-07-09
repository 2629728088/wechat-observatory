package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class EmojiMediaParserTest {
    private static final String MD5_UPPER = "ABCDEF1234567890ABCDEF1234567890";
    private static final String MD5_LOWER = "abcdef1234567890abcdef1234567890";
    private static final String OTHER_MD5 = "00112233445566778899aabbccddeeff";

    @Test
    public void md5FromContentPrefersNormalizedXmlMd5() {
        String raw = "<msg><emoji md5=\"" + OTHER_MD5 + "\" /></msg>";
        String normalized = "<msg><emoji md5=\"" + MD5_UPPER + "\" /></msg>";

        assertEquals(MD5_LOWER, EmojiMediaParser.md5FromContent(normalized, raw, ""));
    }

    @Test
    public void md5FromContentFallsBackToRawAndroidMd5() {
        String raw = "<msg><emoji androidmd5=\"" + MD5_UPPER + "\" /></msg>";

        assertEquals(MD5_LOWER, EmojiMediaParser.md5FromContent("", raw, ""));
    }

    @Test
    public void md5FromContentReadsColonPayloadBeforeEmbeddedXml() {
        String normalizedText = "prefix:" + MD5_UPPER + ":ignored <msg><emoji md5=\"" + OTHER_MD5 + "\" /></msg>";

        assertEquals(MD5_LOWER, EmojiMediaParser.md5FromContent("", "", normalizedText));
    }

    @Test
    public void md5FromWechatContentStripsChatroomSenderPrefix() {
        String content = "wxid_sender:\n<msg><emoji md5=\"" + MD5_UPPER + "\" /></msg>";

        assertEquals(MD5_LOWER, EmojiMediaParser.md5FromWechatContent("room@chatroom", content));
    }

    @Test
    public void md5FromWechatContentPrefersEmbeddedXmlBeforeColonPayload() {
        String content = "wxid_sender:\nprefix:" + MD5_UPPER + ":ignored <msg><emoji md5=\"" + OTHER_MD5 + "\" /></msg>";

        assertEquals(OTHER_MD5, EmojiMediaParser.md5FromWechatContent("room@chatroom", content));
    }

    @Test
    public void md5FromColonPayloadIgnoresInvalidMd5Parts() {
        assertEquals("", EmojiMediaParser.md5FromColonPayload("prefix:not-md5:12345"));
    }

    @Test
    public void extractXmlAttributeKeepsAttributeNameBoundaries() {
        String xml = "<emoji androidmd5=\"" + OTHER_MD5 + "\" />";

        assertEquals("", EmojiMediaParser.extractXmlAttribute(xml, "md5"));
        assertEquals(OTHER_MD5, EmojiMediaParser.extractXmlAttribute(xml, "androidmd5"));
    }
}
