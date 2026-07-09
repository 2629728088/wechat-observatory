package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class NonImageMediaSelectionStrategyTest {
    @Test
    public void selectUsesBaseCandidateFile() throws Exception {
        File file = Files.createTempFile("wxo-video", ".mp4").toFile();
        writeByte(file);

        MediaFileSelector.Selection selected = new NonImageMediaSelectionStrategy(emojiMd5 -> null)
                .select(request(43), ImageDownloadResolution.Candidate.fromFile(file));

        assertEquals(file.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, selected.status());
    }

    @Test
    public void selectKeepsBaseRefTargetStatus() throws Exception {
        File file = Files.createTempFile("wxo-ref", ".dat").toFile();
        writeByte(file);

        MediaFileSelector.Selection selected = new NonImageMediaSelectionStrategy(emojiMd5 -> null)
                .select(request(43), ImageDownloadResolution.Candidate.refTarget(file));

        assertEquals(file.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_REF_TARGET, selected.status());
    }

    @Test
    public void selectUsesEmojiFallbackForEmojiMessages() throws Exception {
        File emoji = Files.createTempFile("wxo-emoji", ".gif").toFile();
        writeByte(emoji);

        MediaFileSelector.Selection selected = new NonImageMediaSelectionStrategy(emojiMd5 -> emoji)
                .select(request(47), ImageDownloadResolution.Candidate.missing());

        assertEquals(emoji.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.EMOJI_FILE, selected.status());
    }

    @Test
    public void selectDoesNotUseEmojiFallbackForNonEmojiMessages() throws Exception {
        File emoji = Files.createTempFile("wxo-emoji-unused", ".gif").toFile();
        writeByte(emoji);

        MediaFileSelector.Selection selected = new NonImageMediaSelectionStrategy(emojiMd5 -> emoji)
                .select(request(43), ImageDownloadResolution.Candidate.missing());

        assertNull(selected);
    }

    @Test
    public void selectReturnsNullWhenNoNonImageCandidateExists() {
        MediaFileSelector.Selection selected = new NonImageMediaSelectionStrategy(emojiMd5 -> null)
                .select(request(34), ImageDownloadResolution.Candidate.missing());

        assertNull(selected);
    }

    private static MediaFileSelector.Request request(int type) {
        return new MediaFileSelector.Request(
                type,
                "",
                Long.valueOf(1L),
                Long.valueOf(2L),
                3L,
                "talker",
                "",
                "emoji-md5",
                4L);
    }

    private static void writeByte(File file) throws Exception {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(1);
        }
    }
}
