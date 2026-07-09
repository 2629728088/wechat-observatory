package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public final class WechatImageStatusMapperTest {
    @Test
    public void mediaFileStatusKeepsDirectAndProfileRefTargetsDistinct() {
        assertEquals(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_REF_TARGET,
                WechatImageStatusMapper.mediaFileStatus(
                        true,
                        true));
        assertEquals(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_REF_TARGET,
                WechatImageStatusMapper.mediaFileStatus(
                        true,
                        false));
    }

    @Test
    public void mediaFileStatusDefaultsToImageFileForRealCandidates() {
        assertEquals(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_FILE,
                WechatImageStatusMapper.mediaFileStatus(
                        false,
                        true));
        assertEquals(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_FILE,
                WechatImageStatusMapper.mediaFileStatus(
                        false,
                        false));
    }

    @Test
    public void mediaFileStatusReadsResolvedCandidateReferenceTarget() {
        WechatImageResolvedCandidate referenceTarget = WechatImageResolvedCandidate.from(
                WechatImageFileResolver.CandidateResolution.refTarget(
                        new File("pointer.ref"),
                        new File("image.jpg")));
        WechatImageResolvedCandidate realImage = WechatImageResolvedCandidate.from(
                WechatImageFileResolver.CandidateResolution.realImage(new File("image.jpg")));

        assertEquals(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_REF_TARGET,
                WechatImageStatusMapper.mediaFileStatus(referenceTarget, true));
        assertEquals(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_FILE,
                WechatImageStatusMapper.mediaFileStatus(realImage, false));
    }

    @Test
    public void mediaMissStatusesKeepDirectAndProfileScope() {
        assertEquals(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_THUMBNAIL,
                WechatImageStatusMapper.mediaThumbnailStatus(true));
        assertEquals(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_THUMBNAIL,
                WechatImageStatusMapper.mediaThumbnailStatus(false));
        assertEquals(
                MediaResolver.ResolutionStatus.DIRECT_IMAGE_UNSUPPORTED,
                WechatImageStatusMapper.mediaUnsupportedStatus(true));
        assertEquals(
                MediaResolver.ResolutionStatus.PROFILE_IMAGE_UNSUPPORTED,
                WechatImageStatusMapper.mediaUnsupportedStatus(false));
    }

    @Test
    public void imageInfoFileStatusKeepsDirectAndProfileRefTargetsDistinct() {
        assertEquals(
                MediaResolver.ImageInfoStatus.DIRECT_IMAGE_REF_TARGET,
                WechatImageStatusMapper.imageInfoFileStatus(
                        true,
                        true));
        assertEquals(
                MediaResolver.ImageInfoStatus.PROFILE_IMAGE_REF_TARGET,
                WechatImageStatusMapper.imageInfoFileStatus(
                        true,
                        false));
    }

    @Test
    public void imageInfoFileStatusReadsResolvedCandidateReferenceTarget() {
        WechatImageResolvedCandidate referenceTarget = WechatImageResolvedCandidate.from(
                WechatImageFileResolver.CandidateResolution.refTarget(
                        new File("pointer.ref"),
                        new File("image.jpg")));
        WechatImageResolvedCandidate realImage = WechatImageResolvedCandidate.from(
                WechatImageFileResolver.CandidateResolution.realImage(new File("image.jpg")));

        assertEquals(
                MediaResolver.ImageInfoStatus.DIRECT_IMAGE_REF_TARGET,
                WechatImageStatusMapper.imageInfoFileStatus(referenceTarget, true));
        assertEquals(
                MediaResolver.ImageInfoStatus.PROFILE_IMAGE_FILE,
                WechatImageStatusMapper.imageInfoFileStatus(realImage, false));
    }

    @Test
    public void imageInfoMissStatusesKeepDirectAndProfileScope() {
        assertEquals(
                MediaResolver.ImageInfoStatus.DIRECT_IMAGE_THUMBNAIL,
                WechatImageStatusMapper.imageInfoThumbnailStatus(true));
        assertEquals(
                MediaResolver.ImageInfoStatus.PROFILE_IMAGE_THUMBNAIL,
                WechatImageStatusMapper.imageInfoThumbnailStatus(false));
        assertEquals(
                MediaResolver.ImageInfoStatus.DIRECT_IMAGE_UNSUPPORTED,
                WechatImageStatusMapper.imageInfoUnsupportedStatus(true));
        assertEquals(
                MediaResolver.ImageInfoStatus.PROFILE_IMAGE_UNSUPPORTED,
                WechatImageStatusMapper.imageInfoUnsupportedStatus(false));
    }
}
