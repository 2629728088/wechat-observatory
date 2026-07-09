package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class NamedMediaFileFinderTest {
    @Test
    public void findInProfileRootsSearchesConfiguredRoots() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsg").toFile();
        File expected = new File(new File(new File(microMsgRoot, "profile-a"), "video"), "clip.mp4");
        writeBytes(expected);

        File found = NamedMediaFileFinder.findInProfileRoots(
                microMsgRoot,
                new String[]{"attachment", "video"},
                Collections.singletonList("clip.mp4"));

        assertEquals(expected.getCanonicalFile(), found.getCanonicalFile());
    }

    @Test
    public void findInProfileRootsUsesStableProfileNameOrder() throws Exception {
        File microMsgRoot = Files.createTempDirectory("MicroMsgOrder").toFile();
        File laterByName = new File(new File(new File(microMsgRoot, "profile-b"), "video"), "clip.mp4");
        File earlierByName = new File(new File(new File(microMsgRoot, "profile-a"), "video"), "clip.mp4");
        writeBytes(laterByName);
        writeBytes(earlierByName);

        File found = NamedMediaFileFinder.findInProfileRoots(
                microMsgRoot,
                new String[]{"video"},
                Collections.singletonList("clip.mp4"));

        assertEquals(earlierByName.getCanonicalFile(), found.getCanonicalFile());
    }

    @Test
    public void findInRootSearchesNestedDirectoriesWithinDepth() throws Exception {
        File root = Files.createTempDirectory("wxo-named-root").toFile();
        File expected = new File(new File(new File(root, "a"), "b"), "emoji.gif");
        writeBytes(expected);

        File found = NamedMediaFileFinder.findInRoot(root, Arrays.asList("missing.gif", "emoji.gif"), 3);

        assertEquals(expected.getCanonicalFile(), found.getCanonicalFile());
    }

    @Test
    public void findInRootStopsWhenDepthIsExhausted() throws Exception {
        File root = Files.createTempDirectory("wxo-named-depth").toFile();
        File tooDeep = new File(new File(root, "a"), "clip.mp4");
        writeBytes(tooDeep);

        File found = NamedMediaFileFinder.findInRoot(root, Collections.singletonList("clip.mp4"), 0);

        assertNull(found);
    }

    @Test
    public void findInRootIgnoresBlankAndMissingCandidates() throws Exception {
        File root = Files.createTempDirectory("wxo-named-empty").toFile();
        File file = new File(root, "voice.amr");
        writeBytes(file);

        assertNull(NamedMediaFileFinder.findInRoot(root, null, 1));
        assertNull(NamedMediaFileFinder.findInRoot(root, Collections.singletonList(""), 1));
        assertTrue(file.isFile());
    }

    private static void writeBytes(File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
    }
}
