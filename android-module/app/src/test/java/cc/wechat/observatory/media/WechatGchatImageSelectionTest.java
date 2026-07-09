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

public final class WechatGchatImageSelectionTest {
    @Test
    public void fileBecomesGchatImageFileSelection() throws Exception {
        File image = Files.createTempFile("wxo-gchat-selection", ".jpg").toFile();
        writeJpeg(image);

        MediaFileSelector.Selection selection = new WechatGchatImageSelection(
                content -> image,
                message -> {
                })
                .select(request());

        assertEquals(MediaFileSelector.SelectionStatus.GCHAT_IMAGE_FILE, selection.status());
        assertEquals(image.getCanonicalFile(), selection.file().getCanonicalFile());
    }

    @Test
    public void thumbnailIsRejectedAndLogged() throws Exception {
        File thumbnail = thumbnailFile();
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selection = new WechatGchatImageSelection(
                content -> thumbnail,
                logs::add)
                .select(request());

        assertEquals(MediaFileSelector.SelectionStatus.GCHAT_IMAGE_THUMBNAIL, selection.status());
        assertNull(selection.file());
        assertTrue(contains(logs, "skip thumbnail image upload"));
        assertTrue(contains(logs, "msgId=12"));
    }

    @Test
    public void missingResolverOrFileReturnsNull() {
        assertNull(new WechatGchatImageSelection(null, message -> {
        }).select(request()));
        assertNull(new WechatGchatImageSelection(content -> null, message -> {
        }).select(request()));
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
        File image2 = new File(Files.createTempDirectory("wxo-gchat-thumb").toFile(), "image2");
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
