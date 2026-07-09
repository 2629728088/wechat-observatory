package cc.wechat.observatory.media;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WechatImageInfoCandidateNamesTest {
    @Test
    public void fromValuesKeepsOnlySearchableImageNames() {
        WechatImageInfoCandidateNames names = WechatImageInfoCandidateNames.fromValues(Arrays.asList(
                "https://host/path/image.jpg?token=secret",
                "<msg><img /></msg>",
                "abcd1234ef567890abcd1234ef567890.jpg"));

        assertFalse(names.isEmpty());
        assertTrue(names.values().contains("abcd1234ef567890abcd1234ef567890.jpg"));
        assertTrue(names.values().contains("abcd1234ef567890abcd1234ef567890"));
        assertFalse(names.values().contains("https://host/path/image.jpg"));
        assertFalse(names.values().contains("host/path/image.jpg"));
        assertFalse(names.values().contains("image.jpg"));
    }

    @Test
    public void fromValuesExpandsUsableFieldsAndSkipsHttpLikeOrXmlValues() {
        String base = "abcd1234ef567890abcd1234ef567890";
        WechatImageInfoCandidateNames names = WechatImageInfoCandidateNames.fromValues(Arrays.asList(
                "https/path/image.jpg",
                "<msg><img/></msg>",
                "/tmp/" + base + ".jpg",
                "th_" + base + "hd",
                base + ".jpg"));

        assertContains(names.values(), base + ".jpg");
        assertContains(names.values(), base);
        assertContains(names.values(), "th_" + base);
        assertContains(names.values(), "th_" + base + "hd");
        assertFalse(names.values().contains("image.jpg"));
        assertFalse(names.values().contains("host/path/image.jpg"));
        assertFalse(names.values().contains("<msg><img/></msg>"));
        assertEquals(names.values().indexOf(base + ".jpg"), names.values().lastIndexOf(base + ".jpg"));
    }

    @Test
    public void fromValuesSkipsRealUrlsBeforeNormalization() {
        String base = "abcd1234ef567890abcd1234ef567890";
        WechatImageInfoCandidateNames names = WechatImageInfoCandidateNames.fromValues(Arrays.asList(
                "https://host/path/image.jpg?token=secret",
                "http://host/path/other.png",
                "HTTPS://host/path/upper.webp",
                base + ".jpg"));

        assertContains(names.values(), base + ".jpg");
        assertFalse(names.values().contains("host/path/image.jpg"));
        assertFalse(names.values().contains("host/path/other.png"));
        assertFalse(names.values().contains("host/path/upper.webp"));
        assertFalse(names.values().contains("image.jpg"));
        assertFalse(names.values().contains("other.png"));
        assertFalse(names.values().contains("upper.webp"));
    }

    @Test
    public void searchableValueRejectsUrlsHttpLikePathsXmlAndBlankValues() {
        assertFalse(WechatImageInfoCandidateNames.isSearchableValue(null));
        assertFalse(WechatImageInfoCandidateNames.isSearchableValue(" "));
        assertFalse(WechatImageInfoCandidateNames.isSearchableValue("https://host/path/image.jpg"));
        assertFalse(WechatImageInfoCandidateNames.isSearchableValue("http/path/image.jpg"));
        assertFalse(WechatImageInfoCandidateNames.isSearchableValue("<msg><img /></msg>"));
        assertTrue(WechatImageInfoCandidateNames.isSearchableValue("abcd1234ef567890abcd1234ef567890.jpg"));
    }

    @Test
    public void nullValuesBecomeEmptyCandidateNames() {
        WechatImageInfoCandidateNames names = WechatImageInfoCandidateNames.fromValues(null);

        assertTrue(names.isEmpty());
        assertEquals(Collections.emptyList(), names.values());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void valuesReturnsImmutableSnapshot() {
        WechatImageInfoCandidateNames names = WechatImageInfoCandidateNames.fromValues(
                Collections.singletonList("abcd1234ef567890abcd1234ef567890.jpg"));

        names.values().add("other.jpg");
    }

    private static void assertContains(List<String> values, String expected) {
        assertTrue("expected " + expected + " in " + values, values.contains(expected));
    }
}
