package cc.wechat.observatory.media;

import java.io.File;

final class NonImageMediaSelectionStrategy {
    private final MediaFileSelector.EmojiResolver emojiResolver;

    NonImageMediaSelectionStrategy(MediaFileSelector.EmojiResolver emojiResolver) {
        this.emojiResolver = emojiResolver;
    }

    MediaFileSelector.Selection select(
            MediaFileSelector.Request request,
            ImageDownloadResolution.Candidate baseCandidate) {
        File file = MediaSelectionStatusMapper.candidateFile(baseCandidate);
        if (isUsable(file)) {
            return MediaFileSelector.Selection.of(MediaSelectionStatusMapper.baseStatus(baseCandidate), file);
        }
        if (request != null && MediaFiles.isEmojiMessageType(request.type()) && emojiResolver != null) {
            file = emojiResolver.resolve(request.emojiMd5());
            if (isUsable(file)) {
                return MediaFileSelector.Selection.of(MediaFileSelector.SelectionStatus.EMOJI_FILE, file);
            }
        }
        return null;
    }

    private static boolean isUsable(File file) {
        return MediaFiles.isExistingFile(file);
    }
}
