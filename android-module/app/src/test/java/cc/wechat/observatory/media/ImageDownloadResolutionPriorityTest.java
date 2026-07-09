package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ImageDownloadResolutionPriorityTest {
    @Test
    public void imageInfoFileWinsOverDownloadedFile() throws Exception {
        File imageInfo = Files.createTempFile("wxo-priority-info", ".jpg").toFile();
        File downloaded = Files.createTempFile("wxo-priority-downloaded", ".jpg").toFile();
        writeJpeg(imageInfo);
        writeJpeg(downloaded);

        ImageDownloadResolution resolution = ImageDownloadResolutionPriority.resolve(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.fromFile(imageInfo),
                ImageDownloadResolution.Candidate.fromFile(downloaded));

        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_FILE, resolution.status());
        assertEquals(imageInfo.getCanonicalFile(), resolution.file().getCanonicalFile());
    }

    @Test
    public void imageInfoRefTargetWinsOverDownloadedFile() throws Exception {
        File refTarget = Files.createTempFile("wxo-priority-info-ref", ".jpg").toFile();
        File downloaded = Files.createTempFile("wxo-priority-downloaded-ref-loser", ".jpg").toFile();
        writeJpeg(refTarget);
        writeJpeg(downloaded);

        ImageDownloadResolution resolution = ImageDownloadResolutionPriority.resolve(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.refTarget(refTarget),
                ImageDownloadResolution.Candidate.fromFile(downloaded));

        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_REF_TARGET, resolution.status());
        assertEquals(refTarget.getCanonicalFile(), resolution.file().getCanonicalFile());
    }

    @Test
    public void downloadedFileWinsOverDeferredImageInfoThumbnail() throws Exception {
        File thumbnail = thumbnailFile();
        File downloaded = Files.createTempFile("wxo-priority-real", ".jpg").toFile();
        writeJpeg(thumbnail);
        writeJpeg(downloaded);

        ImageDownloadResolution resolution = ImageDownloadResolutionPriority.resolve(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.fromFile(thumbnail),
                ImageDownloadResolution.Candidate.fromFile(downloaded));

        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_FILE, resolution.status());
        assertEquals(downloaded.getCanonicalFile(), resolution.file().getCanonicalFile());
    }

    @Test
    public void downloadedRefTargetWinsOverDeferredImageInfoUnsupported() throws Exception {
        File unsupported = Files.createTempFile("wxo-priority-info-unsupported", ".ref").toFile();
        File refTarget = Files.createTempFile("wxo-priority-downloaded-ref", ".jpg").toFile();
        writeJpeg(unsupported);
        writeJpeg(refTarget);

        ImageDownloadResolution resolution = ImageDownloadResolutionPriority.resolve(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.unsupported(unsupported),
                ImageDownloadResolution.Candidate.refTarget(refTarget));

        assertEquals(ImageDownloadResolution.Status.DOWNLOADED_REF_TARGET, resolution.status());
        assertEquals(refTarget.getCanonicalFile(), resolution.file().getCanonicalFile());
    }

    @Test
    public void deferredImageInfoThumbnailIsTerminalWhenNoDownloadedFileExists() throws Exception {
        File thumbnail = thumbnailFile();
        writeJpeg(thumbnail);

        ImageDownloadResolution resolution = ImageDownloadResolutionPriority.resolve(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.fromFile(thumbnail),
                ImageDownloadResolution.Candidate.missing());

        assertEquals(ImageDownloadResolution.Status.IMAGE_INFO_THUMBNAIL, resolution.status());
        assertNull(resolution.file());
        assertTrue(resolution.logMessage().contains("thumbnail only msgId=10"));
    }

    @Test
    public void notReadyMessageKeepsDownloadSource() {
        ImageDownloadResolution requested = ImageDownloadResolutionPriority.resolve(
                10L,
                20L,
                true,
                ImageDownloadResolution.Candidate.missing(),
                ImageDownloadResolution.Candidate.missing());
        ImageDownloadResolution retry = ImageDownloadResolutionPriority.resolve(
                11L,
                21L,
                false,
                ImageDownloadResolution.Candidate.missing(),
                ImageDownloadResolution.Candidate.missing());

        assertEquals(ImageDownloadResolution.Status.NOT_READY, requested.status());
        assertTrue(requested.logMessage().contains("NetSceneGetMsgImg requested"));
        assertTrue(requested.logMessage().contains("msgSvrId=20"));
        assertEquals(ImageDownloadResolution.Status.NOT_READY, retry.status());
        assertTrue(retry.logMessage().contains("image local retry"));
        assertTrue(retry.logMessage().contains("msgSvrId=21"));
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-priority-thumb").toFile(), "image2");
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
}
