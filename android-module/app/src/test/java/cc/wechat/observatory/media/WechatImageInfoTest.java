package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatImageInfoTest {
    @Test
    public void localIdPrefersKnownFieldsBeforeMethods() {
        InfoWithIds info = new InfoWithIds();
        info.a = 12L;
        info.methodId = 88L;

        assertEquals(12L, WechatImageInfo.localId(info));
    }

    @Test
    public void localIdFallsBackToKnownMethods() {
        InfoWithIds info = new InfoWithIds();
        info.methodId = 88L;

        assertEquals(88L, WechatImageInfo.localId(info));
    }

    @Test
    public void stringValuesReadInstanceStringFieldsAcrossHierarchy() {
        ChildInfo info = new ChildInfo();
        info.path = "child.jpg";
        info.empty = " ";

        List<String> values = WechatImageInfo.stringValues(info);

        assertTrue(values.contains("child.jpg"));
        assertTrue(values.contains("parent.jpg"));
        assertFalse(values.contains("static.jpg"));
        assertFalse(values.contains(" "));
    }

    @Test
    public void stringFieldDebugLimitsAndCompactsValues() {
        ChildInfo info = new ChildInfo();
        info.path = "first\nsecond\rthird";

        List<String> fields = WechatImageInfo.stringFieldDebug(info, 1, 10);

        assertEquals(1, fields.size());
        assertEquals("path=first seco...", fields.get(0));
    }

    @Test
    public void stringFieldDebugRedactsSensitiveNamesAndValues() {
        SensitiveInfo info = new SensitiveInfo();

        List<String> fields = WechatImageInfo.stringFieldDebug(info, 4, 80);

        assertTrue(fields.contains("accessToken=<redacted>"));
        assertTrue(fields.contains("url=<redacted>"));
        assertTrue(fields.contains("avatarWxid=<redacted>"));
        assertFalse(fields.toString().contains("real-token"));
        assertFalse(fields.toString().contains("secret-value"));
        assertFalse(fields.toString().contains("wxid_123"));
    }

    private static final class InfoWithIds {
        long a;
        long methodId;

        long getId() {
            return methodId;
        }
    }

    private static class ParentInfo {
        String parentPath = "parent.jpg";
        static String staticPath = "static.jpg";
    }

    private static final class ChildInfo extends ParentInfo {
        String path;
        String empty;
    }

    private static final class SensitiveInfo {
        String accessToken = "real-token";
        String url = "https://example.test/image.jpg?token=secret-value";
        String avatarWxid = "wxid_123";
    }
}
