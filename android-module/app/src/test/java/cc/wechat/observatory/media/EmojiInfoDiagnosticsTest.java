package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EmojiInfoDiagnosticsTest {
    @Test
    public void fieldSummaryReadsInstanceStringFieldsAcrossHierarchy() {
        ChildEmojiInfo info = new ChildEmojiInfo();

        String summary = EmojiInfoDiagnostics.fieldSummary(info);

        assertTrue(summary.contains("path=/data/user/0/com.tencent.mm/emoji/file"));
        assertTrue(summary.contains("parent=parent-value"));
        assertTrue(summary.contains("url=<redacted>"));
        assertTrue(summary.contains("field_aeskey=<redacted>"));
        assertTrue(summary.contains("field_tpauthkey=<redacted>"));
        assertTrue(summary.contains("md5=abcdef12...567890"));
        assertFalse(summary.contains("static-value"));
        assertFalse(summary.contains("blank="));
        assertFalse(summary.contains("secret-aes-value"));
        assertFalse(summary.contains("secret-auth-value"));
    }

    @Test
    public void fieldSummaryReportsNoStringFields() {
        assertEquals("[no-string-fields]", EmojiInfoDiagnostics.fieldSummary(new NoStringInfo()));
    }

    @Test
    public void fieldSummaryHandlesNullInfo() {
        assertEquals("[]", EmojiInfoDiagnostics.fieldSummary(null));
    }

    @Test
    public void summarizeValueCompactsUrlsMd5sAndPlainText() {
        assertEquals("<url>", EmojiInfoDiagnostics.summarizeValue("https://example.test/emoji.gif"));
        assertEquals("abcdef12...567890", EmojiInfoDiagnostics.summarizeValue("abcdef1234567890abcdef1234567890"));
        assertEquals("plain", EmojiInfoDiagnostics.summarizeValue("plain"));
    }

    private static class ParentEmojiInfo {
        String parent = "parent-value";
        static String ignored = "static-value";
    }

    private static final class ChildEmojiInfo extends ParentEmojiInfo {
        String path = "/data/user/0/com.tencent.mm/emoji/file";
        String url = "https://example.test/emoji.gif";
        String md5 = "abcdef1234567890abcdef1234567890";
        String field_aeskey = "secret-aes-value";
        String field_tpauthkey = "secret-auth-value";
        String blank = " ";
    }

    private static final class NoStringInfo {
        int value = 1;
    }
}
