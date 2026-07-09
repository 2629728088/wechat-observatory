package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageBaseSelectionTest {
    @Test
    public void fileCandidateBecomesImmediateBaseSelection() throws Exception {
        File image = Files.createTempFile("wxo-base-selection", ".jpg").toFile();
        writeJpeg(image);

        WechatImageBaseSelection.Result result = new WechatImageBaseSelection(message -> {
        }).select(request(), ImageDownloadResolution.Candidate.fromFile(image));

        assertEquals(MediaFileSelector.SelectionStatus.BASE_FILE, result.immediateSelection().status());
        assertEquals(image.getCanonicalFile(), result.immediateSelection().file().getCanonicalFile());
        assertNull(result.unsupportedFallback());
    }

    @Test
    public void thumbnailCandidateLogsAndDefersToDownload() throws Exception {
        File thumbnail = thumbnailFile();
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        WechatImageBaseSelection.Result result = new WechatImageBaseSelection(logs::add)
                .select(request(), ImageDownloadResolution.Candidate.fromFile(thumbnail));

        assertNull(result.immediateSelection());
        assertNull(result.unsupportedFallback());
        assertTrue(contains(logs, "thumbnail only, requesting full image"));
        assertTrue(contains(logs, "msgId=12"));
    }

    @Test
    public void unsupportedCandidateLogsAndKeepsFallbackStatus() throws Exception {
        File unsupported = Files.createTempFile("wxo-base-selection-unsupported", ".ref").toFile();
        List<String> logs = new ArrayList<>();

        WechatImageBaseSelection.Result result = new WechatImageBaseSelection(logs::add)
                .select(request(), ImageDownloadResolution.Candidate.unsupported(unsupported));

        assertNull(result.immediateSelection());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_UNSUPPORTED, result.unsupportedFallback().status());
        assertNull(result.unsupportedFallback().file());
        assertTrue(contains(logs, "unsupported candidate, requesting full image"));
        assertTrue(contains(logs, "msgId=12"));
    }

    @Test
    public void missingCandidateHasNoSelection() {
        WechatImageBaseSelection.Result result = new WechatImageBaseSelection(message -> {
        }).select(request(), ImageDownloadResolution.Candidate.missing());

        assertNull(result.immediateSelection());
        assertNull(result.unsupportedFallback());
    }

    private static MediaFileSelector.Request request() {
        return new MediaFileSelector.Request(
                3,
                "media.jpg",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "",
                12L);
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-base-selection-thumb").toFile(), "image2");
        return new File(image2, "th_abcd1234");
    }

    private static void writeJpeg(File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        byte[] bytes = new byte[512];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xd8;
        bytes[2] = (byte) 0xff;
        bytes[3] = (byte) 0xe0;
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
    }

    private static boolean contains(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
