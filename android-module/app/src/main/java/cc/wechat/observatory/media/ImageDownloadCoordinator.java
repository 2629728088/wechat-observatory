package cc.wechat.observatory.media;

public final class ImageDownloadCoordinator {
    public interface Downloader {
        boolean request(long localId, long serverId, String talker);
    }

    public interface CandidateImageInfoResolver {
        ImageDownloadResolution.Candidate resolve(long localId, long serverId, String talker);
    }

    public interface CandidateFallbackResolver {
        ImageDownloadResolution.Candidate resolve(String mediaHint, long createTime);
    }

    public interface Logger {
        void log(String message);
    }

    private final ImageDownloadRequestGate requestGate;
    private final ImageDownloadCandidateCollector candidateCollector;
    private final Logger logger;

    public ImageDownloadCoordinator(
            ImageDownloadRequestTracker tracker,
            Downloader downloader,
            CandidateImageInfoResolver imageInfoResolver,
            CandidateFallbackResolver fallbackResolver,
            Logger logger) {
        this.requestGate = new ImageDownloadRequestGate(tracker);
        this.candidateCollector = new ImageDownloadCandidateCollector(
                downloader,
                imageInfoResolver,
                fallbackResolver);
        this.logger = logger;
    }

    public ImageDownloadResolution requestAndResolve(
            String mediaHint,
            Long msgId,
            Long msgSvrId,
            long createTime,
            String talker) {
        ImageDownloadRequestGate.Decision requestDecision = requestGate.evaluate(msgId, msgSvrId);
        long localId = requestDecision.localId();
        long serverId = requestDecision.serverId();
        if (!requestDecision.canResolve()) {
            return ImageDownloadResolution.evaluateCandidates(
                    localId,
                    serverId,
                    false,
                    ImageDownloadResolution.Candidate.missing(),
                    ImageDownloadResolution.Candidate.missing());
        }
        ImageDownloadCandidateSet candidates =
                candidateCollector.collect(requestDecision, mediaHint, createTime, talker);
        ImageDownloadResolution resolution = ImageDownloadResolution.evaluateCandidateSet(
                localId,
                serverId,
                candidates);
        if (logger != null) {
            logger.log(resolution.logMessage());
        }
        return resolution;
    }
}
