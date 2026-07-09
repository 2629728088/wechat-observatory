package cc.wechat.observatory.outbox;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class OutboxMediaSpecTest {
    @Test
    public void usesTopLevelMediaFieldsBeforePayloadFields() {
        OutboxMediaSpec spec = OutboxMediaSpec.fromValues(
                "/module/media/top.jpg",
                "top.jpg",
                2000,
                0,
                "/module/media/payload.jpg",
                "payload.jpg",
                3000,
                0);

        assertEquals("/module/media/top.jpg", spec.mediaUrl);
        assertEquals("top.jpg", spec.mediaName);
        assertEquals(2000, spec.durationMs);
    }

    @Test
    public void fallsBackToPayloadMediaFields() {
        OutboxMediaSpec spec = OutboxMediaSpec.fromValues(
                "",
                "",
                0,
                0,
                "/module/media/payload.silk",
                "payload.silk",
                0,
                4200);

        assertEquals("/module/media/payload.silk", spec.mediaUrl);
        assertEquals("payload.silk", spec.mediaName);
        assertEquals(4200, spec.durationMs);
    }

    @Test
    public void emptyItemUsesSafeDefaults() {
        OutboxMediaSpec spec = OutboxMediaSpec.from(null);

        assertEquals("", spec.mediaUrl);
        assertEquals("", spec.mediaName);
        assertEquals(OutboxMediaSpec.DEFAULT_VOICE_DURATION_MS, spec.durationMs);
    }
}
