package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageDeferredSelectionTest {
    @Test
    public void downloadedFileWinsBeforeGchatFile() throws Exception {
        File downloaded = Files.createTempFile("wxo-deferred-downloaded", ".jpg").toFile();
        File gchat = Files.createTempFile("wxo-deferred-gchat", ".jpg").toFile();
        writeJpeg(downloaded);
        writeJpeg(gchat);

        MediaFileSelector.Selection selection = new WechatImageDeferredSelection(
                (mediaHint, msgId, msgSvrId, createTime, talker) ->
                        downloaded(ImageDownloadResolution.Candidate.fromFile(downloaded)),
                content -> gchat,
                message -> {
                })
                .select(request(), baseResult(ImageDownloadResolution.Candidate.missing()));

        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE, selection.status());
        assertEquals(downloaded.getCanonicalFile(), selection.file().getCanonicalFile());
    }

    @Test
    public void gchatFileWinsOverDownloadedThumbnailStatus() throws Exception {
        File thumbnail = thumbnailFile();
        File gchat = Files.createTempFile("wxo-deferred-gchat-after-thumb", ".jpg").toFile();
        writeJpeg(thumbnail);
        writeJpeg(gchat);

        MediaFileSelector.Selection selection = new WechatImageDeferredSelection(
                (mediaHint, msgId, msgSvrId, createTime, talker) ->
                        downloaded(ImageDownloadResolution.Candidate.fromFile(thumbnail)),
                content -> gchat,
                message -> {
                })
                .select(request(), baseResult(ImageDownloadResolution.Candidate.missing()));

        assertEquals(MediaFileSelector.SelectionStatus.GCHAT_IMAGE_FILE, selection.status());
        assertEquals(gchat.getCanonicalFile(), selection.file().getCanonicalFile());
    }

    @Test
    public void downloadedUnsupportedStatusIsKeptWhenGchatMisses() throws Exception {
        File unsupported = Files.createTempFile("wxo-deferred-unsupported", ".ref").toFile();

        MediaFileSelector.Selection selection = new WechatImageDeferredSelection(
                (mediaHint, msgId, msgSvrId, createTime, talker) ->
                        downloaded(ImageDownloadResolution.Candidate.unsupported(unsupported)),
                content -> null,
                message -> {
                })
                .select(request(), baseResult(ImageDownloadResolution.Candidate.missing()));

        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_UNSUPPORTED, selection.status());
        assertNull(selection.file());
    }

    @Test
    public void unsupportedFallbackIsReturnedAfterDownloadAndGchatMiss() throws Exception {
        File unsupported = Files.createTempFile("wxo-deferred-fallback", ".ref").toFile();

        MediaFileSelector.Selection selection = new WechatImageDeferredSelection(
                (mediaHint, msgId, msgSvrId, createTime, talker) -> notReady(),
                content -> null,
                message -> {
                })
                .select(request(), baseResult(ImageDownloadResolution.Candidate.unsupported(unsupported)));

        assertEquals(MediaFileSelector.SelectionStatus.BASE_UNSUPPORTED, selection.status());
        assertNull(selection.file());
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

    private static WechatImageBaseSelection.Result baseResult(ImageDownloadResolution.Candidate candidate) {
        return new WechatImageBaseSelection(message -> {
        }).select(request(), candidate);
    }

    private static ImageDownloadResolution notReady() {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                false,
                ImageDownloadResolution.Candidate.missing(),
                ImageDownloadResolution.Candidate.missing());
    }

    private static ImageDownloadResolution downloaded(
            ImageDownloadResolution.Candidate downloadedFallbackCandidate) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                true,
                ImageDownloadResolution.Candidate.missing(),
                downloadedFallbackCandidate);
    }

    private static File thumbnailFile() throws Exception {
        File image2 = new File(Files.createTempDirectory("wxo-deferred-thumb").toFile(), "image2");
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
