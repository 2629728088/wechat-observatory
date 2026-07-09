package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadResolutionLogLineTest {
    @Test
    public void imageInfoLogsKeepSourceAndFileName() throws Exception {
        File file = Files.createTempFile("wxo-log-info", ".jpg").toFile();
        writeBytes(file, 512);

        assertEquals(
                "image media resolved from ImgInfo msgId=10 file=" + file.getName(),
                ImageDownloadResolutionLogLine.imageInfoFile(10L, file));
        assertEquals(
                "image media resolved from ImgInfo ref target msgId=10 file=" + file.getName(),
                ImageDownloadResolutionLogLine.imageInfoRefTarget(10L, file));
    }

    @Test
    public void downloadedLogsKeepNetSceneSourceAndFileName() throws Exception {
        File file = Files.createTempFile("wxo-log-downloaded", ".jpg").toFile();
        writeBytes(file, 512);

        assertEquals(
                "image media resolved after NetSceneGetMsgImg msgId=10 file=" + file.getName(),
                ImageDownloadResolutionLogLine.downloadedFile(10L, file));
        assertEquals(
                "image media resolved ref target after NetSceneGetMsgImg msgId=10 file=" + file.getName(),
                ImageDownloadResolutionLogLine.downloadedRefTarget(10L, file));
        assertEquals(
                "image media candidate unsupported after NetSceneGetMsgImg msgId=10 file=" + file.getName(),
                ImageDownloadResolutionLogLine.downloadedUnsupported(10L, file));
    }

    @Test
    public void thumbnailLogsKeepSize() throws Exception {
        File file = Files.createTempFile("wxo-log-thumbnail", ".jpg").toFile();
        writeBytes(file, 512);

        assertEquals(
                "image media resolved thumbnail only after NetSceneGetMsgImg msgId=10 file="
                        + file.getName()
                        + " size=512",
                ImageDownloadResolutionLogLine.downloadedThumbnail(10L, file));
        assertEquals(
                "image media resolved thumbnail only msgId=10 file="
                        + file.getName()
                        + " size=512",
                ImageDownloadResolutionLogLine.imageInfoThumbnail(10L, file));
    }

    @Test
    public void notReadyLogsKeepRequestedOrRetrySource() {
        assertTrue(ImageDownloadResolutionLogLine.notReady(10L, 20L, true)
                .contains("image NetSceneGetMsgImg requested"));
        assertTrue(ImageDownloadResolutionLogLine.notReady(11L, 21L, false)
                .contains("image local retry"));
        assertTrue(ImageDownloadResolutionLogLine.notReady(10L, 20L, true)
                .contains("msgSvrId=20"));
    }

    private static void writeBytes(File file, int size) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(new byte[size]);
        }
    }
}
