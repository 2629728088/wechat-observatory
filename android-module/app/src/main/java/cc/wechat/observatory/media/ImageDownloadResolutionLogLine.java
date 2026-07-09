package cc.wechat.observatory.media;

import java.io.File;

final class ImageDownloadResolutionLogLine {
    private ImageDownloadResolutionLogLine() {
    }

    static String imageInfoFile(long localId, File file) {
        return "image media resolved from ImgInfo msgId=" + localId
                + " file=" + fileName(file);
    }

    static String imageInfoRefTarget(long localId, File file) {
        return "image media resolved from ImgInfo ref target msgId=" + localId
                + " file=" + fileName(file);
    }

    static String downloadedFile(long localId, File file) {
        return "image media resolved after NetSceneGetMsgImg msgId=" + localId
                + " file=" + fileName(file);
    }

    static String downloadedRefTarget(long localId, File file) {
        return "image media resolved ref target after NetSceneGetMsgImg msgId=" + localId
                + " file=" + fileName(file);
    }

    static String downloadedThumbnail(long localId, File file) {
        return "image media resolved thumbnail only after NetSceneGetMsgImg msgId=" + localId
                + " file=" + fileName(file)
                + " size=" + fileSize(file);
    }

    static String downloadedUnsupported(long localId, File file) {
        return "image media candidate unsupported after NetSceneGetMsgImg msgId=" + localId
                + " file=" + fileName(file);
    }

    static String imageInfoThumbnail(long localId, File file) {
        return "image media resolved thumbnail only msgId=" + localId
                + " file=" + fileName(file)
                + " size=" + fileSize(file);
    }

    static String imageInfoUnsupported(long localId, File file) {
        return "image media candidate unsupported from ImgInfo msgId=" + localId
                + " file=" + fileName(file);
    }

    static String notReady(long localId, long serverId, boolean requestedDownload) {
        String source = requestedDownload ? "image NetSceneGetMsgImg requested" : "image local retry";
        return source + " but file not ready msgId=" + localId + " msgSvrId=" + serverId;
    }

    private static String fileName(File file) {
        return file == null ? "" : file.getName();
    }

    private static long fileSize(File file) {
        return file == null ? 0L : file.length();
    }
}
