package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class WechatImageLoggerAdaptersTest {
    @Test
    public void fileLoggerForwardsMessages() {
        List<String> logs = new ArrayList<>();

        WechatImageFileResolver.Logger logger =
                WechatImageLoggerAdapters.fileLogger(logs::add);
        logger.log("image log");

        assertEquals(1, logs.size());
        assertEquals("image log", logs.get(0));
    }

    @Test
    public void referenceLoggerForwardsMessages() {
        List<String> logs = new ArrayList<>();

        WechatImageReferenceResolver.Logger logger =
                WechatImageLoggerAdapters.referenceLogger(logs::add);
        logger.log("reference log");

        assertEquals(1, logs.size());
        assertEquals("reference log", logs.get(0));
    }

    @Test
    public void nullLoggersStayNull() {
        assertNull(WechatImageLoggerAdapters.fileLogger(null));
        assertNull(WechatImageLoggerAdapters.referenceLogger(null));
    }
}
