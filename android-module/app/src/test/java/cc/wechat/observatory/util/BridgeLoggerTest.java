package cc.wechat.observatory.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BridgeLoggerTest {
    @Test
    public void sanitizeRedactsWechatIdentifiersAndSecrets() {
        String line = BridgeLogger.sanitize(
                "module registered wxid=wxid_1abcDEF_234 chat=123456@chatroom api_key=secret-token cookie=session-value");

        assertTrue(line.contains("wxid=<redacted>"));
        assertTrue(line.contains("chat=<chatroom>"));
        assertTrue(line.contains("api_key=<redacted>"));
        assertTrue(line.contains("cookie=<redacted>"));
        assertFalse(line.contains("wxid_1abcDEF_234"));
        assertFalse(line.contains("123456@chatroom"));
        assertFalse(line.contains("secret-token"));
        assertFalse(line.contains("session-value"));
    }

    @Test
    public void sanitizeRedactsJsonSecrets() {
        String line = BridgeLogger.sanitize(
                "{\"wxid\":\"wxid_real_user\",\"token\":\"secret\",\"count\":2}");

        assertTrue(line.contains("\"wxid\":\"<redacted>\""));
        assertTrue(line.contains("\"token\":\"<redacted>\""));
        assertTrue(line.contains("\"count\":2"));
        assertFalse(line.contains("wxid_real_user"));
        assertFalse(line.contains("secret"));
    }

    @Test
    public void sanitizeRedactsLongBase64Payloads() {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            payload.append('A');
        }

        String line = BridgeLogger.sanitize("media_base64=" + payload);

        assertTrue(line.contains("media_base64=<redacted>"));
        assertFalse(line.contains(payload.toString()));
    }

    @Test
    public void sanitizeRedactsWechatMediaSecrets() {
        String line = BridgeLogger.sanitize(
                "field_aeskey=secret-aes field_tpauthkey=secret-auth field_tpurl=https://cdn.example.test/a.gif?token=secret");

        assertTrue(line.contains("field_aeskey=<redacted>"));
        assertTrue(line.contains("field_tpauthkey=<redacted>"));
        assertTrue(line.contains("field_tpurl=<redacted>"));
        assertFalse(line.contains("secret-aes"));
        assertFalse(line.contains("secret-auth"));
        assertFalse(line.contains("cdn.example.test"));
    }

    @Test
    public void sanitizeKeepsOperationalFields() {
        String line = BridgeLogger.sanitize("media retry uploaded type=3 msgId=123 attempt=2 size=456");

        assertTrue(line.contains("type=3"));
        assertTrue(line.contains("msgId=123"));
        assertTrue(line.contains("attempt=2"));
        assertTrue(line.contains("size=456"));
    }
}
