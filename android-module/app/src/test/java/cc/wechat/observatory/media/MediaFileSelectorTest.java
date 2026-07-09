package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaFileSelectorTest {
    @Test
    public void imageThumbnailFromBaseResolverFallsBackToDownloadedFile() throws Exception {
        File thumbnail = image2File("th_abcd1234");
        File full = Files.createTempFile("wxo-full", ".jpg").toFile();
        writeJpeg(thumbnail);
        writeJpeg(full);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> candidate(thumbnail),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> downloaded(full),
                emojiMd5 -> null,
                content -> null,
                logs::add)
                .selectDetailed(request(3));

        assertEquals(full.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE, selected.status());
        assertTrue(contains(logs, "thumbnail only"));
    }

    @Test
    public void imageThumbnailAfterFallbackIsRejected() throws Exception {
        File thumbnail = image2File("th_abcd1234");
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> downloaded(thumbnail),
                emojiMd5 -> null,
                content -> null,
                logs::add)
                .selectDetailed(request(3));

        assertNull(selected.file());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_THUMBNAIL, selected.status());
        assertTrue(contains(logs, "skip thumbnail image upload"));
    }

    @Test
    public void imageThumbnailWithoutFallbackDoesNotReturnBaseThumbnail() throws Exception {
        File thumbnail = image2File("th_abcd1234");
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> candidate(thumbnail),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> notReady(),
                emojiMd5 -> null,
                content -> null,
                logs::add)
                .selectDetailed(request(3));

        assertNull(selected.file());
        assertEquals(MediaFileSelector.SelectionStatus.NOT_FOUND, selected.status());
        assertTrue(contains(logs, "thumbnail only"));
        assertTrue(contains(logs, "media file not found type=3"));
    }

    @Test
    public void imageInfoThumbnailKeepsStructuredStatus() throws Exception {
        File thumbnail = image2File("th_abcd1234");
        writeJpeg(thumbnail);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> imageInfoThumbnail(thumbnail),
                emojiMd5 -> null,
                content -> null,
                logs::add)
                .selectDetailed(request(3));

        assertNull(selected.file());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_INFO_THUMBNAIL, selected.status());
        assertTrue(contains(logs, "status=IMAGE_INFO_THUMBNAIL"));
    }

    @Test
    public void imageUnsupportedBaseCandidateFallsBackToDownloadedFile() throws Exception {
        File unsupported = Files.createTempFile("wxo-base-unsupported", ".ref").toFile();
        File full = Files.createTempFile("wxo-full-after-unsupported", ".jpg").toFile();
        writeJpeg(full);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> ImageDownloadResolution.Candidate.unsupported(unsupported),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> downloaded(full),
                emojiMd5 -> null,
                content -> null,
                logs::add)
                .selectDetailed(request(3));

        assertEquals(full.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_FILE, selected.status());
        assertTrue(contains(logs, "unsupported candidate"));
    }

    @Test
    public void imageInfoUnsupportedKeepsStructuredStatus() throws Exception {
        File unsupported = Files.createTempFile("wxo-info-unsupported", ".ref").toFile();
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> imageInfoUnsupported(unsupported),
                emojiMd5 -> null,
                content -> null,
                logs::add)
                .selectDetailed(request(3));

        assertNull(selected.file());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_INFO_UNSUPPORTED, selected.status());
        assertTrue(contains(logs, "status=IMAGE_INFO_UNSUPPORTED"));
    }

    @Test
    public void imageDownloadedUnsupportedKeepsStructuredStatus() throws Exception {
        File unsupported = Files.createTempFile("wxo-downloaded-unsupported", ".ref").toFile();
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> downloadedUnsupported(unsupported),
                emojiMd5 -> null,
                content -> null,
                logs::add)
                .selectDetailed(request(3));

        assertNull(selected.file());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_UNSUPPORTED, selected.status());
        assertTrue(contains(logs, "status=DOWNLOADED_UNSUPPORTED"));
    }

    @Test
    public void imageRefTargetFromBaseResolverKeepsStructuredStatus() throws Exception {
        File image = Files.createTempFile("wxo-base-ref", ".jpg").toFile();
        writeJpeg(image);

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> ImageDownloadResolution.Candidate.refTarget(image),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> notReady(),
                emojiMd5 -> null,
                content -> null,
                message -> {
                })
                .selectDetailed(request(3));

        assertEquals(image.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.BASE_REF_TARGET, selected.status());
    }

    @Test
    public void imageRefTargetAfterDownloadKeepsStructuredStatus() throws Exception {
        File image = Files.createTempFile("wxo-download-ref", ".jpg").toFile();
        writeJpeg(image);

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> downloadedRef(image),
                emojiMd5 -> null,
                content -> null,
                message -> {
                })
                .selectDetailed(request(3));

        assertEquals(image.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.IMAGE_DOWNLOAD_REF_TARGET, selected.status());
    }

    @Test
    public void emojiUsesEmojiResolverWhenBaseLookupMisses() throws Exception {
        File emoji = Files.createTempFile("wxo-emoji", ".gif").toFile();
        writeGif(emoji);

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> notReady(),
                emojiMd5 -> emoji,
                content -> null,
                message -> {
                })
                .selectDetailed(request(47));

        assertEquals(emoji.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.EMOJI_FILE, selected.status());
    }

    @Test
    public void imageUsesGchatResolverWhenDownloadMisses() throws Exception {
        File image = Files.createTempFile("wxo-gchat", ".jpg").toFile();
        writeJpeg(image);

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> notReady(),
                emojiMd5 -> null,
                content -> image,
                message -> {
                })
                .selectDetailed(request(3));

        assertEquals(image.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.GCHAT_IMAGE_FILE, selected.status());
    }

    @Test
    public void imageUsesGchatResolverWhenDownloadOnlyFindsThumbnail() throws Exception {
        File thumbnail = image2File("th_abcd1234");
        File image = Files.createTempFile("wxo-gchat-after-download-thumb", ".jpg").toFile();
        writeJpeg(thumbnail);
        writeJpeg(image);
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> downloaded(thumbnail),
                emojiMd5 -> null,
                content -> image,
                logs::add)
                .selectDetailed(request(3));

        assertEquals(image.getCanonicalFile(), selected.file().getCanonicalFile());
        assertEquals(MediaFileSelector.SelectionStatus.GCHAT_IMAGE_FILE, selected.status());
        assertTrue(contains(logs, "skip thumbnail image upload"));
    }

    @Test
    public void unknownTypeDoesNotCallResolvers() {
        AtomicInteger calls = new AtomicInteger();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> {
                    calls.incrementAndGet();
                    return missingCandidate();
                },
                (mediaHint, msgId, msgSvrId, createTime, talker) -> notReady(),
                emojiMd5 -> null,
                content -> null,
                message -> {
                })
                .selectDetailed(request(999));

        assertNull(selected.file());
        assertEquals(MediaFileSelector.SelectionStatus.UNSUPPORTED_TYPE, selected.status());
        assertEquals(0, calls.get());
    }

    @Test
    public void missingKnownMediaLogsMissWithoutHintValue() {
        List<String> logs = new ArrayList<>();

        MediaFileSelector.Selection selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> missingCandidate(),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> notReady(),
                emojiMd5 -> null,
                content -> null,
                logs::add)
                .selectDetailed(request(34));

        assertNull(selected.file());
        assertEquals(MediaFileSelector.SelectionStatus.NOT_FOUND, selected.status());
        assertTrue(contains(logs, "media file not found type=34"));
        assertFalse(contains(logs, "media.jpg"));
    }

    @Test
    public void selectKeepsLegacyFileReturn() throws Exception {
        File media = Files.createTempFile("wxo-base", ".jpg").toFile();
        writeJpeg(media);

        File selected = new MediaFileSelector(
                (type, mediaHint, createTime, emojiMd5) -> candidate(media),
                (mediaHint, msgId, msgSvrId, createTime, talker) -> notReady(),
                emojiMd5 -> null,
                content -> null,
                message -> {
                })
                .select(request(3));

        assertEquals(media.getCanonicalFile(), selected.getCanonicalFile());
    }

    @Test
    public void selectionHasFileRequiresExistingFile() throws Exception {
        File media = Files.createTempFile("wxo-selection-has-file", ".jpg").toFile();
        File missing = new File(media.getParentFile(), "missing.jpg");

        assertTrue(MediaFileSelector.Selection.of(
                MediaFileSelector.SelectionStatus.BASE_FILE,
                media).hasFile());
        assertFalse(MediaFileSelector.Selection.of(
                MediaFileSelector.SelectionStatus.BASE_FILE,
                missing).hasFile());
        assertFalse(MediaFileSelector.Selection.of(
                MediaFileSelector.SelectionStatus.NOT_FOUND,
                null).hasFile());
    }

    private static MediaFileSelector.Request request(int type) {
        return new MediaFileSelector.Request(
                type,
                "media.jpg",
                Long.valueOf(12L),
                Long.valueOf(34L),
                123456L,
                "talker",
                "<content/>",
                "abcdefabcdefabcdefabcdefabcdefab",
                12L);
    }

    private static ImageDownloadResolution.Candidate candidate(File file) {
        return ImageDownloadResolution.Candidate.fromFile(file);
    }

    private static ImageDownloadResolution.Candidate missingCandidate() {
        return ImageDownloadResolution.Candidate.missing();
    }

    private static ImageDownloadResolution downloaded(File file) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                true,
                missingCandidate(),
                candidate(file));
    }

    private static ImageDownloadResolution imageInfoThumbnail(File file) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                false,
                candidate(file),
                missingCandidate());
    }

    private static ImageDownloadResolution imageInfoUnsupported(File file) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                false,
                ImageDownloadResolution.Candidate.unsupported(file),
                missingCandidate());
    }

    private static ImageDownloadResolution downloadedUnsupported(File file) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                true,
                missingCandidate(),
                ImageDownloadResolution.Candidate.unsupported(file));
    }

    private static ImageDownloadResolution downloadedRef(File file) {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                true,
                missingCandidate(),
                ImageDownloadResolution.Candidate.refTarget(file));
    }

    private static ImageDownloadResolution notReady() {
        return ImageDownloadResolution.evaluateCandidates(
                12L,
                34L,
                false,
                missingCandidate(),
                missingCandidate());
    }

    private static File image2File(String name) throws Exception {
        File root = Files.createTempDirectory("wxo-selector").toFile();
        File image2 = new File(root, "image2");
        return new File(image2, name);
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

    private static void writeGif(File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61});
        }
    }

    private static boolean contains(List<String> values, String needle) {
        for (String value : values) {
            if (value != null && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
