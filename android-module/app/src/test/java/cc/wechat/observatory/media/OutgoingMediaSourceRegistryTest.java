package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public final class OutgoingMediaSourceRegistryTest {
    @Test
    public void pendingOutgoingMediaMatchesFirstNewLocalMessage() throws Exception {
        File image = File.createTempFile("wxo-outgoing-", ".jpg");
        image.deleteOnExit();
        OutgoingMediaSourceRegistry registry = new OutgoingMediaSourceRegistry();

        registry.rememberPending("room@chatroom", 3, image, 100L);

        String hint = registry.resolveHint(
                Integer.valueOf(1),
                3,
                "room@chatroom",
                Long.valueOf(101L),
                null,
                "thumb");

        assertEquals(image.getAbsolutePath(), hint);
    }

    @Test
    public void boundOutgoingMediaRequiresExactMessageId() throws Exception {
        File image = File.createTempFile("wxo-outgoing-bound-", ".jpg");
        image.deleteOnExit();
        OutgoingMediaSourceRegistry registry = new OutgoingMediaSourceRegistry();

        registry.bind("wxid_target", 3, 200L, image);

        assertEquals("thumb", registry.resolveHint(
                Integer.valueOf(1),
                3,
                "wxid_target",
                Long.valueOf(199L),
                null,
                "thumb"));
        assertEquals(image.getAbsolutePath(), registry.resolveHint(
                Integer.valueOf(1),
                3,
                "wxid_target",
                Long.valueOf(200L),
                null,
                "thumb"));
    }

    @Test
    public void incomingOrServerBackedMessageKeepsOriginalHint() throws Exception {
        File image = File.createTempFile("wxo-outgoing-inbound-", ".jpg");
        image.deleteOnExit();
        OutgoingMediaSourceRegistry registry = new OutgoingMediaSourceRegistry();
        registry.rememberPending("wxid_target", 3, image, 100L);

        assertEquals("thumb", registry.resolveHint(
                Integer.valueOf(0),
                3,
                "wxid_target",
                Long.valueOf(101L),
                null,
                "thumb"));
        assertEquals("thumb", registry.resolveHint(
                Integer.valueOf(1),
                3,
                "wxid_target",
                Long.valueOf(101L),
                Long.valueOf(999L),
                "thumb"));
    }
}
