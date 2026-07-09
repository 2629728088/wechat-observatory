package cc.wechat.observatory.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MediaCacheNamesTest {
    @Test
    public void safeExtensionUsesMediaNameBeforeUrlAndStripsQuery() {
        assertEquals(".jpg", MediaCacheNames.safeExtension("PHOTO.JPG?x=1", "https://example.test/file.png"));
        assertEquals(".png", MediaCacheNames.safeExtension("", "https://example.test/path/file.png?token=secret"));
    }

    @Test
    public void safeExtensionFallsBackForMissingLongOrUnsafeExtensions() {
        assertEquals(".img", MediaCacheNames.safeExtension("file", ""));
        assertEquals(".img", MediaCacheNames.safeExtension("file.verylong", ""));
        assertEquals(".img", MediaCacheNames.safeExtension("file.jp#g", ""));
    }

    @Test
    public void safeFileNameKeepsOnlySafeCharactersAndDropsPath() {
        assertEquals("photo-01_copy.jpg", MediaCacheNames.safeFileName("", "https://example.test/a/photo-01_copy.jpg?token=secret"));
        assertEquals("ca_b-c.123", MediaCacheNames.safeFileName("a b/c:a_b-c.123", ""));
    }

    @Test
    public void safeFileNameFallsBackForHiddenOrUnsafeNames() {
        assertEquals("file.hidden", MediaCacheNames.safeFileName(".hidden", "https://example.test/file.jpg"));
        assertEquals("file.bin", MediaCacheNames.safeFileName("???", ""));
    }
}
