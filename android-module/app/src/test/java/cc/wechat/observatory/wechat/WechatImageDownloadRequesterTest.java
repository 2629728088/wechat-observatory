package cc.wechat.observatory.wechat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class WechatImageDownloadRequesterTest {
    @Test
    public void requestSkipsMissingLocalId() {
        AtomicInteger resolverCalls = new AtomicInteger();
        AtomicInteger runnerCalls = new AtomicInteger();
        List<String> logs = new ArrayList<>();

        boolean requested = new WechatImageDownloadRequester(
                (localId, serverId) -> {
                    resolverCalls.incrementAndGet();
                    return 0L;
                },
                callable -> {
                    runnerCalls.incrementAndGet();
                    callable.call();
                },
                millis -> {
                },
                logs::add,
                (classLoader, imageInfoId, localId, serverId, talker) -> {
                })
                .request(getClass().getClassLoader(), 0L, 20L, "talker");

        assertFalse(requested);
        assertEquals(0, resolverCalls.get());
        assertEquals(0, runnerCalls.get());
        assertTrue(contains(logs, "skipped: missing local msgId"));
    }

    @Test
    public void requestRunsRuntimeOnMainThreadAndUsesResolvedImageInfoId() {
        ClassLoader loader = getClass().getClassLoader();
        AtomicInteger runnerCalls = new AtomicInteger();
        AtomicLong sleptMs = new AtomicLong();
        CaptureRuntime runtime = new CaptureRuntime();

        boolean requested = new WechatImageDownloadRequester(
                (localId, serverId) -> 500L,
                callable -> {
                    runnerCalls.incrementAndGet();
                    callable.call();
                },
                sleptMs::set,
                message -> {
                },
                runtime)
                .request(loader, 10L, 20L, "room");

        assertTrue(requested);
        assertEquals(1, runnerCalls.get());
        assertEquals(1800L, sleptMs.get());
        assertSame(loader, runtime.classLoader);
        assertEquals(500L, runtime.imageInfoId);
        assertEquals(10L, runtime.localId);
        assertEquals(20L, runtime.serverId);
        assertEquals("room", runtime.talker);
    }

    @Test
    public void requestFallsBackToLocalIdWhenResolverMisses() {
        CaptureRuntime runtime = new CaptureRuntime();

        boolean requested = new WechatImageDownloadRequester(
                (localId, serverId) -> 0L,
                Callable::call,
                millis -> {
                },
                message -> {
                },
                runtime)
                .request(getClass().getClassLoader(), 11L, 0L, null);

        assertTrue(requested);
        assertEquals(11L, runtime.imageInfoId);
        assertEquals(11L, runtime.localId);
    }

    @Test
    public void requestLogsFailureFromRuntimeInvoker() {
        List<String> logs = new ArrayList<>();
        AtomicInteger sleepCalls = new AtomicInteger();

        boolean requested = new WechatImageDownloadRequester(
                (localId, serverId) -> 500L,
                Callable::call,
                millis -> sleepCalls.incrementAndGet(),
                logs::add,
                (classLoader, imageInfoId, localId, serverId, talker) -> {
                    throw new IllegalStateException("boom");
                })
                .request(getClass().getClassLoader(), 10L, 20L, "room");

        assertFalse(requested);
        assertEquals(0, sleepCalls.get());
        assertTrue(contains(logs, "request failed msgId=10"));
        assertTrue(contains(logs, "boom"));
    }

    private static boolean contains(List<String> logs, String expected) {
        for (String log : logs) {
            if (log != null && log.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static final class CaptureRuntime implements WechatImageDownloadRequester.RuntimeInvoker {
        ClassLoader classLoader;
        long imageInfoId;
        long localId;
        long serverId;
        String talker;

        @Override
        public void request(ClassLoader classLoader, long imageInfoId, long localId, long serverId, String talker) {
            this.classLoader = classLoader;
            this.imageInfoId = imageInfoId;
            this.localId = localId;
            this.serverId = serverId;
            this.talker = talker;
        }
    }
}
