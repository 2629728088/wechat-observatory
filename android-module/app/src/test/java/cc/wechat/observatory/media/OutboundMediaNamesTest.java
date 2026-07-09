package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public final class OutboundMediaNamesTest {
    @Test
    public void videoBaseNameKeepsAsciiLettersDigitsDashAndUnderscore() {
        assertEquals("clip_01-final", OutboundMediaNames.videoBaseName(new File("clip_01-final!.mp4"), 123L));
    }

    @Test
    public void videoBaseNameFallsBackWhenSanitizedNameIsEmpty() {
        assertEquals("outbox_123", OutboundMediaNames.videoBaseName(new File("!!!.mp4"), 123L));
    }

    @Test
    public void voiceBaseNameAddsWechatPrefixAndKeepsSafeBaseName() {
        assertEquals("wo_voice_456_voice-ABC_123", OutboundMediaNames.voiceBaseName(new File("voice-ABC_123.silk"), 456L));
    }

    @Test
    public void voiceBaseNameTruncatesSanitizedBaseName() {
        assertEquals(
                "wo_voice_7_abcdefghijklmnopqrstuvwxyz012345",
                OutboundMediaNames.voiceBaseName(new File("abcdefghijklmnopqrstuvwxyz0123456789.silk"), 7L));
    }

    @Test
    public void voiceBaseNameFallsBackWhenSanitizedNameIsEmpty() {
        assertEquals("wo_voice_456_voice", OutboundMediaNames.voiceBaseName(new File("!!!.silk"), 456L));
    }
}
