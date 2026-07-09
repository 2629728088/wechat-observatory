package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class WechatImageDownloadRequesterRuntimeAdaptersTest {
    @Test
    public void imageInfoIdResolverAlwaysReturnsFixedImageInfoId() {
        WechatImageDownloadRuntime.ImageInfoIdResolver resolver =
                WechatImageDownloadRequesterRuntimeAdapters.imageInfoIdResolver(500L);

        assertEquals(500L, resolver.resolve(getClass().getClassLoader(), 10L, 20L, "talker"));
        assertEquals(500L, resolver.resolve(null, 11L, 21L, null));
    }

    @Test
    public void loggerIgnoresMissingDelegateAndForwardsMessages() {
        WechatImageDownloadRequesterRuntimeAdapters.logger(null).log("ignored");
        List<String> logs = new ArrayList<>();

        WechatImageDownloadRequesterRuntimeAdapters.logger(new WechatImageDownloadRequester.Logger() {
            @Override
            public void log(String message) {
                logs.add(message);
            }
        }).log("message");

        assertEquals(1, logs.size());
        assertEquals("message", logs.get(0));
    }
}
