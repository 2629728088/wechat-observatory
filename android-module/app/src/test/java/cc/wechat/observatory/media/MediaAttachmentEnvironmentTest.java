package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaAttachmentEnvironmentTest {
    @Test
    public void resolveMediaFileCandidateDelegatesToResolverRuntime() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-attach-env").toFile();
        File image = new File(appRoot, "image.jpg");
        writeJpeg(image);

        MediaAttachmentEnvironment environment = new MediaAttachmentEnvironment(
                new MediaResolverRuntime(new TestResolverEnvironment(appRoot)),
                null,
                null,
                null);

        ImageDownloadResolution.Candidate candidate =
                environment.resolveMediaFileCandidate(
                        MediaFiles.MESSAGE_TYPE_IMAGE,
                        image.getAbsolutePath(),
                        0L,
                        "");

        assertTrue(candidate.isImageFile());
        assertEquals(image.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void resolveImageInfoCandidatePreservesRefTargetStatus() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-attach-env-ref").toFile();
        String base = "abcd1234ef567890abcd1234ef567890";
        File image2 = new File(new File(new File(appRoot, "MicroMsg"), "profile"), "image2");
        File bucket = new File(new File(image2, base.substring(0, 2)), base.substring(2, 4));
        String refId = "123e4567-e89b-12d3-a456-426614174000";
        File pointer = new File(bucket, base + WechatImageFiles.REF_SUFFIX);
        writeBytes(pointer, refId.getBytes(StandardCharsets.US_ASCII));
        File real = new File(new File(new File(image2, ".ref"), "d"), refId);
        writeJpeg(real);
        TestResolverEnvironment resolverEnvironment = new TestResolverEnvironment(appRoot);
        resolverEnvironment.resolvedHint = base + ".jpg";

        MediaAttachmentEnvironment environment = new MediaAttachmentEnvironment(
                new MediaResolverRuntime(resolverEnvironment),
                null,
                null,
                null);

        ImageDownloadResolution.Candidate candidate = environment.resolveImageInfoCandidate(11L, 22L, "talker");

        assertTrue(candidate.isReferenceTarget());
        assertEquals(real.getCanonicalFile(), candidate.file().getCanonicalFile());
    }

    @Test
    public void delegatesDownloadEncodeAndLog() {
        AtomicReference<String> requestArgs = new AtomicReference<>();
        List<String> logs = new ArrayList<>();
        MediaAttachmentEnvironment environment = new MediaAttachmentEnvironment(
                null,
                (localId, serverId, talker) -> {
                    requestArgs.set(localId + ":" + serverId + ":" + talker);
                    return true;
                },
                bytes -> "encoded-" + bytes.length,
                logs::add);

        assertTrue(environment.requestImageDownload(1L, 2L, "talker"));
        assertEquals("1:2:talker", requestArgs.get());
        assertEquals("encoded-3", environment.encode(new byte[]{1, 2, 3}));
        environment.log("message");
        assertEquals("message", logs.get(0));
    }

    @Test
    public void handlesMissingCollaborators() {
        MediaAttachmentEnvironment environment = new MediaAttachmentEnvironment(null, null, null, null);

        assertTrue(environment.resolveMediaFileCandidate(
                MediaFiles.MESSAGE_TYPE_IMAGE,
                "image.jpg",
                0L,
                "").isMissing());
        assertTrue(environment.resolveImageInfoCandidate(1L, 2L, "talker").isMissing());
        assertFalse(environment.requestImageDownload(1L, 2L, "talker"));
        assertEquals("", environment.encode(new byte[]{1}));
        environment.log("ignored");
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

    private static final class TestResolverEnvironment implements MediaResolverRuntime.Environment {
        private final File appRoot;
        String resolvedHint = "";

        TestResolverEnvironment(File appRoot) {
            this.appRoot = appRoot;
        }

        @Override
        public File appRoot() {
            return appRoot;
        }

        @Override
        public String resolveMediaHint(int type, Long msgId, Long msgSvrId, String mediaHint) {
            return resolvedHint;
        }

        @Override
        public void log(String message) {
        }

        @Override
        public void onEmojiDiagnosticNeeded(String emojiMd5) {
        }
    }
}
