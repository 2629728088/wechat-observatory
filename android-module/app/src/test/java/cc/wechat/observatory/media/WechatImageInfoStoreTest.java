package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class WechatImageInfoStoreTest {
    @Test
    public void loadPrefersTalkerLocalMessageInfo() {
        RecordingLoader loader = new RecordingLoader("T1:room:10", 101L);

        Object info = WechatImageInfoStore.load(new Object(), 10L, 20L, "room", loader);

        assertEquals(101L, WechatImageInfo.localId(info));
        assertEquals(1, loader.calls.size());
        assertEquals("T1:room:10", loader.calls.get(0));
    }

    @Test
    public void loadFallsThroughInStableOrderUntilInfoIsFound() {
        RecordingLoader loader = new RecordingLoader("C1:10", 303L);

        Object info = WechatImageInfoStore.load(new Object(), 10L, 20L, "room", loader);

        assertEquals(303L, WechatImageInfo.localId(info));
        assertEquals("T1:room:10", loader.calls.get(0));
        assertEquals("b2:room:20", loader.calls.get(1));
        assertEquals("C1:10", loader.calls.get(2));
        assertEquals(3, loader.calls.size());
    }

    @Test
    public void downloadIdFallsBackToMessageIdWhenLookupMisses() {
        RecordingLoader loader = new RecordingLoader("", 0L);

        long id = WechatImageInfoStore.downloadIdOrFallback(new Object(), 10L, 20L, "room", loader);

        assertEquals(10L, id);
        assertEquals(4, loader.calls.size());
    }

    private static final class RecordingLoader implements WechatImageInfoStore.Loader {
        private final String match;
        private final long localId;
        final List<String> calls = new ArrayList<>();

        RecordingLoader(String match, long localId) {
            this.match = match;
            this.localId = localId;
        }

        @Override
        public Object load(Object storage, String methodName, Object... args) {
            String signature = signature(methodName, args);
            calls.add(signature);
            if (signature.equals(match)) {
                FakeInfo info = new FakeInfo();
                info.a = localId;
                return info;
            }
            return null;
        }

        private static String signature(String methodName, Object[] args) {
            if (args.length == 2) {
                return methodName + ":" + args[0] + ":" + args[1];
            }
            if (args.length == 1) {
                return methodName + ":" + args[0];
            }
            return methodName;
        }
    }

    private static final class FakeInfo {
        long a;
    }
}
