package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaSearchPlanTest {
    private static final String IMAGE_BASE = "abcd1234ef567890abcd1234ef567890";

    @Test
    public void normalizeHintRemovesQueryAndScheme() {
        assertEquals("host/path/image.jpg", MediaSearchPlan.normalizeHint("https://host/path/image.jpg?token=secret"));
        assertEquals("image.jpg", MediaSearchPlan.normalizeHint(" image.jpg "));
        assertEquals("", MediaSearchPlan.normalizeHint(null));
    }

    @Test
    public void imageCandidatesIncludeOriginalBaseAndThumbnailVariants() {
        List<String> names = MediaSearchPlan.candidateNames(MediaFiles.MESSAGE_TYPE_IMAGE, "dir/photo.jpg", "");

        assertContains(names, "photo.jpg");
        assertContains(names, "photo");
        assertContains(names, "photo.jpeg");
        assertContains(names, "photo.png");
        assertContains(names, "photo.webp");
        assertContains(names, "th_photohd");
        assertContains(names, "th_photo");
        assertBefore(names, "th_photo", "th_photohd");
    }

    @Test
    public void imageThumbnailHintExpandsBackToBaseVariants() {
        List<String> names = MediaSearchPlan.candidateNames(MediaFiles.MESSAGE_TYPE_IMAGE, "th_abcd1234", "");

        assertContains(names, "abcd1234.jpg");
        assertContains(names, "abcd1234.webp");
        assertContains(names, "th_abcd1234hd");
        assertContains(names, "th_abcd1234");
        assertBefore(names, "th_abcd1234", "th_abcd1234hd");
    }

    @Test
    public void imageHdThumbnailHintExpandsBackToCanonicalWechatBase() {
        List<String> names = MediaSearchPlan.candidateNames(
                MediaFiles.MESSAGE_TYPE_IMAGE,
                "th_" + IMAGE_BASE + "hd",
                "");

        assertContains(names, IMAGE_BASE);
        assertContains(names, IMAGE_BASE + ".jpg");
        assertContains(names, IMAGE_BASE + ".webp");
        assertContains(names, "th_" + IMAGE_BASE);
        assertContains(names, "th_" + IMAGE_BASE + "hd");
        assertBefore(names, "th_" + IMAGE_BASE, "th_" + IMAGE_BASE + "hd");
        assertFalse("should not expand hd suffix as part of the real image base: " + names,
                names.contains(IMAGE_BASE + "hd.jpg"));
    }

    @Test
    public void videoCandidatesIncludeVideoPrefixVariants() {
        List<String> names = MediaSearchPlan.candidateNames(MediaFiles.MESSAGE_TYPE_VIDEO, "clip", "");

        assertContains(names, "clip");
        assertContains(names, "clip.mp4");
        assertContains(names, "video_clip");
        assertContains(names, "video_clip.mp4");
    }

    @Test
    public void voiceCandidatesIncludeBaseAndKnownExtensions() {
        List<String> names = MediaSearchPlan.candidateNames(MediaFiles.MESSAGE_TYPE_VOICE, "voice/path/msg.silk", "");

        assertContains(names, "voice/path/msg.silk");
        assertContains(names, "msg.silk");
        assertContains(names, "msg");
        assertContains(names, "msg.amr");
        assertBefore(names, "msg.silk", "msg");
        assertBefore(names, "msg", "msg.amr");
    }

    @Test
    public void emojiCandidatesIncludeMd5Variants() {
        List<String> names = MediaSearchPlan.candidateNames(MediaFiles.MESSAGE_TYPE_EMOJI, "", "ABCDEF");

        assertContains(names, "abcdef");
        assertContains(names, "abcdef.gif");
        assertContains(names, "emoji_abcdef.webp");
        assertContains(names, "thumb_abcdef.png");
    }

    @Test
    public void searchRootsStayStableForKnownTypes() {
        assertArrayEquals(new String[]{"image2"}, MediaSearchPlan.searchRoots(MediaFiles.MESSAGE_TYPE_IMAGE));
        assertArrayEquals(new String[]{"image2"}, MediaSearchPlan.imageSearchRoots());
        assertArrayEquals(new String[]{"voice2"}, MediaSearchPlan.searchRoots(MediaFiles.MESSAGE_TYPE_VOICE));
        assertArrayEquals(
                new String[]{"video", "c2c_temp/origin/video", "c2c_temp"},
                MediaSearchPlan.searchRoots(MediaFiles.MESSAGE_TYPE_VIDEO));
        assertEquals(Arrays.asList("finder/video", "video"), Arrays.asList(MediaSearchPlan.videoCacheSearchRoots()));
        assertEquals(Arrays.asList("files/public/emoji", "cache"), Arrays.asList(MediaSearchPlan.emojiFallbackSearchRoots()));
        assertEquals(6, MediaSearchPlan.emojiFallbackSearchDepth("files/public/emoji"));
        assertEquals(4, MediaSearchPlan.emojiFallbackSearchDepth("cache"));
    }

    private static void assertContains(List<String> values, String expected) {
        assertTrue("expected " + expected + " in " + values, values.contains(expected));
    }

    private static void assertBefore(List<String> values, String first, String second) {
        int firstIndex = values.indexOf(first);
        int secondIndex = values.indexOf(second);
        assertTrue("expected " + first + " before " + second + " in " + values,
                firstIndex >= 0 && secondIndex >= 0 && firstIndex < secondIndex);
    }
}
