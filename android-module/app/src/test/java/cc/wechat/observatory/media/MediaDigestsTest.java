package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaDigestsTest {
    @Test
    public void normalizeMd5TrimsAndLowercasesValue() {
        assertEquals(
                "abcdef0123456789abcdef0123456789",
                MediaDigests.normalizeMd5(" ABCDEF0123456789ABCDEF0123456789 "));
        assertEquals("", MediaDigests.normalizeMd5(" "));
        assertEquals("", MediaDigests.normalizeMd5(null));
    }

    @Test
    public void isMd5HexAcceptsOnlyThirtyTwoHexCharacters() {
        assertTrue(MediaDigests.isMd5Hex("abcdef0123456789abcdef0123456789"));
        assertTrue(MediaDigests.isMd5Hex("ABCDEF0123456789ABCDEF0123456789"));
        assertFalse(MediaDigests.isMd5Hex("abcdef"));
        assertFalse(MediaDigests.isMd5Hex("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"));
        assertFalse(MediaDigests.isMd5Hex(null));
    }

    @Test
    public void shortMd5KeepsLogsCompactWithoutRevealingFullValue() {
        assertEquals("<empty>", MediaDigests.shortMd5(""));
        assertEquals("abc123", MediaDigests.shortMd5("abc123"));
        assertEquals("abcdef01...456789", MediaDigests.shortMd5("abcdef0123456789abcdef0123456789"));
    }
}
