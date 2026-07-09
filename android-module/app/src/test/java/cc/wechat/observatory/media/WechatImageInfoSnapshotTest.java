package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatImageInfoSnapshotTest {
    @Test
    public void fromExtractsIdValuesAndDiagnosticsOnce() {
        FakeImageInfo info = new FakeImageInfo();
        info.a = 31L;
        info.path = "abcd1234ef567890abcd1234ef567890.jpg";

        WechatImageInfoSnapshot snapshot = WechatImageInfoSnapshot.from(info);

        assertEquals(31L, snapshot.localInfoId());
        assertTrue(snapshot.values().contains(info.path));
        assertTrue(snapshot.fieldDebug().contains("path=" + info.path));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void valuesAreImmutable() {
        WechatImageInfoSnapshot snapshot = WechatImageInfoSnapshot.of(
                32L,
                Collections.singletonList("one.jpg"),
                null);

        snapshot.values().add("two.jpg");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void diagnosticsAreImmutable() {
        WechatImageInfoSnapshot snapshot = WechatImageInfoSnapshot.of(
                33L,
                null,
                Arrays.asList("path=one.jpg"));

        snapshot.fieldDebug().add("path=two.jpg");
    }

    private static final class FakeImageInfo {
        long a;
        String path;
    }
}
