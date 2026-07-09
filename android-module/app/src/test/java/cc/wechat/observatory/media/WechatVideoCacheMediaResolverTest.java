package cc.wechat.observatory.media;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WechatVideoCacheMediaResolverTest {
    @Test
    public void resolvesVideoCacheFile() throws Exception {
        File appRoot = Files.createTempDirectory("wxo-video-cache").toFile();
        File video = new File(new File(new File(appRoot, "cache"), "finder/video"), "video_abc.mp4");
        writeBytes(video, "video");

        MediaResolver.Result result = WechatVideoCacheMediaResolver.resolve(
                appRoot,
                MediaSearchPlan.candidateNames(MediaFiles.MESSAGE_TYPE_VIDEO, "abc.mp4", ""));

        assertEquals(MediaResolver.ResolutionStatus.VIDEO_CACHE_FILE, result.status());
        assertEquals(video.getCanonicalFile(), result.file().getCanonicalFile());
    }

    private static void writeBytes(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        Files.write(file.toPath(), value.getBytes(StandardCharsets.US_ASCII));
    }
}
