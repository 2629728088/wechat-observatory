package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaResolverTest {
    @Test
    public void resolveImagePrefersRefTargetBeforeLowQualityThumbnail() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        File thumbnail = new File(bucket, "th_" + base);
        writeJpeg(thumbnail);

        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2Root(appRoot, "profile"), ".ref"), "d"), refId);
        writeJpeg(real);

        List<String> logs = new ArrayList<>();
        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                "th_" + base,
                0L,
                "",
                logs::add);

        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.PROFILE_IMAGE_REF_TARGET, result.status());
        assertFalse(result.needsEmojiDiagnostic());
        assertTrue(logsContain(logs, "image ref resolved"));
        assertTrue(WechatImageFiles.isLowQualityThumbnailFile(thumbnail));
    }

    @Test
    public void resolveImagePrefersThumbnailRefTargetBeforeHdThumbnail() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-thumb-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        File hdThumbnail = new File(bucket, "th_" + base + "hd");
        writeJpeg(hdThumbnail);

        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, "th_" + base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2Root(appRoot, "profile"), ".ref"), "d"), refId);
        writeJpeg(real);

        List<String> logs = new ArrayList<>();
        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                "th_" + base,
                0L,
                "",
                logs::add);

        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.PROFILE_IMAGE_REF_TARGET, result.status());
        assertFalse(result.needsEmojiDiagnostic());
        assertTrue(logsContain(logs, "image ref resolved"));
    }

    @Test
    public void resolveImageHdThumbnailHintUsesCanonicalRefTarget() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-hd-thumb-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        File hdThumbnail = new File(bucket, "th_" + base + "hd");
        writeJpeg(hdThumbnail);

        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, "th_" + base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2Root(appRoot, "profile"), ".ref"), "d"), refId);
        writeJpeg(real);

        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                "th_" + base + "hd",
                0L,
                "",
                null);

        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.PROFILE_IMAGE_REF_TARGET, result.status());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void resolveDirectImageReportsDirectImageStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-direct-status").toFile();
        File image = new File(appRoot, "direct-image.jpg");
        writeJpeg(image);

        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                image.getAbsolutePath(),
                0L,
                "",
                null);

        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_FILE, result.status());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void resolveDirectImageRefReportsRefTargetStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-direct-ref-status").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2Root(appRoot, "profile"), ".ref"), "d"), refId);
        writeJpeg(real);

        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                pointer.getAbsolutePath(),
                0L,
                "",
                null);

        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_REF_TARGET, result.status());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void resolveDirectImageRefReportsUnsupportedWhenTargetIsMissing() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-direct-bad-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));

        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                pointer.getAbsolutePath(),
                0L,
                "",
                null);

        assertNull(result.file());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_UNSUPPORTED, result.status());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void resolveImageRejectsDirectLowQualityThumbnail() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-thumb").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File thumbnail = new File(imageBucket(appRoot, "profile", base), "th_" + base);
        writeJpeg(thumbnail);

        List<String> logs = new ArrayList<>();
        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                thumbnail.getAbsolutePath(),
                0L,
                "",
                logs::add);

        assertNull(result.file());
        assertEquals(thumbnail.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.DIRECT_IMAGE_THUMBNAIL, result.status());
        assertFalse(result.needsEmojiDiagnostic());
        assertTrue(logsContain(logs, "skip thumbnail image candidate"));
    }

    @Test
    public void resolveImageReportsProfileThumbnailStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-profile-thumb").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File thumbnail = new File(imageBucket(appRoot, "profile", base), "th_" + base);
        writeJpeg(thumbnail);

        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                "th_" + base,
                0L,
                "",
                null);

        assertNull(result.file());
        assertEquals(thumbnail.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.PROFILE_IMAGE_THUMBNAIL, result.status());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void resolveImageReportsProfileUnsupportedRefStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-profile-bad-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));

        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                3,
                base + ".jpg",
                0L,
                "",
                null);

        assertNull(result.file());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.PROFILE_IMAGE_UNSUPPORTED, result.status());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void resolveVoiceWithEmptyHintUsesRecentVoiceFallback() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-voice-fallback").toFile();
        long createTimeSeconds = 1700000000L;
        File voice = new File(new File(new File(appRoot, "MicroMsg"), "profile/voice2"), "voice.amr");
        writeBytes(voice, "#!AMR\n".getBytes(StandardCharsets.US_ASCII));
        assertTrue(voice.setLastModified(createTimeSeconds * 1000L));

        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                MediaFiles.MESSAGE_TYPE_VOICE,
                "",
                createTimeSeconds,
                "",
                null);

        assertEquals(voice.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(MediaResolver.ResolutionStatus.VOICE_RECENT_FILE, result.status());
        assertFalse(result.needsEmojiDiagnostic());
    }

    @Test
    public void resolveImageInfoUsesDirectAbsoluteImagePath() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-direct").toFile();
        File image = new File(appRoot, "direct-image.jpg");
        writeJpeg(image);
        FakeImageInfo info = new FakeImageInfo();
        info.path = image.getAbsolutePath();

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertNotNull(result.file());
        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_FILE, result.status());
    }

    @Test
    public void resolveImageInfoReportsDirectImageStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-direct-status").toFile();
        File image = new File(appRoot, "direct-image.jpg");
        writeJpeg(image);
        FakeImageInfo info = new FakeImageInfo();
        info.a = 88L;
        info.path = image.getAbsolutePath();

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(88L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_FILE, result.status());
        assertTrue(result.candidateNames().isEmpty());
        assertTrue(result.fieldDebug().isEmpty());
    }

    @Test
    public void resolveImageInfoReportsDirectRefTargetStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-direct-ref-status").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2Root(appRoot, "profile"), ".ref"), "d"), refId);
        writeJpeg(real);
        FakeImageInfo info = new FakeImageInfo();
        info.a = 89L;
        info.path = pointer.getAbsolutePath();

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(89L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_REF_TARGET, result.status());
        assertTrue(result.candidateNames().isEmpty());
        assertTrue(result.fieldDebug().isEmpty());
    }

    @Test
    public void resolveImageInfoReportsDirectUnsupportedStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-direct-unsupported").toFile();
        File unsupported = new File(appRoot, "not-image.bin");
        Files.write(unsupported.toPath(), "not an image".getBytes(StandardCharsets.US_ASCII));
        FakeImageInfo info = new FakeImageInfo();
        info.a = 90L;
        info.path = unsupported.getAbsolutePath();

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertNull(result.file());
        assertEquals(unsupported.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(90L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_UNSUPPORTED, result.status());
        assertFalse(result.candidateNames().isEmpty());
    }

    @Test
    public void resolveImageInfoReportsProfileImageStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-profile-status").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File image = new File(imageBucket(appRoot, "profile", base), base + ".jpg");
        writeJpeg(image);
        FakeImageInfo info = new FakeImageInfo();
        info.a = 99L;
        info.path = base + ".jpg";

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertEquals(image.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(99L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_FILE, result.status());
        assertTrue(result.candidateNames().contains(base + ".jpg"));
        assertTrue(result.fieldDebug().isEmpty());
    }

    @Test
    public void resolveImageInfoReportsProfileRefTargetStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-profile-ref-status").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2Root(appRoot, "profile"), ".ref"), "d"), refId);
        writeJpeg(real);
        FakeImageInfo info = new FakeImageInfo();
        info.a = 101L;
        info.path = base + ".jpg";

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertEquals(real.getCanonicalFile(), result.file().getCanonicalFile());
        assertEquals(101L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_REF_TARGET, result.status());
        assertTrue(result.candidateNames().contains(base + ".jpg"));
        assertTrue(result.fieldDebug().isEmpty());
    }

    @Test
    public void resolveImageInfoReportsProfileThumbnailStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-profile-thumb").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File thumbnail = new File(imageBucket(appRoot, "profile", base), "th_" + base);
        writeJpeg(thumbnail);
        FakeImageInfo info = new FakeImageInfo();
        info.a = 102L;
        info.path = base + ".jpg";

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertNull(result.file());
        assertEquals(thumbnail.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(102L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_THUMBNAIL, result.status());
        assertTrue(result.candidateNames().contains(base + ".jpg"));
    }

    @Test
    public void resolveImageInfoReportsProfileUnsupportedRefStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-profile-bad-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File bucket = imageBucket(appRoot, "profile", base);
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        Files.write(pointer.toPath(), refId.getBytes(StandardCharsets.US_ASCII));
        FakeImageInfo info = new FakeImageInfo();
        info.a = 103L;
        info.path = base + ".jpg";

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertNull(result.file());
        assertEquals(pointer.getCanonicalFile(), result.source().getCanonicalFile());
        assertEquals(103L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_UNSUPPORTED, result.status());
        assertTrue(result.candidateNames().contains(base + ".jpg"));
    }

    @Test
    public void resolveImageInfoReturnsCandidateDiagnosticsWhenLookupMisses() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-miss").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        FakeImageInfo info = new FakeImageInfo();
        info.a = 77L;
        info.path = base + ".jpg";

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertNull(result.file());
        assertEquals(77L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.CANDIDATES_NOT_FOUND, result.status());
        assertTrue(result.candidateNames().contains(base + ".jpg"));
        assertTrue(result.candidateNames().contains("th_" + base + "hd"));
        assertTrue(logsContain(result.fieldDebug(), "path=" + base + ".jpg"));
    }

    @Test
    public void resolveImageInfoReportsNoCandidateNamesForXmlOnlyInfo() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-info-no-candidates").toFile();
        FakeImageInfo info = new FakeImageInfo();
        info.a = 55L;
        info.path = "<msg><img /></msg>";

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);

        assertNull(result.file());
        assertEquals(55L, result.localInfoId());
        assertEquals(MediaResolver.ImageInfoStatus.NO_CANDIDATE_NAMES, result.status());
        assertTrue(result.candidateNames().isEmpty());
        assertTrue(result.fieldDebug().isEmpty());
    }

    @Test
    public void resolveEmojiRequestsDiagnosticWhenLookupMisses() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-media-emoji").toFile();

        MediaResolver.Result result = MediaResolver.resolveMediaFile(
                appRoot,
                47,
                "",
                0L,
                "abcdef0123456789abcdef0123456789",
                null);

        assertEquals(null, result.file());
        assertEquals(MediaResolver.ResolutionStatus.EMOJI_DIAGNOSTIC_NEEDED, result.status());
        assertTrue(result.needsEmojiDiagnostic());
    }

    private static File imageBucket(File appRoot, String profile, String base) {
        File bucket = new File(new File(image2Root(appRoot, profile), base.substring(0, 2)), base.substring(2, 4));
        assertTrue(bucket.mkdirs() || bucket.isDirectory());
        return bucket;
    }

    private static File image2Root(File appRoot, String profile) {
        return new File(new File(new File(appRoot, "MicroMsg"), profile), "image2");
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

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        Files.write(file.toPath(), bytes);
    }

    private static boolean logsContain(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static final class FakeImageInfo {
        long a;
        String path;
    }
}
