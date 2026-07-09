package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaResolverRuntimeTest {
    @Test
    public void resolveDelegatesToMediaResolverWithEnvironmentAppRoot() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-resolver").toFile();
        File voice = new File(appRoot, "voice.amr");
        writeBytes(voice, "#!AMR\n".getBytes("US-ASCII"));

        File resolved = new MediaResolverRuntime(new TestEnvironment(appRoot))
                .resolve(MediaFiles.MESSAGE_TYPE_VOICE, voice.getAbsolutePath(), 0L, "");

        assertEquals(voice.getCanonicalFile(), resolved.getCanonicalFile());
    }

    @Test
    public void resolveTriggersEmojiDiagnosticWhenLookupNeedsRuntimeHelp() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-emoji").toFile();
        String md5 = "abcdef0123456789abcdef0123456789";
        TestEnvironment environment = new TestEnvironment(appRoot);

        File resolved = new MediaResolverRuntime(environment, new MediaResolverRuntime.EmojiInfoProvider() {
            @Override
            public Object load(String emojiMd5) {
                environment.loadedEmojiMd5.set(emojiMd5);
                return environment.emojiInfo;
            }
        })
                .resolve(47, "", 0L, md5);

        assertNull(resolved);
        assertEquals(md5, environment.diagnosticMd5.get());
    }

    @Test
    public void resolveUsesEmojiInfoBeforeDiagnosticWhenFallbackSearchMisses() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-emoji-info").toFile();
        String md5 = "abcdef0123456789abcdef0123456789";
        File emojiDir = new File(new File(appRoot, "emoji-info-only"), "store");
        File emoji = new File(emojiDir, md5 + ".gif");
        writeBytes(emoji, "GIF89a".getBytes(StandardCharsets.US_ASCII));
        FakeEmojiInfo info = new FakeEmojiInfo();
        info.C2 = emojiDir.getAbsolutePath();
        TestEnvironment environment = new TestEnvironment(appRoot);
        environment.emojiInfo = info;

        File resolved = new MediaResolverRuntime(environment, new MediaResolverRuntime.EmojiInfoProvider() {
            @Override
            public Object load(String emojiMd5) {
                environment.loadedEmojiMd5.set(emojiMd5);
                return environment.emojiInfo;
            }
        })
                .resolve(47, "", 0L, md5);

        assertEquals(emoji.getCanonicalFile(), resolved.getCanonicalFile());
        assertEquals(md5, environment.loadedEmojiMd5.get());
        assertNull(environment.diagnosticMd5.get());
    }

    @Test
    public void resolveReturnsNullWithoutEnvironment() {
        File resolved = new MediaResolverRuntime(null)
                .resolve(MediaFiles.MESSAGE_TYPE_VOICE, "voice.amr", 0L, "");

        assertNull(resolved);
    }

    @Test
    public void resolveImageInfoCandidateUsesHintResolverThenImageResolver() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-image-info").toFile();
        File image = new File(appRoot, "downloaded-image.jpg");
        writeJpeg(image);
        TestEnvironment environment = new TestEnvironment(appRoot);
        environment.resolvedHint = image.getAbsolutePath();

        ImageDownloadResolution.Candidate candidate = new MediaResolverRuntime(environment)
                .resolveImageInfoCandidate(11L, 22L);

        assertTrue(candidate.isImageFile());
        assertEquals(image.getCanonicalFile(), candidate.file().getCanonicalFile());
        assertEquals(1, environment.mediaHintCalls);
        assertEquals(MediaFiles.MESSAGE_TYPE_IMAGE, environment.lastHintType);
        assertEquals(Long.valueOf(11L), environment.lastHintMsgId);
        assertEquals(Long.valueOf(22L), environment.lastHintMsgSvrId);
        assertEquals("", environment.lastHintMediaHint);
    }

    @Test
    public void resolveImageInfoCandidateReturnsMissingWhenHintIsMissing() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-image-info-missing").toFile();
        TestEnvironment environment = new TestEnvironment(appRoot);

        ImageDownloadResolution.Candidate candidate = new MediaResolverRuntime(environment)
                .resolveImageInfoCandidate(11L, 22L);

        assertTrue(candidate.isMissing());
        assertNull(candidate.file());
        assertEquals(1, environment.mediaHintCalls);
    }

    @Test
    public void resolveImageInfoCandidateReturnsMissingWithoutEnvironment() {
        ImageDownloadResolution.Candidate candidate = new MediaResolverRuntime(null)
                .resolveImageInfoCandidate(11L, 22L);

        assertTrue(candidate.isMissing());
        assertNull(candidate.file());
    }

    @Test
    public void resolveImageInfoCandidatePreservesRefTargetStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-image-info-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File image2 = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
        File bucket = new File(new File(image2, base.substring(0, 2)), base.substring(2, 4));
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        writeBytes(pointer, refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2, ".ref"), "d"), refId);
        writeJpeg(real);
        TestEnvironment environment = new TestEnvironment(appRoot);
        environment.resolvedHint = base + ".jpg";

        ImageDownloadResolution.Candidate candidate = new MediaResolverRuntime(environment)
                .resolveImageInfoCandidate(11L, 22L);

        assertTrue(candidate.isReferenceTarget());
        assertEquals(real.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void imageDownloadCandidateMapsImageInfoFileStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-image-info-candidate-file").toFile();
        File image = new File(appRoot, "direct-image.jpg");
        writeJpeg(image);
        FakeImageInfo info = new FakeImageInfo();
        info.path = image.getAbsolutePath();

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);
        ImageDownloadResolution.Candidate candidate = ImageDownloadCandidateMapper.fromImageInfoResult(result);

        assertTrue(candidate.isImageFile());
        assertEquals(image.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void imageDownloadCandidateMapsImageInfoRefTargetStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-image-info-candidate-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File image2 = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
        File bucket = new File(new File(image2, base.substring(0, 2)), base.substring(2, 4));
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        writeBytes(pointer, refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2, ".ref"), "d"), refId);
        writeJpeg(real);
        FakeImageInfo info = new FakeImageInfo();
        info.path = pointer.getAbsolutePath();

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);
        ImageDownloadResolution.Candidate candidate = ImageDownloadCandidateMapper.fromImageInfoResult(result);

        assertTrue(candidate.isReferenceTarget());
        assertEquals(real.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void imageDownloadCandidateMapsImageInfoThumbnailStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-image-info-candidate-thumbnail").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File image2 = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
        File thumbnail = new File(new File(new File(image2, base.substring(0, 2)), base.substring(2, 4)), "th_" + base);
        writeJpeg(thumbnail);
        FakeImageInfo info = new FakeImageInfo();
        info.path = base + ".jpg";

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);
        ImageDownloadResolution.Candidate candidate = ImageDownloadCandidateMapper.fromImageInfoResult(result);

        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_THUMBNAIL, result.status());
        assertTrue(candidate.isLowQualityThumbnail());
        assertEquals(thumbnail.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void imageDownloadCandidateMapsImageInfoUnsupportedStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-image-info-candidate-unsupported").toFile();
        File unsupported = new File(appRoot, "not-image.bin");
        writeBytes(unsupported, "not an image".getBytes(StandardCharsets.US_ASCII));
        FakeImageInfo info = new FakeImageInfo();
        info.path = unsupported.getAbsolutePath();

        MediaResolver.ImageInfoResult result = MediaResolver.resolveImageInfo(appRoot, info, null);
        ImageDownloadResolution.Candidate candidate = ImageDownloadCandidateMapper.fromImageInfoResult(result);

        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_UNSUPPORTED, result.status());
        assertTrue(candidate.isUnsupported());
        assertEquals(unsupported.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void imageDownloadCandidateMapsMissingImageInfoStatus() {
        ImageDownloadResolution.Candidate candidate = ImageDownloadCandidateMapper.fromImageInfoResult(
                MediaResolver.ImageInfoResult.missing());

        assertTrue(candidate.isMissing());
        assertNull(candidate.file());
    }

    @Test
    public void resolveCandidatePreservesThumbnailStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-candidate-thumbnail").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File image2 = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
        File thumbnail = new File(new File(new File(image2, base.substring(0, 2)), base.substring(2, 4)), "th_" + base);
        writeJpeg(thumbnail);

        ImageDownloadResolution.Candidate candidate = new MediaResolverRuntime(new TestEnvironment(appRoot))
                .resolveCandidate(MediaFiles.MESSAGE_TYPE_IMAGE, "th_" + base, 0L, "");

        assertTrue(candidate.isLowQualityThumbnail());
        assertEquals(thumbnail.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void resolveCandidatePreservesUnsupportedRefStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-candidate-unsupported").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File image2 = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
        File bucket = new File(new File(image2, base.substring(0, 2)), base.substring(2, 4));
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        writeBytes(pointer, refId.getBytes(StandardCharsets.US_ASCII));

        ImageDownloadResolution.Candidate candidate = new MediaResolverRuntime(new TestEnvironment(appRoot))
                .resolveCandidate(MediaFiles.MESSAGE_TYPE_IMAGE, base + ".jpg", 0L, "");

        assertTrue(candidate.isUnsupported());
        assertEquals(pointer.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void resolveCandidatePreservesRefTargetStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-runtime-candidate-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File image2 = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
        File bucket = new File(new File(image2, base.substring(0, 2)), base.substring(2, 4));
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        writeBytes(pointer, refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2, ".ref"), "d"), refId);
        writeJpeg(real);

        ImageDownloadResolution.Candidate candidate = new MediaResolverRuntime(new TestEnvironment(appRoot))
                .resolveCandidate(MediaFiles.MESSAGE_TYPE_IMAGE, base + ".jpg", 0L, "");

        assertTrue(candidate.isReferenceTarget());
        assertEquals(real.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
    }

    private static void writeJpeg(File file) throws Exception {
        byte[] bytes = new byte[512];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xd8;
        bytes[2] = (byte) 0xff;
        writeBytes(file, bytes);
    }

    private static final class TestEnvironment implements MediaResolverRuntime.Environment {
        private final File appRoot;
        final AtomicReference<String> diagnosticMd5 = new AtomicReference<>();
        final AtomicReference<String> loadedEmojiMd5 = new AtomicReference<>();
        String resolvedHint = "";
        Object emojiInfo;
        int mediaHintCalls;
        int lastHintType;
        Long lastHintMsgId;
        Long lastHintMsgSvrId;
        String lastHintMediaHint;

        TestEnvironment(File appRoot) {
            this.appRoot = appRoot;
        }

        @Override
        public File appRoot() {
            return appRoot;
        }

        @Override
        public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
            mediaHintCalls++;
            lastHintType = type;
            lastHintMsgId = msgId;
            lastHintMsgSvrId = msgSvrId;
            lastHintMediaHint = mediaHint;
            return resolvedHint;
        }

        @Override
        public void log(String message) {
        }

        @Override
        public void onEmojiDiagnosticNeeded(String emojiMd5) {
            diagnosticMd5.set(emojiMd5);
        }
    }

    private static final class FakeImageInfo {
        String path;
    }

    private static final class FakeEmojiInfo {
        String C2;
    }
}
