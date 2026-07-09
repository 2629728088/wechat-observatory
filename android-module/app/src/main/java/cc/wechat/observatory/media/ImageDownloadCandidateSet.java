package cc.wechat.observatory.media;

final class ImageDownloadCandidateSet {
    private final boolean requestedDownload;
    private final ImageDownloadResolution.Candidate imageInfoCandidate;
    private final ImageDownloadResolution.Candidate downloadedFallbackCandidate;

    private ImageDownloadCandidateSet(
            boolean requestedDownload,
            ImageDownloadResolution.Candidate imageInfoCandidate,
            ImageDownloadResolution.Candidate downloadedFallbackCandidate) {
        this.requestedDownload = requestedDownload;
        this.imageInfoCandidate = normalize(imageInfoCandidate);
        this.downloadedFallbackCandidate = normalize(downloadedFallbackCandidate);
    }

    static ImageDownloadCandidateSet missing() {
        return new ImageDownloadCandidateSet(
                false,
                ImageDownloadResolution.Candidate.missing(),
                ImageDownloadResolution.Candidate.missing());
    }

    static ImageDownloadCandidateSet of(
            boolean requestedDownload,
            ImageDownloadResolution.Candidate imageInfoCandidate,
            ImageDownloadResolution.Candidate downloadedFallbackCandidate) {
        return new ImageDownloadCandidateSet(
                requestedDownload,
                imageInfoCandidate,
                downloadedFallbackCandidate);
    }

    boolean requestedDownload() {
        return requestedDownload;
    }

    ImageDownloadResolution.Candidate imageInfoCandidate() {
        return imageInfoCandidate;
    }

    ImageDownloadResolution.Candidate downloadedFallbackCandidate() {
        return downloadedFallbackCandidate;
    }

    private static ImageDownloadResolution.Candidate normalize(
            ImageDownloadResolution.Candidate candidate) {
        return candidate == null ? ImageDownloadResolution.Candidate.missing() : candidate;
    }
}
