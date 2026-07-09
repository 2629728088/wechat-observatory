package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MediaDirectFileResolverTest {
    @Test
    public void blankValueReturnsNull() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-direct-blank").toFile();

        assertNull(MediaDirectFileResolver.resolve(appRoot, " "));
    }

    @Test
    public void absoluteFileWinsBeforeRelativeCandidates() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-direct-absolute-root").toFile();
        File absolute = Files.createTempFile("wxo-direct-absolute", ".jpg").toFile();
        assertTrue(absolute.isFile());

        File result = MediaDirectFileResolver.resolve(appRoot, absolute.getAbsolutePath());

        assertEquals(absolute.getCanonicalFile(), result.getCanonicalFile());
    }

    @Test
    public void relativeFileResolvesAgainstAppRoot() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-direct-relative").toFile();
        File relative = new File(appRoot, "media/image.jpg");
        assertTrue(relative.getParentFile().mkdirs() || relative.getParentFile().isDirectory());
        assertTrue(relative.createNewFile());

        File result = MediaDirectFileResolver.resolve(appRoot, "media/image.jpg");

        assertEquals(relative.getCanonicalFile(), result.getCanonicalFile());
    }

    @Test
    public void normalizedHintCanResolveRelativeFile() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-direct-normalized").toFile();
        File normalized = new File(appRoot, "host/path/image.jpg");
        assertTrue(normalized.getParentFile().mkdirs() || normalized.getParentFile().isDirectory());
        assertTrue(normalized.createNewFile());

        File result = MediaDirectFileResolver.resolve(
                appRoot,
                "https://host/path/image.jpg?token=secret");

        assertEquals(normalized.getCanonicalFile(), result.getCanonicalFile());
    }
}
