package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WechatImageInfoResultSelectorTest {
    @Test
    public void withoutCandidateNamesPreservesDirectMissDiagnostics() {
        File unsupported = new File("direct.bin");
        MediaResolver.ImageInfoResult result = WechatImageInfoResultSelector.withoutCandidateNames(
                WechatImageFileResolver.CandidateResolution.unsupported(unsupported),
                31L,
                WechatImageInfoCandidateNames.fromValues(Collections.emptyList()));

        assertEquals(MediaResolver.ImageInfoStatus.DIRECT_IMAGE_UNSUPPORTED, result.status());
        assertEquals(unsupported, result.source());
        assertNull(result.file());
        assertTrue(result.candidateNames().isEmpty());
    }

    @Test
    public void withoutCandidateNamesFallsBackToGenericStatus() {
        MediaResolver.ImageInfoResult result = WechatImageInfoResultSelector.withoutCandidateNames(
                WechatImageFileResolver.CandidateResolution.missing(null),
                32L,
                WechatImageInfoCandidateNames.fromValues(Collections.emptyList()));

        assertEquals(MediaResolver.ImageInfoStatus.NO_CANDIDATE_NAMES, result.status());
        assertEquals(32L, result.localInfoId());
        assertNull(result.source());
        assertNull(result.file());
    }

    @Test
    public void missPrefersProfileDiagnosticsBeforeDirectDiagnostics() {
        File directSource = new File("direct.bin");
        File profileSource = new File("profile-thumb.jpg");
        WechatImageInfoCandidateNames names = WechatImageInfoCandidateNames.fromValues(
                Collections.singletonList("profile-thumb.jpg"));

        MediaResolver.ImageInfoResult result = WechatImageInfoResultSelector.miss(
                WechatImageFileResolver.CandidateResolution.unsupported(directSource),
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(profileSource)),
                33L,
                names,
                Arrays.asList("imgPath=profile-thumb.jpg"));

        assertEquals(MediaResolver.ImageInfoStatus.PROFILE_IMAGE_THUMBNAIL, result.status());
        assertEquals(profileSource, result.source());
        assertNull(result.file());
        assertTrue(result.candidateNames().contains("profile-thumb.jpg"));
        assertTrue(result.fieldDebug().contains("imgPath=profile-thumb.jpg"));
    }

    @Test
    public void missFallsBackToGenericStatusWhenNoDiagnosticsExist() {
        WechatImageInfoCandidateNames names = WechatImageInfoCandidateNames.fromValues(
                Collections.singletonList("missing.jpg"));

        MediaResolver.ImageInfoResult result = WechatImageInfoResultSelector.miss(
                WechatImageFileResolver.CandidateResolution.missing(null),
                WechatImageFileResolver.ProfileSearchResult.missing(),
                34L,
                names,
                Collections.singletonList("imgPath=missing.jpg"));

        assertEquals(MediaResolver.ImageInfoStatus.CANDIDATES_NOT_FOUND, result.status());
        assertEquals(34L, result.localInfoId());
        assertNull(result.source());
        assertNull(result.file());
        assertTrue(result.candidateNames().contains("missing.jpg"));
        assertTrue(result.fieldDebug().contains("imgPath=missing.jpg"));
    }
}
