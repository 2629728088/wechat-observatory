package cc.wechat.observatory.media;

public interface MediaAttachmentServices {
    ImageDownloadResolution.Candidate resolveMediaFileCandidate(int type, String mediaHint, long createTime, String emojiMd5);

    default ImageDownloadResolution.Candidate resolveImageInfoCandidate(long localId, long serverId, String talker) {
        return ImageDownloadResolution.Candidate.missing();
    }

    boolean requestImageDownload(long localId, long serverId, String talker);

    String encode(byte[] bytes);

    void log(String message);
}
