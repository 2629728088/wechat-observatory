package cc.wechat.observatory.wechat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class WechatImageDownloadLogLineTest {
    @Test
    public void formatsQueueLifecycleWithoutChangingExistingText() {
        assertEquals(
                "image NetSceneGetMsgImg skipped: missing local msgId msgSvrId=20",
                WechatImageDownloadLogLine.skippedMissingLocalId(20L));
        assertEquals(
                "image NetSceneGetMsgImg enqueued imageInfoId=30 msgId=10 msgSvrId=20 type=1",
                WechatImageDownloadLogLine.enqueued(30L, 10L, 20L, 1));
        assertEquals(
                "image NetSceneGetMsgImg dispatched via NetSceneQueue result=queued",
                WechatImageDownloadLogLine.dispatched("queued"));
        assertEquals(
                "image NetSceneGetMsgImg autostart enabled msgId=10",
                WechatImageDownloadLogLine.autostartEnabled(10L));
    }

    @Test
    public void formatsSceneEndWithMissingArguments() {
        assertEquals(
                "image NetSceneGetMsgImg onSceneEnd errType=0 errCode=0",
                WechatImageDownloadLogLine.onSceneEnd(new Object[]{0, 0}));
        assertEquals(
                "image NetSceneGetMsgImg onSceneEnd errType=? errCode=?",
                WechatImageDownloadLogLine.onSceneEnd(null));
        assertEquals(
                "image NetSceneGetMsgImg onSceneEnd errType=4 errCode=?",
                WechatImageDownloadLogLine.onSceneEnd(new Object[]{4}));
    }

    @Test
    public void formatsFailuresFromRootCause() {
        Throwable failure = new IllegalStateException(
                "wrapper",
                new IllegalArgumentException("queue failed"));

        assertEquals(
                "image NetSceneGetMsgImg request failed msgId=10 msgSvrId=20 error=queue failed",
                WechatImageDownloadLogLine.requestFailed(10L, 20L, failure));
        assertEquals(
                "image NetSceneGetMsgImg autostart scene flag failed msgId=10 error=queue failed",
                WechatImageDownloadLogLine.autostartSceneFlagFailed(10L, failure));
        assertEquals(
                "image NetSceneGetMsgImg autostart queue flag failed msgId=10 error=queue failed",
                WechatImageDownloadLogLine.autostartQueueFlagFailed(10L, failure));
        assertEquals(
                "image NetSceneGetMsgImg callback unregister skipped: queue failed",
                WechatImageDownloadLogLine.callbackUnregisterSkipped(failure));
        assertFalse(WechatImageDownloadLogLine.requestFailed(10L, 20L, failure).contains("<msg"));
    }
}
