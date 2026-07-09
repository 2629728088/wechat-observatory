package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageReferenceResolverTest {
    private static final String REF_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void resolvesWechatImage2RefPointerToRefTarget() throws Exception {
        File image2 = image2Root("wxo-ref-ok");
        File pointer = new File(new File(new File(image2, "ab"), "cd"), "abcd" + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);
        File target = new File(new File(new File(image2, ".ref"), "d"), REF_ID);
        writeJpeg(target);
        List<String> logs = new ArrayList<>();

        WechatImageReferenceResolver.Result result = WechatImageReferenceResolver.resolve(pointer, logs::add);

        assertEquals(WechatImageReferenceResolver.Status.TARGET_FOUND, result.status());
        assertEquals(pointer.getCanonicalFile(), result.pointer().getCanonicalFile());
        assertEquals(target.getCanonicalFile(), result.target().getCanonicalFile());
        assertEquals(REF_ID, result.referenceId());
        assertTrue(result.found());
        assertTrue(contains(logs, "image ref resolved"));
    }

    @Test
    public void reportsTargetMissingWhenPointerIdIsValidButRefFileIsAbsent() throws Exception {
        File image2 = image2Root("wxo-ref-missing");
        File pointer = new File(image2, "abcd" + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, REF_ID);

        WechatImageReferenceResolver.Result result = WechatImageReferenceResolver.resolve(pointer, null);

        assertEquals(WechatImageReferenceResolver.Status.TARGET_MISSING, result.status());
        assertEquals(REF_ID, result.referenceId());
        assertNull(result.target());
    }

    @Test
    public void rejectsInvalidReferenceIdWithoutLookingForTarget() throws Exception {
        File image2 = image2Root("wxo-ref-invalid");
        File pointer = new File(image2, "abcd" + WechatImageFiles.REF_SUFFIX);
        writeText(pointer, "not-a-uuid");

        WechatImageReferenceResolver.Result result = WechatImageReferenceResolver.resolve(pointer, null);

        assertEquals(WechatImageReferenceResolver.Status.INVALID_REFERENCE_ID, result.status());
        assertEquals("not-a-uuid", result.referenceId());
        assertNull(result.target());
    }

    private static void writeText(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        Files.write(file.toPath(), text.getBytes(StandardCharsets.US_ASCII));
    }

    private static File image2Root(String prefix) throws Exception {
        File root = Files.createTempDirectory(prefix).toFile();
        File image2 = new File(root, "image2");
        assertTrue(image2.mkdirs() || image2.isDirectory());
        return image2;
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

    private static boolean contains(List<String> values, String needle) {
        for (String value : values) {
            if (value != null && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
