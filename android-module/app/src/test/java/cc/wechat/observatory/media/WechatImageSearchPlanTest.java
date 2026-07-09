package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatImageSearchPlanTest {
    private static final String IMAGE_BASE = "abcd1234ef567890abcd1234ef567890";

    @Test
    public void referencePointerNamesKeepWechatPointerOrder() {
        List<String> hdThumbnail = WechatImageSearchPlan.referencePointerNames("th_" + IMAGE_BASE + "hd");
        List<String> imageWithExtension = WechatImageSearchPlan.referencePointerNames(IMAGE_BASE + ".jpg");

        assertEquals(Arrays.asList(
                "th_" + IMAGE_BASE + WechatImageFiles.REF_SUFFIX,
                "th_" + IMAGE_BASE + "hd" + WechatImageFiles.REF_SUFFIX), hdThumbnail);
        assertEquals(Arrays.asList(
                IMAGE_BASE + ".jpg" + WechatImageFiles.REF_SUFFIX,
                IMAGE_BASE + WechatImageFiles.REF_SUFFIX), imageWithExtension);
    }

    @Test
    public void bucketKeyNormalizesThumbnailHdExtensionAndRefSuffix() {
        assertEquals(IMAGE_BASE, WechatImageSearchPlan.bucketKey("th_" + IMAGE_BASE + "hd"));
        assertEquals(IMAGE_BASE, WechatImageSearchPlan.bucketKey("/tmp/" + IMAGE_BASE + ".jpg"));
        assertEquals(IMAGE_BASE, WechatImageSearchPlan.bucketKey(IMAGE_BASE + WechatImageFiles.REF_SUFFIX));
        assertEquals("", WechatImageSearchPlan.bucketKey("not-a-wechat-image"));
    }

    @Test
    public void candidateNameMatcherAcceptsReferencePointerVariant() {
        List<String> names = Arrays.asList(IMAGE_BASE + ".jpg", "th_" + IMAGE_BASE);

        assertTrue(WechatImageSearchPlan.matchesCandidateName(IMAGE_BASE + ".jpg", names));
        assertTrue(WechatImageSearchPlan.matchesCandidateName("th_" + IMAGE_BASE + WechatImageFiles.REF_SUFFIX, names));
        assertFalse(WechatImageSearchPlan.matchesCandidateName("other" + WechatImageFiles.REF_SUFFIX, names));
    }

    @Test
    public void candidateNamesPreserveThumbnailExpansionOrder() {
        List<String> names = WechatImageSearchPlan.candidateNames("th_" + IMAGE_BASE + "hd");

        assertTrue(names.contains(IMAGE_BASE));
        assertTrue(names.contains(IMAGE_BASE + ".jpg"));
        assertTrue(names.contains("th_" + IMAGE_BASE));
        assertTrue(names.contains("th_" + IMAGE_BASE + "hd"));
        assertFalse(names.contains(IMAGE_BASE + "hd.jpg"));
        assertTrue(names.indexOf("th_" + IMAGE_BASE) < names.indexOf("th_" + IMAGE_BASE + "hd"));
    }

    @Test
    public void candidateNamesKeepRefPointerSeparateFromRealImageVariants() {
        List<String> imagePointer = WechatImageSearchPlan.candidateNames(
                IMAGE_BASE + ".jpg" + WechatImageFiles.REF_SUFFIX);
        List<String> thumbnailPointer = WechatImageSearchPlan.candidateNames(
                "th_" + IMAGE_BASE + "hd" + WechatImageFiles.REF_SUFFIX);

        assertEquals(IMAGE_BASE + ".jpg" + WechatImageFiles.REF_SUFFIX, imagePointer.get(0));
        assertTrue(imagePointer.contains(IMAGE_BASE + ".jpg"));
        assertTrue(imagePointer.contains(IMAGE_BASE));
        assertFalse(imagePointer.contains(IMAGE_BASE + ".jpg" + WechatImageFiles.REF_SUFFIX + ".jpg"));

        assertEquals("th_" + IMAGE_BASE + "hd" + WechatImageFiles.REF_SUFFIX, thumbnailPointer.get(0));
        assertTrue(thumbnailPointer.contains(IMAGE_BASE));
        assertTrue(thumbnailPointer.contains(IMAGE_BASE + ".jpg"));
        assertTrue(thumbnailPointer.contains("th_" + IMAGE_BASE));
        assertTrue(thumbnailPointer.contains("th_" + IMAGE_BASE + "hd"));
        assertFalse(thumbnailPointer.contains(IMAGE_BASE + "hd.jpg"));
        assertFalse(thumbnailPointer.contains("th_" + IMAGE_BASE + "hd" + WechatImageFiles.REF_SUFFIX + ".jpg"));
    }
}
