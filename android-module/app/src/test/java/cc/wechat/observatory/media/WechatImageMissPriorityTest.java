package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatImageMissPriorityTest {
    @Test
    public void betterKeepsCurrentWhenCandidateIsMissing() {
        WechatImageFileResolver.ProfileSearchResult current =
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.unsupported(new File("candidate.bin")));

        WechatImageFileResolver.ProfileSearchResult better =
                WechatImageMissPriority.better(
                        current,
                        WechatImageFileResolver.ProfileSearchResult.missing());

        assertSame(current, better);
    }

    @Test
    public void betterUsesCandidateWhenCurrentIsMissing() {
        WechatImageFileResolver.ProfileSearchResult candidate =
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(new File("th_candidate")));

        WechatImageFileResolver.ProfileSearchResult better =
                WechatImageMissPriority.better(
                        WechatImageFileResolver.ProfileSearchResult.missing(),
                        candidate);

        assertSame(candidate, better);
    }

    @Test
    public void betterPrefersThumbnailMissOverUnsupportedMiss() {
        WechatImageFileResolver.ProfileSearchResult unsupported =
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.unsupported(new File("candidate.bin")));
        WechatImageFileResolver.ProfileSearchResult thumbnail =
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(new File("th_candidate")));

        WechatImageFileResolver.ProfileSearchResult better =
                WechatImageMissPriority.better(unsupported, thumbnail);

        assertSame(thumbnail, better);
        assertTrue(better.isLowQualityThumbnail());
    }

    @Test
    public void betterKeepsExistingThumbnailMissOverUnsupportedMiss() {
        WechatImageFileResolver.ProfileSearchResult thumbnail =
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(new File("th_candidate")));
        WechatImageFileResolver.ProfileSearchResult unsupported =
                WechatImageFileResolver.ProfileSearchResult.from(
                        WechatImageFileResolver.CandidateResolution.unsupported(new File("candidate.bin")));

        WechatImageFileResolver.ProfileSearchResult better =
                WechatImageMissPriority.better(thumbnail, unsupported);

        assertSame(thumbnail, better);
    }

    @Test
    public void firstNonMissingKeepsDirectCandidateOrder() {
        WechatImageFileResolver.CandidateResolution unsupported =
                WechatImageFileResolver.CandidateResolution.unsupported(new File("candidate.bin"));
        WechatImageFileResolver.CandidateResolution thumbnail =
                WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(new File("th_candidate"));

        WechatImageFileResolver.CandidateResolution better =
                WechatImageMissPriority.firstNonMissing(unsupported, thumbnail);

        assertSame(unsupported, better);
    }

    @Test
    public void firstNonMissingUsesCandidateWhenCurrentIsMissing() {
        WechatImageFileResolver.CandidateResolution thumbnail =
                WechatImageFileResolver.CandidateResolution.lowQualityThumbnail(new File("th_candidate"));

        WechatImageFileResolver.CandidateResolution better =
                WechatImageMissPriority.firstNonMissing(
                        WechatImageFileResolver.CandidateResolution.missing(null),
                        thumbnail);

        assertSame(thumbnail, better);
    }
}
