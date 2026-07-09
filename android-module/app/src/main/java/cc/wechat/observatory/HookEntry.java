package cc.wechat.observatory;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cc.wechat.observatory.config.BridgeConfig;
import cc.wechat.observatory.gateway.WebSocketFrame;
import cc.wechat.observatory.model.MessagePayload;
import cc.wechat.observatory.outbox.OutboxMediaFilePreparer;
import cc.wechat.observatory.util.BridgeLogger;
import cc.wechat.observatory.wechat.SendResult;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static cc.wechat.observatory.util.Strings.isBlank;
import static cc.wechat.observatory.util.Strings.json;
import static cc.wechat.observatory.util.Strings.shortError;
import static cc.wechat.observatory.util.Strings.trimRight;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final int MESSAGE_TYPE_QUOTE = 822083633;
    private static final int MESSAGE_TYPE_APPMSG = 49;
    private static final int MESSAGE_TYPE_FILE_TRANSFER = 1090519089;
    private static final int APPMSG_TYPE_LINK = 5;
    private static final int APPMSG_TYPE_FILE = 6;
    private static final int APPMSG_TYPE_CHAT_HISTORY = 19;
    private static final int APPMSG_TYPE_MINI_PROGRAM = 33;
    private static final int APPMSG_TYPE_MINI_PROGRAM_LEGACY = 36;
    private static final int APPMSG_TYPE_QUOTE = 57;
    private static final int MAX_CHAT_HISTORY_SOURCE_ITEMS = 50;
    private static final int BRIDGE_CONNECT_TIMEOUT_MS = 10000;
    private static final int BRIDGE_READ_TIMEOUT_MS = 30000;
    private static final Object WECHAT_SEND_LOCK = new Object();
    private static final AtomicBoolean WORKER_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean OUTBOX_WORKER_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean EXTERNAL_STORAGE_FALLBACK_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean REVOKE_EVENT_HOOKED = new AtomicBoolean(false);
    private static final ExecutorService POST_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "wechat-observatory-post");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static volatile String LAST_READY_STATE = "";
    private static volatile String LAST_CLASSLOADER_STATE = "";
    private static volatile Object LAST_DATABASE;
    private static volatile long LAST_CONTACT_SYNC_AT = 0L;
    private static volatile long LAST_CONTACT_SYNC_SKIP_LOG_AT = 0L;
    private static volatile long LAST_CONTACT_SCAN_LOG_AT = 0L;
    private static volatile long LAST_MESSAGE_POLL_AT = 0L;
    private static volatile long LAST_MESSAGE_ID = 0L;
    private static volatile boolean MESSAGE_WATERMARK_READY = false;
    private static volatile int MESSAGE_POLL_CONSECUTIVE_FAILS = 0;
    private static volatile long MESSAGE_POLL_BACKOFF_UNTIL = 0L;
    private static volatile long LAST_MESSAGE_POLL_FAIL_LOG_AT = 0L;
    private static final HookMediaAttachmentController MEDIA_ATTACHMENT_CONTROLLER =
            HookMediaServices.attachmentController(
                    HookEntry::wechatAppRoot,
                    HookEntry::wechatRuntimeClassLoader,
                    HookEntry::callOnMainThread,
                    HookEntry::sleepQuietly,
                    bytes -> Base64.encodeToString(bytes, Base64.NO_WRAP),
                    HookEntry::log,
                    HookEntry::loadEmojiInfoByMd5,
                    HookEntry::logEmojiInfoDiagnostic);
    private static final HookMediaRetryScheduler MEDIA_RETRY_SCHEDULER =
            HookMediaServices.mediaRetryScheduler(mediaRetryEnvironment(), MEDIA_ATTACHMENT_CONTROLLER);
    private static final HookWechatVoiceFilePreparer.Reflection WECHAT_VOICE_FILE_REFLECTION =
            new HookWechatVoiceFilePreparer.Reflection() {
                @Override
                public Class<?> findClass(ClassLoader classLoader, String name) throws ClassNotFoundException {
                    return HookEntry.findClass(classLoader, name);
                }

                @Override
                public Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
                    return HookEntry.findMethod(cls, name, parameterTypes);
                }

                @Override
                public Field findField(Class<?> cls, String name) throws NoSuchFieldException {
                    return HookEntry.findField(cls, name);
                }
            };
    private static final Set<Long> REVOKE_UPDATE_REPORTED_IDS = new HashSet<>();
    private static final Set<Long> REVOKE_CONFIRMED_IDS = new HashSet<>();
    private static volatile long LAST_WEBSOCKET_FAIL_LOG_AT = 0L;
    private static volatile String CURRENT_WXID = "";
    private static volatile String CURRENT_NICKNAME = "";
    private static volatile String REGISTERED_KEY = "";
    private static volatile String REGISTERED_DEVICE = "";
    private static volatile long LAST_REGISTER_ATTEMPT_AT = 0L;
    private static volatile long LAST_REGISTER_SUCCESS_AT = 0L;
    private static volatile long LAST_USER_SKIP_LOG_AT = 0L;
    private static volatile Context APP_CONTEXT;
    private static volatile ClassLoader WECHAT_CLASS_LOADER;
    private static final HookEmojiInfoDiagnosticReporter EMOJI_INFO_DIAGNOSTIC_REPORTER =
            new HookEmojiInfoDiagnosticReporter(
                    () -> runtimeClassLoader(WECHAT_CLASS_LOADER == null
                            ? HookEntry.class.getClassLoader()
                            : WECHAT_CLASS_LOADER),
                    HookEntry::loadEmojiInfoByMd5,
                    HookEntry::log);

    private static final class QuoteSource {
        long msgId;
        long msgSvrId;
        String talker;
        String content;
        int isSend;
        long createTime;
        int type;
        String msgSource;
        String senderWxid;
    }

    private static final class ChatHistorySource {
        long msgId;
        long msgSvrId;
        String talker;
        String content;
        int isSend;
        long createTime;
        int type;
        String msgSource;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!WECHAT_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        BridgeConfig config = BridgeConfig.load(null);
        if (!isTargetAndroidUser(config)) {
            logTargetUserSkip("skip WeChat hook in process " + lpparam.processName);
            return;
        }
        log("loading build video-send-feature-db-v2 process=" + lpparam.processName);
        hookExternalStorageFallback();
        boolean mainProcess = WECHAT_PACKAGE.equals(lpparam.processName);

        try {
            Class<?> sqliteDatabase = XposedHelpers.findClass("com.tencent.wcdb.database.SQLiteDatabase", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(
                    sqliteDatabase,
                    "insertWithOnConflict",
                    String.class,
                    String.class,
                    ContentValues.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            handleInsert(param);
                        }
                    });
            hookDatabaseCaptureMethods(sqliteDatabase);
            hookRevokeEventPublish(lpparam.classLoader);
            log("hooked WeChat WCDB access methods");
            if (mainProcess) {
                hookApplicationAttach(lpparam.classLoader);
                hookWeChatApplication(lpparam.classLoader);
            } else {
                log("skip outbox worker in process " + lpparam.processName);
            }
        } catch (Throwable t) {
            log("hook failed: " + t);
        }
    }

    private static void hookExternalStorageFallback() {
        if (!EXTERNAL_STORAGE_FALLBACK_HOOKED.compareAndSet(false, true)) {
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(
                    ContextWrapper.class,
                    "getExternalCacheDir",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Context context = contextFromWrapper(param.thisObject);
                            File dir = fallbackExternalDir(context, true, null);
                            if (dir != null) {
                                param.setResult(dir);
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    ContextWrapper.class,
                    "getExternalCacheDirs",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Context context = contextFromWrapper(param.thisObject);
                            File dir = fallbackExternalDir(context, true, null);
                            if (dir != null) {
                                param.setResult(new File[]{dir});
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    ContextWrapper.class,
                    "getExternalFilesDir",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Context context = contextFromWrapper(param.thisObject);
                            String type = param.args != null && param.args.length > 0 && param.args[0] instanceof String
                                    ? (String) param.args[0]
                                    : null;
                            File dir = fallbackExternalDir(context, false, type);
                            if (dir != null) {
                                param.setResult(dir);
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    ContextWrapper.class,
                    "getExternalFilesDirs",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Context context = contextFromWrapper(param.thisObject);
                            String type = param.args != null && param.args.length > 0 && param.args[0] instanceof String
                                    ? (String) param.args[0]
                                    : null;
                            File dir = fallbackExternalDir(context, false, type);
                            if (dir != null) {
                                param.setResult(new File[]{dir});
                            }
                        }
                    });
            log("hooked external storage fallback");
        } catch (Throwable t) {
            log("hook external storage fallback failed: " + shortError(t));
        }
    }

    private static Context contextFromWrapper(Object value) {
        if (value instanceof Context) {
            return (Context) value;
        }
        Context context = APP_CONTEXT;
        if (context != null) {
            return context;
        }
        Object app = currentApplication();
        return app instanceof Context ? (Context) app : null;
    }

    private static File fallbackExternalDir(Context context, boolean cache, String type) {
        if (context == null) {
            return null;
        }
        File root = cache ? context.getCacheDir() : context.getFilesDir();
        if (root == null) {
            return null;
        }
        File dir = new File(root, cache ? "external_cache" : "external_files");
        if (!cache && !isBlank(type)) {
            dir = new File(dir, safePathSegment(type));
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return null;
        }
        return dir;
    }

    private static String safePathSegment(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.replace('\\', '_').replace('/', '_').trim();
    }

    private static void hookDatabaseCaptureMethods(Class<?> sqliteDatabase) {
        for (Method method : sqliteDatabase.getDeclaredMethods()) {
            String name = method.getName();
            if (!shouldHookDatabaseMethod(name) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            try {
                method.setAccessible(true);
                final String methodName = name;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        captureDatabaseFromArgs(param.thisObject, param.args);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        captureDatabaseFromArgs(param.thisObject, param.args);
                        if (isMessageUpdateMethod(methodName)) {
                            handleMessageUpdate(param);
                        }
                    }
                });
            } catch (Throwable t) {
                log("hook db method " + name + " failed: " + shortError(t));
            }
        }
    }

    private static boolean shouldHookDatabaseMethod(String name) {
        return "rawQuery".equals(name)
                || "rawQueryWithFactory".equals(name)
                || "query".equals(name)
                || "queryWithFactory".equals(name)
                || "insert".equals(name)
                || "insertOrThrow".equals(name)
                || "replace".equals(name)
                || "replaceOrThrow".equals(name)
                || "update".equals(name)
                || "updateWithOnConflict".equals(name)
                || "delete".equals(name);
    }

    private static boolean isMessageUpdateMethod(String name) {
        return "update".equals(name) || "updateWithOnConflict".equals(name);
    }

    private static void hookRevokeEventPublish(final ClassLoader classLoader) {
        if (!REVOKE_EVENT_HOOKED.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> eventClass = findClass(classLoader, "com.tencent.mm.sdk.event.IEvent");
            Class<?> eventPoolClass = findClass(classLoader, "com.tencent.mm.sdk.event.d");
            Method publish = findMethod(eventPoolClass, "d", eventClass, boolean.class);
            XposedBridge.hookMethod(publish, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        handleRevokeEventAsync(param.args[0]);
                    }
                }
            });
            log("hooked WeChat revoke event publisher");
        } catch (Throwable t) {
            log("hook revoke event publisher failed: " + shortError(t));
        }
    }

    private static void handleRevokeEventAsync(final Object event) {
        if (!isRevokeMsgEvent(event)) {
            return;
        }
        POST_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                handleRevokeEvent(event);
            }
        });
    }

    private static boolean isRevokeMsgEvent(Object event) {
        return event != null
                && "com.tencent.mm.autogen.events.RevokeMsgEvent".equals(event.getClass().getName());
    }

    private static void handleRevokeEvent(Object event) {
        try {
            BridgeConfig config = BridgeConfig.load(bridgeContext());
            if (!isTargetAndroidUser(config)) {
                return;
            }
            if (!config.enabled || isBlank(config.baseUrl) || isBlank(config.apiKey)) {
                return;
            }
            if (!bindRuntimeIdentity(config) || !ensureRegistered(config)) {
                return;
            }

            MessagePayload payload = buildRevokeEventPayload(config, event);
            if (payload == null) {
                log("revoke event ignored because message row could not be resolved");
                return;
            }
            if (!rememberRevokeUpdate(payload)) {
                return;
            }
            rememberRevokeConfirmed(payload.chatRecordId);
            post(config, payload);
            log("reported revoke event msgId=" + payload.chatRecordId + " chat=" + redactedId(payload.chatId));
        } catch (Throwable t) {
            log("handle revoke event failed: " + shortError(t));
        }
    }

    private static MessagePayload buildRevokeEventPayload(BridgeConfig config, Object event) throws Exception {
        Object data = revokeEventData(event);
        if (data == null) {
            return null;
        }
        long eventMsgId = optionalLongField(data, "a", "msgId", "eventMsgId");
        long eventMsgSvrId = optionalLongField(data, "e", "msgSvrId", "newMsgId", "eventMsgSrvId");
        String replaceMsg = firstNonBlank(
                optionalStringField(data, "b", "replaceMsg", "replacemsg"),
                optionalStringField(data, "f"));

        Object message = optionalObjectField(data, "c", "d", "message", "msg");
        long objectMsgId = firstPositiveLong(
                optionalLongMethod(message, "getMsgId"),
                optionalLongField(message, "field_msgId", "msgId"));
        long objectMsgSvrId = firstPositiveLong(
                optionalLongMethod(message, "getMsgSvrId"),
                optionalLongField(message, "field_msgSvrId", "msgSvrId"));
        String objectTalker = firstNonBlank(
                optionalStringMethod(message, "Q0"),
                optionalStringMethod(message, "getTalker"),
                optionalStringField(message, "field_talker", "talker"));

        Object db = LAST_DATABASE;
        if (db == null) {
            db = findContactDatabaseOnMainThread(config);
        }

        MessagePayload source = null;
        long sourceMsgId = firstPositiveLong(eventMsgId, objectMsgId);
        if (db != null && sourceMsgId > 0L) {
            source = loadMessagePayloadById(config, db, sourceMsgId);
        }
        String session = firstNonBlank(
                source == null ? "" : source.chatId,
                objectTalker,
                optionalStringField(data, "session", "talker"));
        if (isBlank(session)) {
            return null;
        }

        long recordId = firstPositiveLong(
                source == null ? 0L : source.chatRecordId,
                sourceMsgId,
                objectMsgId,
                eventMsgSvrId,
                objectMsgSvrId);
        if (recordId <= 0L) {
            return null;
        }
        long serverId = firstPositiveLong(eventMsgSvrId, objectMsgSvrId);
        String rawXml = buildRevokeRawXml(session, recordId, serverId, replaceMsg);
        return buildSystemRevokePayload(config, source, session, recordId, rawXml, replaceMsg);
    }

    private static Object revokeEventData(Object event) {
        try {
            return findFieldAny(event.getClass(), "g").get(event);
        } catch (Throwable ignored) {
            Field[] fields = event.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(event);
                    if (value != null) {
                        return value;
                    }
                } catch (Throwable ignoredField) {
                    // Try the next candidate.
                }
            }
        }
        return null;
    }

    private static MessagePayload buildSystemRevokePayload(
            BridgeConfig config,
            MessagePayload source,
            String session,
            long recordId,
            String rawXml,
            String replaceMsg) {
        MessagePayload payload = new MessagePayload();
        payload.id = String.valueOf(recordId) + ":revoke";
        payload.eventId = recordId;
        payload.chatRecordId = recordId;
        payload.apiKey = config.apiKey;
        payload.device = config.device;
        payload.chatId = session;
        payload.chatKind = isChatroomTalker(session) ? "room" : "direct";
        payload.roomId = isChatroomTalker(session) ? session : "";
        payload.text = firstNonBlank(replaceMsg, "[系统消息]");
        payload.messageType = 10000;
        payload.rawXml = rawXml;
        payload.createTime = System.currentTimeMillis() / 1000L;

        if (source != null) {
            payload.direction = source.direction;
            payload.from = source.from;
            payload.to = source.to;
            payload.sender = source.sender;
            return payload;
        }

        payload.direction = "recv";
        payload.from = session;
        payload.to = config.selfWxid;
        payload.sender = "";
        return payload;
    }

    private static String buildRevokeRawXml(String session, long msgId, long msgSvrId, String replaceMsg) {
        String message = firstNonBlank(replaceMsg, "撤回了一条消息");
        String id = String.valueOf(msgId);
        String serverId = msgSvrId > 0L ? String.valueOf(msgSvrId) : id;
        return "<sysmsg type=\"revokemsg\"><revokemsg>"
                + "<session>" + xmlEscape(session) + "</session>"
                + "<oldmsgid>" + xmlEscape(id) + "</oldmsgid>"
                + "<msgid>" + xmlEscape(id) + "</msgid>"
                + "<newmsgid>" + xmlEscape(serverId) + "</newmsgid>"
                + "<msgsvrid>" + xmlEscape(serverId) + "</msgsvrid>"
                + "<replacemsg><![CDATA[" + cdataSafe(message) + "]]></replacemsg>"
                + "</revokemsg></sysmsg>";
    }

    private static void captureDatabaseFromArgs(Object db, Object[] args) {
        if (db == null || args == null) {
            return;
        }
        for (Object arg : args) {
            if (arg instanceof String && looksLikeMessageTableAccess((String) arg)) {
                if (LAST_DATABASE != db) {
                    LAST_DATABASE = db;
                    log("captured WeChat database from " + shorten((String) arg, 80));
                }
                return;
            }
        }
    }

    private static boolean looksLikeMessageTableAccess(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.US);
        String padded = normalized
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
        padded = " " + padded + " ";
        return "message".equals(normalized)
                || padded.contains(" from message ")
                || padded.contains(" join message ")
                || padded.contains(" update message ")
                || padded.contains(" into message ")
                || padded.contains(" delete from message ");
    }

    private static String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static void hookApplicationAttach(final ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "attach",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args != null && param.args.length > 0 && param.args[0] instanceof Context) {
                                APP_CONTEXT = (Context) param.args[0];
                                hookExternalStorageFallback();
                                ClassLoader runtimeLoader = param.thisObject == null ? null : param.thisObject.getClass().getClassLoader();
                                log("Application.attach captured context; start outbox worker");
                                startWorker(runtimeLoader == null ? classLoader : runtimeLoader);
                            }
                        }
                    });
            log("hooked Application.attach");
        } catch (Throwable t) {
            log("hook Application.attach failed: " + t);
        }
    }

    private static void hookWeChatApplication(final ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.tencent.mm.app.MMApplicationLike",
                    classLoader,
                    "onBaseContextAttached",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args != null && param.args.length > 0 && param.args[0] instanceof Context) {
                                APP_CONTEXT = (Context) param.args[0];
                                hookExternalStorageFallback();
                            }
                            ClassLoader runtimeLoader = param.thisObject == null ? null : param.thisObject.getClass().getClassLoader();
                            log("WeChat application init completed; start outbox worker");
                            startWorker(runtimeLoader == null ? classLoader : runtimeLoader);
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    "com.tencent.mm.app.MMApplicationLike",
                    classLoader,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object app = currentApplication();
                            if (app instanceof Context) {
                                APP_CONTEXT = (Context) app;
                                hookExternalStorageFallback();
                            }
                            ClassLoader runtimeLoader = param.thisObject == null ? null : param.thisObject.getClass().getClassLoader();
                            log("WeChat application onCreate completed; start outbox worker");
                            startWorker(runtimeLoader == null ? classLoader : runtimeLoader);
                        }
                    });
            log("hooked WeChat application init");
            startDelayedWorker(classLoader, 20000L);
        } catch (Throwable t) {
            log("hook WeChat application init failed, start worker with readiness gate: " + t);
            startWorker(classLoader);
        }
    }

    private static void startDelayedWorker(final ClassLoader classLoader, final long delayMs) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (!sleepOnce(delayMs)) {
                    return;
                }
                log("delayed worker fallback fired");
                startWorker(classLoader);
            }
        }, "wechat-observatory-delayed-start");
        thread.setDaemon(true);
        thread.start();
    }

    private static void handleMessageUpdate(XC_MethodHook.MethodHookParam param) {
        try {
            if (!isTargetAndroidUser(BridgeConfig.load(bridgeContext()))) {
                return;
            }
            String table = stringArg(param.args, 0);
            if (param.thisObject != null && "message".equals(table)) {
                LAST_DATABASE = param.thisObject;
            }
            if (!"message".equals(table) || affectedRows(param.getResult()) <= 0) {
                return;
            }
            ContentValues values = contentValuesArg(param.args);
            if (values == null) {
                return;
            }
            String content = values.getAsString("content");
            if (!containsRevokePayload(content)) {
                return;
            }

            BridgeConfig config = BridgeConfig.load(bridgeContext());
            if (!isTargetAndroidUser(config)) {
                return;
            }
            if (!config.enabled || isBlank(config.baseUrl) || isBlank(config.apiKey)) {
                return;
            }
            if (!bindRuntimeIdentity(config)) {
                return;
            }
            if (!ensureRegistered(config)) {
                return;
            }

            MessagePayload payload = loadRevokeUpdatePayload(config, param.thisObject, values, param.args);
            if (payload == null || !containsRevokePayload(firstNonBlank(payload.rawXml, content))) {
                log("revoke update ignored because message row could not be resolved");
                return;
            }
            markRevokeUpdatePayload(payload);
            if (!rememberRevokeUpdate(payload)) {
                return;
            }
            rememberRevokeConfirmed(payload.chatRecordId);
            postAsync(config, payload, "handle update revoke");
            log("reported revoke update msgId=" + payload.chatRecordId + " chat=" + redactedId(payload.chatId));
        } catch (Throwable t) {
            log("handle update revoke failed: " + t);
        }
    }

    private static void handleInsert(XC_MethodHook.MethodHookParam param) {
        try {
            if (!isTargetAndroidUser(BridgeConfig.load(bridgeContext()))) {
                return;
            }
            String table = stringArg(param.args, 0);
            if (param.thisObject != null && "message".equals(table)) {
                LAST_DATABASE = param.thisObject;
            }
            if (!"message".equals(table)) {
                return;
            }
            if (!(param.args[2] instanceof ContentValues)) {
                return;
            }

            ContentValues values = (ContentValues) param.args[2];
            String talker = values.getAsString("talker");
            String content = values.getAsString("content");
            Integer isSend = values.getAsInteger("isSend");
            Long msgId = values.getAsLong("msgId");
            Long msgSvrId = values.getAsLong("msgSvrId");
            Long createTime = values.getAsLong("createTime");
            Integer type = values.getAsInteger("type");
            String imgPath = values.getAsString("imgPath");

            int messageType = type == null ? 1 : type;
            if (!shouldReportMessage(talker, content, messageType)) {
                return;
            }

            BridgeConfig config = BridgeConfig.load(bridgeContext());
            if (!isTargetAndroidUser(config)) {
                return;
            }
            if (!config.enabled || isBlank(config.baseUrl) || isBlank(config.apiKey)) {
                return;
            }
            if (!bindRuntimeIdentity(config)) {
                return;
            }
            if (!ensureRegistered(config)) {
                return;
            }

            String mediaHint = MEDIA_ATTACHMENT_CONTROLLER.resolveMediaHint(
                    param.thisObject,
                    messageType,
                    msgId,
                    msgSvrId,
                    imgPath);
            MessagePayload payload = buildMessagePayload(config, talker, content, isSend, msgId, msgSvrId, createTime, messageType, mediaHint);
            postAsync(config, payload, "handle insert");
            MEDIA_RETRY_SCHEDULER.scheduleIfNeeded(
                    config != null && config.mediaUploadEnabled,
                    msgId,
                    msgSvrId,
                    talker,
                    content,
                    isSend,
                    createTime,
                    messageType,
                    mediaHint,
                    payload);
            if (msgId != null && msgId > LAST_MESSAGE_ID) {
                LAST_MESSAGE_ID = msgId;
                MESSAGE_WATERMARK_READY = true;
            }
        } catch (Throwable t) {
            log("handle insert failed: " + t);
        }
    }

    private static void post(BridgeConfig config, MessagePayload payload) throws Exception {
        postJson(config, "/webhook/lsposed/message", payload.toJson());
    }

    private static void postAsync(final BridgeConfig config, final MessagePayload payload, final String context) {
        if (config == null || payload == null) {
            return;
        }
        POST_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    post(config, payload);
                    MEDIA_RETRY_SCHEDULER.rememberUploaded(payload);
                } catch (Throwable t) {
                    log(context + " async upload failed: " + shortError(t));
                }
            }
        });
    }

    private static HookMediaRetryEnvironment mediaRetryEnvironment() {
        return HookMediaServices.retryEnvironment(
                () -> BridgeConfig.load(bridgeContext()),
                () -> LAST_DATABASE,
                HookEntry::isTargetAndroidUser,
                HookEntry::bindRuntimeIdentity,
                HookEntry::ensureRegistered,
                HookEntry::sleepQuietly,
                HookEntry::log,
                new HookMediaRetryMessageBridge(
                        MEDIA_ATTACHMENT_CONTROLLER::resolveMediaHint,
                        HookEntry::buildMessagePayload,
                        HookEntry::post));
    }

    private static void startWorker(final ClassLoader classLoader) {
        if (!WORKER_STARTED.compareAndSet(false, true)) {
            return;
        }
        startOutboxWorker(classLoader);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                runWorker(classLoader);
            }
        }, "wechat-observatory-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private static void runWorker(ClassLoader classLoader) {
        WECHAT_CLASS_LOADER = classLoader;
        while (true) {
            BridgeConfig config = BridgeConfig.load(bridgeContext());
            try {
                if (!isTargetAndroidUser(config)) {
                    logTargetUserSkip("stop worker");
                    return;
                }
                if (config.enabled && !isBlank(config.baseUrl) && !isBlank(config.apiKey)) {
                    if (!bindRuntimeIdentity(config)) {
                        if (!sleepOnce(Math.max(3000L, config.pollIntervalMs))) {
                            return;
                        }
                        continue;
                    }
                    if (!ensureRegistered(config)) {
                        if (!sleepOnce(Math.max(3000L, config.pollIntervalMs))) {
                            return;
                        }
                        continue;
                    }
                    if (!isWeChatReadyForSend(classLoader)) {
                        log("WeChat send stack not ready; skip outbox poll");
                        if (!sleepOnce(Math.max(5000L, config.pollIntervalMs))) {
                            return;
                        }
                        continue;
                    }
                    syncContactsIfDue(config);
                    pollMessagesIfDue(config);
                }
            } catch (Throwable t) {
                log("worker failed: " + t);
            }
            if (!sleepOnce(Math.max(1000L, config.pollIntervalMs))) {
                return;
            }
        }
    }

    private static void startOutboxWorker(final ClassLoader classLoader) {
        if (!OUTBOX_WORKER_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                runOutboxWorker(classLoader);
            }
        }, "wechat-observatory-outbox");
        thread.setDaemon(true);
        thread.start();
    }

    private static void runOutboxWorker(ClassLoader classLoader) {
        while (true) {
            BridgeConfig config = BridgeConfig.load(bridgeContext());
            try {
                if (!isTargetAndroidUser(config)) {
                    logTargetUserSkip("stop outbox worker");
                    return;
                }
                if (config.enabled && !isBlank(config.baseUrl) && !isBlank(config.apiKey)) {
                    if (!bindRuntimeIdentity(config)) {
                        if (!sleepOnce(Math.max(3000L, config.pollIntervalMs))) {
                            return;
                        }
                        continue;
                    }
                    if (!ensureRegistered(config)) {
                        if (!sleepOnce(Math.max(3000L, config.pollIntervalMs))) {
                            return;
                        }
                        continue;
                    }
                    if (!isWeChatReadyForSend(classLoader)) {
                        log("WeChat send stack not ready; skip outbox websocket");
                    } else if (!runOutboxWebSocket(config, classLoader)) {
                        pollOutbox(config, classLoader);
                    }
                }
            } catch (Throwable t) {
                log("outbox worker failed: " + t);
            }
            if (!sleepOnce(Math.max(1000L, config.pollIntervalMs))) {
                return;
            }
        }
    }

    private static boolean sleepOnce(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean isTargetAndroidUser(BridgeConfig config) {
        return config == null
                || config.targetAndroidUserId < 0
                || config.targetAndroidUserId == currentAndroidUserId();
    }

    private static int currentAndroidUserId() {
        int uid = android.os.Process.myUid();
        return uid < 100000 ? 0 : uid / 100000;
    }

    private static void logTargetUserSkip(String message) {
        long now = System.currentTimeMillis();
        if (now - LAST_USER_SKIP_LOG_AT < 30000L) {
            return;
        }
        LAST_USER_SKIP_LOG_AT = now;
        BridgeConfig config = BridgeConfig.load(null);
        log(message + ": currentAndroidUserId=" + currentAndroidUserId()
                + " targetAndroidUserId=" + (config == null ? -1 : config.targetAndroidUserId));
    }

    private static boolean isWeChatReadyForSend(ClassLoader classLoader) {
        try {
            classLoader = runtimeClassLoader(classLoader);
            WECHAT_CLASS_LOADER = classLoader;
            ensureWeChatRegistries(classLoader);
            if (!isStaticArraySlotReady(classLoader, "fs.g", "f283324a", "a")) {
                return readyState(false, "fs.g extension registry not initialized");
            }
            if (!isStaticBooleanFlag(classLoader, "i95.n0", "f307062f", "f")) {
                return readyState(false, "i95.n0 service manager not initialized");
            }
            return readyState(true, "ready");
        } catch (Throwable t) {
            return readyState(false, "readiness check failed: " + shortError(t));
        }
    }

    private static void ensureWeChatRegistries(ClassLoader classLoader) throws Exception {
        classLoader = runtimeClassLoader(classLoader);
        Class<?> appContext = findClass(classLoader, "com.tencent.mm.sdk.platformtools.x2");
        Object application = findFieldAny(appContext, "f210311a", "a").get(null);
        if (application == null) {
            application = currentApplication();
        }
        if (application == null) {
            log("WeChat application context is null during registry init");
            return;
        }
        if (findFieldAny(appContext, "f210311a", "a").get(null) == null) {
            findMethod(appContext, "u", Context.class).invoke(null, application);
            log("initialized WeChat MMApplicationContext from module");
        }

        Class<?> extensionRegistry = findClass(classLoader, "fs.g");
        Field registryField = findFieldAny(extensionRegistry, "f283324a", "a");
        Object registryArray = registryField.get(null);
        if (registryArray != null
                && registryArray.getClass().isArray()
                && java.lang.reflect.Array.getLength(registryArray) > 0
                && java.lang.reflect.Array.get(registryArray, 0) == null) {
            java.lang.reflect.Array.set(registryArray, 0, enumConstant(classLoader, "fs.k2", "INSTANCE"));
            findFieldAny(extensionRegistry, "f283326c", "c").set(null, application);
            findFieldAny(extensionRegistry, "f283325b", "b").set(null, enumConstant(classLoader, "com.tencent.mm.app.q0", "INSTANCE"));
            log("initialized fs.g extension registry from module");
        }

        if (!isStaticBooleanFlag(classLoader, "i95.n0", "f307062f", "f")) {
            Class<?> providerClass = findClass(classLoader, "com.tencent.mm.app.p0");
            Object provider = findFieldAny(providerClass, "f70808d", "d").get(null);
            Object y = findMethod(provider.getClass(), "b").invoke(provider);
            Method initialize = findMethod(
                    findClass(classLoader, "i95.n0"),
                    "d",
                    Application.class,
                    findClass(classLoader, "i95.y"),
                    findClass(classLoader, "k95.a"));
            initialize.invoke(null, application, y, enumConstant(classLoader, "com.tencent.mm.app.q0", "INSTANCE"));
            log("initialized i95.n0 service manager from module");
        }
    }

    private static Object enumConstant(ClassLoader classLoader, String className, String name) throws Exception {
        Class<?> enumClass = findClass(classLoader, className);
        Object[] constants = enumClass.getEnumConstants();
        if (constants != null && constants.length > 0) {
            for (Object constant : constants) {
                if (name.equals(String.valueOf(constant))) {
                    return constant;
                }
            }
            return constants[0];
        }
        Field field = findFieldAny(enumClass, name);
        return field.get(null);
    }

    private static Object currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object app = findNoArgMethod(activityThread, "currentApplication").invoke(null);
            if (app != null) {
                return app;
            }
            Object thread = findNoArgMethod(activityThread, "currentActivityThread").invoke(null);
            return thread == null ? null : findNoArgMethod(thread.getClass(), "getApplication").invoke(thread);
        } catch (Throwable t) {
            log("read current application failed: " + shortError(t));
            return null;
        }
    }

    private static Context bridgeContext() {
        Context context = APP_CONTEXT;
        if (context != null) {
            return context;
        }
        Object app = currentApplication();
        return app instanceof Context ? (Context) app : null;
    }

    private static ClassLoader runtimeClassLoader(ClassLoader fallback) {
        List<ClassLoader> candidates = new ArrayList<>();
        addClassLoader(candidates, Thread.currentThread().getContextClassLoader());
        Object app = currentApplication();
        addClassLoader(candidates, app instanceof Application ? ((Application) app).getClassLoader() : null);
        addClassLoader(candidates, app == null ? null : app.getClass().getClassLoader());
        addClassLoader(candidates, fallback);

        ClassLoader loader = null;
        for (ClassLoader candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (isPatchedWeChatLoader(candidate)) {
                loader = candidate;
                break;
            }
        }
        if (loader == null) {
            loader = firstLoadable(candidates);
        }
        if (loader == null) {
            return fallback;
        }
        String state = loader.toString();
        if (!state.equals(LAST_CLASSLOADER_STATE)) {
            LAST_CLASSLOADER_STATE = state;
            log("using WeChat runtime classLoader: " + state);
        }
        return loader;
    }

    private static void addClassLoader(List<ClassLoader> loaders, ClassLoader loader) {
        if (loader == null) {
            return;
        }
        for (ClassLoader existing : loaders) {
            if (existing == loader) {
                return;
            }
        }
        loaders.add(loader);
    }

    private static boolean isPatchedWeChatLoader(ClassLoader loader) {
        String description = String.valueOf(loader);
        return description.contains("DelegateLastClassLoader")
                || description.contains("tinker_classN")
                || description.contains("/tinker/");
    }

    private static ClassLoader firstLoadable(List<ClassLoader> candidates) {
        for (ClassLoader candidate : candidates) {
            try {
                Class.forName("w11.r0", false, candidate);
                Class.forName("i95.n0", false, candidate);
                return candidate;
            } catch (Throwable ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static boolean readyState(boolean ready, String reason) {
        String state = ready ? "ready" : reason;
        if (!state.equals(LAST_READY_STATE)) {
            LAST_READY_STATE = state;
            log("WeChat readiness: " + state);
        }
        return ready;
    }

    private static boolean isStaticArraySlotReady(ClassLoader classLoader, String className, String... fieldNames) throws Exception {
        Field field = findFieldAny(findClass(classLoader, className), fieldNames);
        Object value = field.get(null);
        if (value == null || !value.getClass().isArray() || java.lang.reflect.Array.getLength(value) == 0) {
            return false;
        }
        return java.lang.reflect.Array.get(value, 0) != null;
    }

    private static boolean isStaticBooleanFlag(ClassLoader classLoader, String className, String... fieldNames) throws Exception {
        Field field = findFieldAny(findClass(classLoader, className), fieldNames);
        Object value = field.get(null);
        if (value instanceof boolean[]) {
            boolean[] flags = (boolean[]) value;
            return flags.length > 0 && flags[0];
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    private static boolean booleanNoArg(Class<?> cls, String methodName) throws Exception {
        Object result = findNoArgMethod(cls, methodName).invoke(null);
        return result instanceof Boolean && (Boolean) result;
    }

    private static boolean bindRuntimeIdentity(BridgeConfig config) {
        String wxid = "";
        String nickname = "";
        Object db = LAST_DATABASE;
        if (db == null) {
            db = findContactDatabaseOnMainThread(config);
        }
        WeChatIdentity identity = readWeChatIdentity(db);
        wxid = identity.wxid;
        nickname = identity.nickname;
        if (isBlank(wxid)) {
            wxid = CURRENT_WXID;
        }
        if (isBlank(nickname)) {
            nickname = CURRENT_NICKNAME;
        }
        if (isBlank(wxid)) {
            log("runtime identity not ready: current wxid cannot be detected yet");
            return false;
        }
        if (!isBlank(CURRENT_WXID) && !wxid.equals(CURRENT_WXID)) {
            REGISTERED_KEY = "";
            REGISTERED_DEVICE = "";
            LAST_REGISTER_SUCCESS_AT = 0L;
            log("wechat identity changed wxid=" + redactedId(wxid));
        }
        CURRENT_WXID = wxid;
        CURRENT_NICKNAME = nickname;
        config.selfWxid = wxid;
        config.nickname = isBlank(nickname) ? wxid : nickname;
        return true;
    }

    private static WeChatIdentity readWeChatIdentity(Object db) {
        if (db == null) {
            return new WeChatIdentity("", "");
        }
        String wxid = "";
        String nickname = "";
        String[][] candidates = new String[][]{
                {"SELECT value FROM userinfo WHERE id=2 LIMIT 1", ""},
                {"SELECT value FROM userinfo WHERE id=42 LIMIT 1", ""},
                {"SELECT value FROM userinfo WHERE id=4 LIMIT 1", "nickname"},
                {"SELECT value FROM userinfo WHERE id=5 LIMIT 1", "nickname"},
                {"SELECT value FROM userinfo WHERE id=6 LIMIT 1", "nickname"}
        };
        for (String[] candidate : candidates) {
            String value = readSingleString(db, candidate[0]);
            if (isBlank(value)) {
                continue;
            }
            if ("nickname".equals(candidate[1])) {
                if (isBlank(nickname)) {
                    nickname = value;
                }
                continue;
            }
            if (looksLikeWxid(value)) {
                wxid = value;
                break;
            }
        }
        return new WeChatIdentity(wxid, nickname);
    }

    private static String readSingleString(Object db, String sql) {
        Object cursor = null;
        try {
            cursor = rawQuery(db, sql, new String[]{});
            if (cursor == null) {
                return "";
            }
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return stringColumn(cursor, 0);
            }
        } catch (Throwable ignored) {
            // WeChat database schemas vary between versions; try the next candidate.
        } finally {
            closeQuietly(cursor);
        }
        return "";
    }

    private static final class WeChatIdentity {
        final String wxid;
        final String nickname;

        WeChatIdentity(String wxid, String nickname) {
            this.wxid = wxid == null ? "" : wxid.trim();
            this.nickname = nickname == null ? "" : nickname.trim();
        }
    }

    private static boolean ensureRegistered(BridgeConfig config) throws Exception {
        if (isBlank(config.apiKey) || isBlank(config.selfWxid)) {
            return false;
        }
        String key = config.apiKey + "\n" + config.selfWxid + "\n" + config.signature;
        long now = System.currentTimeMillis();
        if (key.equals(REGISTERED_KEY) && !isBlank(REGISTERED_DEVICE)) {
            config.device = REGISTERED_DEVICE;
            if (now - LAST_REGISTER_SUCCESS_AT < 60000L) {
                return true;
            }
        }
        if (now - LAST_REGISTER_ATTEMPT_AT < 5000L) {
            return false;
        }
        LAST_REGISTER_ATTEMPT_AT = now;
        return registerModule(config, key);
    }

    private static boolean registerModule(BridgeConfig config, String key) throws Exception {
        if (isBlank(config.apiKey) || isBlank(config.selfWxid)) {
            return false;
        }
        String body = "{"
                + "\"api_key\":\"" + json(config.apiKey) + "\","
                + "\"device\":\"" + json(config.device) + "\","
                + "\"wxid\":\"" + json(config.selfWxid) + "\","
                + "\"nickname\":\"" + json(config.nickname) + "\""
                + "}";
        String response = postJson(config, "/module/register", body);
        JSONObject root = new JSONObject(response);
        JSONObject result = root.optJSONObject("result");
        String device = "";
        if (result != null) {
            JSONObject deviceObject = result.optJSONObject("device");
            if (deviceObject != null) {
                device = deviceObject.optString("name", "");
            }
            if (isBlank(device)) {
                JSONObject account = result.optJSONObject("account");
                if (account != null) {
                    device = account.optString("device", "");
                }
            }
        }
        if (isBlank(device)) {
            log("module register response missing server device");
            return false;
        }
        REGISTERED_KEY = key;
        REGISTERED_DEVICE = device;
        LAST_REGISTER_SUCCESS_AT = System.currentTimeMillis();
        config.device = device;
        log("module registered device=" + device + " wxid=" + redactedId(config.selfWxid));
        return true;
    }

    private static void syncContactsIfDue(BridgeConfig config) {
        if (config.contactSyncIntervalMs <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - LAST_CONTACT_SYNC_AT < config.contactSyncIntervalMs) {
            return;
        }
        Object db = LAST_DATABASE;
        if (db == null) {
            db = findContactDatabaseOnMainThread(config);
        }
        if (db == null) {
            if (now - LAST_CONTACT_SYNC_SKIP_LOG_AT > 60000L) {
                LAST_CONTACT_SYNC_SKIP_LOG_AT = now;
                log("contact sync skipped: WeChat database not captured yet");
            }
            return;
        }
        LAST_CONTACT_SYNC_AT = now;
        try {
            JSONArray contacts = readContacts(db, config);
            if (contacts.length() == 0) {
                log("contact sync skipped: no friend contacts read");
                return;
            }
            JSONObject body = new JSONObject();
            body.put("api_key", config.apiKey);
            body.put("device", config.device);
            body.put("wxid", config.selfWxid);
            body.put("complete", true);
            body.put("contacts", contacts);
            postJson(config, "/module/contacts/snapshot", body.toString());
            log("contact sync uploaded count=" + contacts.length()
                    + " includeChatrooms=" + config.includeChatrooms);
        } catch (Throwable t) {
            log("contact sync failed: " + shortError(t));
        }
    }

    private static void pollMessagesIfDue(BridgeConfig config) {
        long now = System.currentTimeMillis();
        if (now < MESSAGE_POLL_BACKOFF_UNTIL) {
            return;
        }
        if (now - LAST_MESSAGE_POLL_AT < Math.max(1000L, config.pollIntervalMs)) {
            return;
        }
        LAST_MESSAGE_POLL_AT = now;
        Object db = LAST_DATABASE;
        if (db == null) {
            db = findContactDatabaseOnMainThread(config);
        }
        if (db == null) {
            return;
        }
        try {
            if (!MESSAGE_WATERMARK_READY) {
                LAST_MESSAGE_ID = readMaxMessageId(db);
                MESSAGE_WATERMARK_READY = true;
                log("message poll watermark initialized msgId=" + LAST_MESSAGE_ID);
                return;
            }
            int count = pollNewMessages(db, config);
            if (count > 0) {
                log("message poll uploaded count=" + count + " lastMsgId=" + LAST_MESSAGE_ID);
            }
            MESSAGE_POLL_CONSECUTIVE_FAILS = 0;
        } catch (Throwable t) {
            handleMessagePollFailure(t);
        }
    }

    private static void handleMessagePollFailure(Throwable t) {
        long now = System.currentTimeMillis();
        MESSAGE_POLL_CONSECUTIVE_FAILS++;
        Throwable cause = rootCause(t);
        if (now - LAST_MESSAGE_POLL_FAIL_LOG_AT > 30000L || MESSAGE_POLL_CONSECUTIVE_FAILS <= 3) {
            LAST_MESSAGE_POLL_FAIL_LOG_AT = now;
            log("message poll failed count=" + MESSAGE_POLL_CONSECUTIVE_FAILS
                    + " error=" + shortError(cause));
        }
        if (MESSAGE_POLL_CONSECUTIVE_FAILS >= 3) {
            long backoffMs = Math.min(10L * 60L * 1000L, 30000L * MESSAGE_POLL_CONSECUTIVE_FAILS);
            MESSAGE_POLL_BACKOFF_UNTIL = now + backoffMs;
            log("message poll backoff seconds=" + (backoffMs / 1000L)
                    + " reason=" + shortError(cause));
        }
    }

    private static long readMaxMessageId(Object db) throws Exception {
        Object cursor = rawQuery(db, "SELECT MAX(msgId) FROM message", new String[]{});
        if (cursor == null) {
            return 0L;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return longColumn(cursor, 0);
            }
            return 0L;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static int pollNewMessages(Object db, BridgeConfig config) throws Exception {
        int queryMode = 2;
        Object cursor;
        try {
            cursor = rawQuery(db, ""
                    + "SELECT msgId,COALESCE(msgSvrId,0),talker,COALESCE(content,''),isSend,createTime,type,COALESCE(imgPath,'') "
                    + "FROM message "
                    + "WHERE msgId > ? AND talker IS NOT NULL AND talker <> '' "
                    + "ORDER BY msgId ASC "
                    + "LIMIT 50", new String[]{String.valueOf(LAST_MESSAGE_ID)});
        } catch (Throwable t) {
            try {
                queryMode = 1;
                cursor = rawQuery(db, ""
                        + "SELECT msgId,talker,COALESCE(content,''),isSend,createTime,type,COALESCE(imgPath,'') "
                        + "FROM message "
                        + "WHERE msgId > ? AND talker IS NOT NULL AND talker <> '' "
                        + "ORDER BY msgId ASC "
                        + "LIMIT 50", new String[]{String.valueOf(LAST_MESSAGE_ID)});
            } catch (Throwable ignored) {
                queryMode = 0;
                cursor = rawQuery(db, ""
                        + "SELECT msgId,talker,COALESCE(content,''),isSend,createTime,type "
                        + "FROM message "
                        + "WHERE msgId > ? AND talker IS NOT NULL AND talker <> '' "
                        + "ORDER BY msgId ASC "
                        + "LIMIT 50", new String[]{String.valueOf(LAST_MESSAGE_ID)});
            }
        }
        if (cursor == null) {
            return 0;
        }
        int count = 0;
        try {
            Method moveToNext = findNoArgMethod(cursor.getClass(), "moveToNext");
            while (Boolean.TRUE.equals(moveToNext.invoke(cursor))) {
                int column = 0;
                long msgId = longColumn(cursor, column++);
                if (msgId <= LAST_MESSAGE_ID) {
                    continue;
                }
                LAST_MESSAGE_ID = msgId;

                long msgSvrId = queryMode == 2 ? longColumn(cursor, column++) : 0L;
                String talker = stringColumn(cursor, column++);
                String content = stringColumn(cursor, column++);
                int isSend = intColumn(cursor, column++);
                long createTime = normalizeCreateTime(longColumn(cursor, column++));
                int type = intColumn(cursor, column++);
                String imgPath = queryMode >= 1 ? stringColumn(cursor, column) : "";
                if (!shouldReportMessage(talker, content, type)) {
                    continue;
                }

                int messageType = type <= 0 ? 1 : type;
                Long serverId = msgSvrId > 0L ? Long.valueOf(msgSvrId) : null;
                String mediaHint = MEDIA_ATTACHMENT_CONTROLLER.resolveMediaHint(
                        db,
                        messageType,
                        Long.valueOf(msgId),
                        serverId,
                        imgPath);
                MessagePayload payload = buildMessagePayload(config, talker, content, isSend == 1 ? Integer.valueOf(1) : Integer.valueOf(0), Long.valueOf(msgId), serverId, Long.valueOf(createTime), messageType, mediaHint);
                post(config, payload);
                MEDIA_RETRY_SCHEDULER.rememberUploaded(payload);
                MEDIA_RETRY_SCHEDULER.scheduleIfNeeded(
                        config != null && config.mediaUploadEnabled,
                        Long.valueOf(msgId),
                        serverId,
                        talker,
                        content,
                        isSend == 1 ? Integer.valueOf(1) : Integer.valueOf(0),
                        Long.valueOf(createTime),
                        messageType,
                        mediaHint,
                        payload);
                count++;
            }
        } finally {
            closeQuietly(cursor);
        }
        return count;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        int depth = 0;
        while (current != null && current.getCause() != null && depth < 8) {
            current = current.getCause();
            depth++;
        }
        return current == null ? t : current;
    }

    private static boolean shouldReportMessage(String talker, String content, int type) {
        if (isBlank(talker)) {
            return false;
        }
        String normalizedTalker = talker.trim().toLowerCase(Locale.US);
        if (normalizedTalker.startsWith("gh_") || isSystemContact(normalizedTalker)) {
            return false;
        }
        if (type == 1) {
            if (isBlank(content)) {
                return false;
            }
            String text = normalizeMessageText(talker, content);
            return !looksLikeXmlPayload(text);
        }
        return type > 0;
    }

    private static MessagePayload buildMessagePayload(BridgeConfig config, String talker, String content, Integer isSend, Long msgId, Long msgSvrId, Long createTime, int type, String mediaHint) {
        MessagePayload payload = new MessagePayload();
        String normalizedTalker = talker == null ? "" : talker.trim();
        boolean chatroom = isChatroomTalker(normalizedTalker);
        String text = displayMessageText(normalizedTalker, content, type);
        String normalizedContent = normalizeMessageContent(normalizedTalker, content, type);
        payload.id = msgId == null || msgId <= 0 ? "" : String.valueOf(msgId);
        payload.eventId = msgId == null ? 0L : msgId;
        payload.chatRecordId = msgId == null ? 0L : msgId;
        payload.apiKey = config.apiKey;
        payload.device = config.device;
        payload.chatId = normalizedTalker;
        payload.chatKind = chatroom ? "room" : "direct";
        payload.roomId = chatroom ? normalizedTalker : "";
        payload.text = text;
        payload.messageType = type;
        payload.rawXml = rawXmlPayload(normalizedContent, type);
        payload.createTime = normalizeCreateTime(createTime);
        MEDIA_ATTACHMENT_CONTROLLER.attachMedia(
                LAST_DATABASE,
                config,
                payload,
                isSend,
                type,
                mediaHint,
                msgId,
                msgSvrId,
                payload.createTime,
                normalizedTalker,
                content);

        boolean sent = isSend != null && isSend == 1;
        if (sent) {
            payload.direction = "sent";
            payload.from = config.selfWxid;
            payload.to = normalizedTalker;
            payload.sender = chatroom ? config.selfWxid : "";
        } else {
            payload.direction = "recv";
            payload.from = normalizedTalker;
            payload.to = config.selfWxid;
            payload.sender = chatroom ? extractChatroomSender(content) : "";
        }
        return payload;
    }

    private static ClassLoader wechatRuntimeClassLoader() {
        ClassLoader current = WECHAT_CLASS_LOADER == null ? HookEntry.class.getClassLoader() : WECHAT_CLASS_LOADER;
        return runtimeClassLoader(current);
    }

    private static File wechatAppRoot() {
        Context context = APP_CONTEXT;
        if (context == null) {
            Object app = currentApplication();
            if (app instanceof Context) {
                context = (Context) app;
            }
        }
        return context == null || context.getFilesDir() == null ? null : context.getFilesDir().getParentFile();
    }

    private static String displayMessageText(String talker, String content, int type) {
        if (type == 1) {
            return normalizeMessageText(talker, content);
        }
        String label = messageTypeLabel(type);
        String detail = nonTextMessageDetail(type, normalizeMessageContent(talker, content, type));
        if (isBlank(detail)) {
            return label;
        }
        return label + " " + detail;
    }

    private static String nonTextMessageDetail(int type, String content) {
        String detail = firstNonBlank(
                extractXmlField(content, "title"),
                extractXmlField(content, "des"),
                extractXmlField(content, "filename"),
                extractXmlField(content, "appname"),
                extractXmlField(content, "displayname"));
        if (!isBlank(detail)) {
            return compactDisplayText(detail, 80);
        }
        if (!looksLikeXmlPayload(content)) {
            return compactDisplayText(content, 80);
        }
        if (type == 10000) {
            return "";
        }
        return "";
    }

    private static String messageTypeLabel(int type) {
        switch (type) {
            case 1:
                return "[文本]";
            case 3:
                return "[图片]";
            case 34:
                return "[语音]";
            case 37:
                return "[好友请求]";
            case 43:
                return "[视频]";
            case 47:
                return "[表情]";
            case 48:
                return "[位置]";
            case 49:
                return "[链接/文件]";
            case MESSAGE_TYPE_FILE_TRANSFER:
                return "[文件]";
            case 50:
                return "[通话]";
            case 62:
                return "[小视频]";
            case 10000:
                return "[系统消息]";
            default:
                return "[消息 " + type + "]";
        }
    }

    private static boolean looksLikeXmlPayload(String value) {
        if (isBlank(value)) {
            return false;
        }
        String text = value.trim().toLowerCase(Locale.US);
        return text.startsWith("<msg")
                || text.startsWith("<?xml")
                || text.startsWith("<sysmsg")
                || text.startsWith("<appmsg")
                || text.startsWith("<emoji")
                || text.startsWith("<template>");
    }

    private static String rawXmlPayload(String content, int type) {
        if (type != 49 && type != 48 && type != 47 && type != 10000) {
            return "";
        }
        if (!looksLikeXmlPayload(content)) {
            return "";
        }
        return content.trim();
    }

    private static boolean containsRevokePayload(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.US);
        return normalized.contains("revokemsg") || normalized.contains("msgsvrcancel");
    }

    private static String extractXmlField(String xml, String field) {
        return cleanDisplayText(extractXmlFieldRaw(xml, field), 120);
    }

    private static String extractXmlFieldRaw(String xml, String field) {
        if (isBlank(xml) || isBlank(field)) {
            return "";
        }
        String text = xml.trim();
        String lower = text.toLowerCase(Locale.US);
        String openTag = "<" + field.toLowerCase(Locale.US) + ">";
        int start = lower.indexOf(openTag);
        if (start >= 0) {
            start += openTag.length();
        } else {
            String prefix = "<" + field.toLowerCase(Locale.US);
            start = lower.indexOf(prefix);
            if (start < 0) {
                return "";
            }
            start = lower.indexOf(">", start);
            if (start < 0) {
                return "";
            }
            start++;
        }
        String closeTag = "</" + field.toLowerCase(Locale.US) + ">";
        int end = lower.indexOf(closeTag, start);
        if (end <= start) {
            return "";
        }
        return text.substring(start, end).trim();
    }

    private static String cleanDisplayText(String text, int maxLen) {
        if (isBlank(text)) {
            return "";
        }
        String value = text.trim()
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ");
        if (value.startsWith("<![CDATA[") && value.endsWith("]]>") && value.length() >= 12) {
            value = value.substring(9, value.length() - 3).trim();
        }
        value = value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        while (value.contains("  ")) {
            value = value.replace("  ", " ");
        }
        if (maxLen > 0 && value.length() > maxLen) {
            value = value.substring(0, maxLen).trim();
        }
        return value;
    }

    private static String compactDisplayText(String text, int maxLen) {
        return cleanDisplayText(text, maxLen);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static long firstPositiveLong(long... values) {
        if (values == null) {
            return 0L;
        }
        for (long value : values) {
            if (value > 0L) {
                return value;
            }
        }
        return 0L;
    }

    private static long[] uniquePositiveLongs(long... values) {
        if (values == null || values.length == 0) {
            return new long[0];
        }
        long[] tmp = new long[values.length];
        int count = 0;
        for (long value : values) {
            if (value <= 0L) {
                continue;
            }
            boolean exists = false;
            for (int i = 0; i < count; i++) {
                if (tmp[i] == value) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                tmp[count++] = value;
            }
        }
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            result[i] = tmp[i];
        }
        return result;
    }

    private static boolean isLongParameter(Class<?> type) {
        return type == long.class || type == Long.class;
    }

    private static int firstPositiveInt(int... values) {
        if (values == null) {
            return 0;
        }
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private static double firstFiniteDouble(double... values) {
        if (values == null) {
            return Double.NaN;
        }
        for (double value : values) {
            if (isFinite(value)) {
                return value;
            }
        }
        return Double.NaN;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean containsInt(int[] values, int expected) {
        if (values == null) {
            return false;
        }
        for (int value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChatroomTalker(String talker) {
        return !isBlank(talker) && talker.toLowerCase(Locale.US).endsWith("@chatroom");
    }

    private static boolean sameTalker(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return !a.isEmpty() && a.equals(b);
    }

    private static String normalizeMessageText(String talker, String content) {
        if (isBlank(content)) {
            return "";
        }
        String text = content.trim();
        if (!isChatroomTalker(talker)) {
            return text;
        }
        int newline = text.indexOf(":\n");
        if (newline <= 0) {
            return text;
        }
        String prefix = text.substring(0, newline).trim();
        String body = text.substring(newline + 2).trim();
        if (looksLikeWxid(prefix) && !isBlank(body)) {
            return body;
        }
        return text;
    }

    private static String normalizeMessageContent(String talker, String content, int type) {
        String text = normalizeMessageText(talker, content);
        if (type != 47) {
            return text;
        }
        String embedded = embeddedXmlPayload(text);
        return isBlank(embedded) ? text : embedded;
    }

    private static String embeddedXmlPayload(String value) {
        int start = embeddedXmlStart(value);
        if (start < 0) {
            return "";
        }
        return value.substring(start).trim();
    }

    private static int embeddedXmlStart(String value) {
        if (isBlank(value)) {
            return -1;
        }
        String lower = value.toLowerCase(Locale.US);
        int best = -1;
        String[] markers = new String[]{"<msg", "<emoji", "<?xml", "<sysmsg", "<appmsg"};
        for (String marker : markers) {
            int index = lower.indexOf(marker);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static String extractChatroomSender(String content) {
        if (isBlank(content)) {
            return "";
        }
        String text = content.trim();
        int newline = text.indexOf(":\n");
        if (newline <= 0) {
            return "";
        }
        String prefix = text.substring(0, newline).trim();
        return looksLikeWxid(prefix) ? prefix : "";
    }

    private static boolean looksLikeWxid(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.length() < 3 || normalized.indexOf(' ') >= 0 || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            return false;
        }
        return normalized.startsWith("wxid_")
                || normalized.startsWith("gh_")
                || normalized.contains("@chatroom")
                || normalized.contains("_");
    }

    private static JSONArray readContacts(Object db, BridgeConfig config) throws Exception {
        int limit = config.contactSyncLimit <= 0 ? 1000 : Math.min(config.contactSyncLimit, 10000);
        Object cursor = rawQuery(db, ""
                + "SELECT username,nickname,conRemark,alias,type,verifyFlag "
                + "FROM rcontact "
                + "WHERE username IS NOT NULL AND username <> '' "
                + "LIMIT ?", new String[]{String.valueOf(limit)});
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        if (cursor == null) {
            return out;
        }
        try {
            Method moveToNext = findNoArgMethod(cursor.getClass(), "moveToNext");
            while (Boolean.TRUE.equals(moveToNext.invoke(cursor))) {
                String wxid = stringColumn(cursor, 0);
                int type = intColumn(cursor, 4);
                boolean chatroom = wxid.toLowerCase(Locale.US).endsWith("@chatroom");
                if (!shouldIncludeContact(wxid, type, chatroom, config)) {
                    continue;
                }
                String normalized = wxid.trim().toLowerCase(Locale.US);
                if (!seen.add(normalized)) {
                    continue;
                }
                JSONObject contact = new JSONObject();
                contact.put("wxid", wxid);
                contact.put("nickname", contactNickname(wxid, stringColumn(cursor, 1)));
                contact.put("remark", stringColumn(cursor, 2));
                contact.put("alias", stringColumn(cursor, 3));
                contact.put("type", type);
                contact.put("verify_flag", intColumn(cursor, 5));
                contact.put("chatroom", chatroom);
                contact.put("deleted", false);
                out.put(contact);
            }
        } finally {
            closeQuietly(cursor);
        }
        if (config.includeChatrooms && out.length() < limit) {
            int added = appendChatroomContacts(db, out, seen, limit - out.length());
            if (added == 0 && out.length() < limit) {
                appendActiveDatabaseChatrooms(out, seen, limit - out.length());
            }
        }
        return out;
    }

    private static int appendChatroomContacts(Object db, JSONArray out, Set<String> seen, int limit) {
        if (limit <= 0) {
            return 0;
        }
        Object cursor = null;
        try {
            cursor = rawQuery(db, ""
                    + "SELECT chatroomname,displayname "
                    + "FROM chatroom "
                    + "WHERE chatroomname IS NOT NULL AND chatroomname <> '' "
                    + "LIMIT ?", new String[]{String.valueOf(limit)});
        } catch (Throwable first) {
            try {
                cursor = rawQuery(db, ""
                        + "SELECT chatroomname,'' "
                        + "FROM chatroom "
                        + "WHERE chatroomname IS NOT NULL AND chatroomname <> '' "
                        + "LIMIT ?", new String[]{String.valueOf(limit)});
            } catch (Throwable second) {
                log("chatroom contact scan skipped: " + shortError(second));
                return 0;
            }
        }
        if (cursor == null) {
            return 0;
        }
        int added = 0;
        try {
            Method moveToNext = findNoArgMethod(cursor.getClass(), "moveToNext");
            while (Boolean.TRUE.equals(moveToNext.invoke(cursor))) {
                String wxid = stringColumn(cursor, 0);
                if (isBlank(wxid) || !wxid.toLowerCase(Locale.US).endsWith("@chatroom")) {
                    continue;
                }
                String normalized = wxid.trim().toLowerCase(Locale.US);
                if (!seen.add(normalized)) {
                    continue;
                }
                String nickname = stringColumn(cursor, 1);
                JSONObject contact = new JSONObject();
                contact.put("wxid", wxid);
                contact.put("nickname", isBlank(nickname) ? wxid : nickname);
                contact.put("remark", "");
                contact.put("alias", "");
                contact.put("type", 0);
                contact.put("verify_flag", 0);
                contact.put("chatroom", true);
                contact.put("deleted", false);
                out.put(contact);
                added++;
            }
        } catch (Throwable t) {
            log("chatroom contact scan failed: " + shortError(t));
        } finally {
            closeQuietly(cursor);
        }
        if (added > 0) {
            log("chatroom contact scan added=" + added);
        }
        return added;
    }

    private static void appendActiveDatabaseChatrooms(JSONArray out, Set<String> seen, int limit) {
        if (limit <= 0) {
            return;
        }
        try {
            ClassLoader classLoader = WECHAT_CLASS_LOADER;
            if (classLoader == null) {
                classLoader = runtimeClassLoader(HookEntry.class.getClassLoader());
            } else {
                classLoader = runtimeClassLoader(classLoader);
            }
            Class<?> dbClass = findClass(classLoader, "com.tencent.wcdb.database.SQLiteDatabase");
            Field activeField = findField(dbClass, "sActiveDatabases");
            Object active = activeField.get(null);
            if (!(active instanceof Map)) {
                return;
            }
            Object[] databases;
            synchronized (active) {
                databases = ((Map<?, ?>) active).keySet().toArray();
            }
            int total = 0;
            for (Object candidate : databases) {
                if (candidate == null || out.length() >= limit) {
                    continue;
                }
                int added = appendChatroomContacts(candidate, out, seen, limit - out.length());
                if (added > 0) {
                    total += added;
                    log("chatroom active database path=" + databasePath(candidate) + " added=" + added);
                }
            }
            if (total == 0) {
                log("chatroom active database scan found no rows dbCount=" + databases.length);
            }
        } catch (Throwable t) {
            log("chatroom active database scan failed: " + shortError(t));
        }
    }

    private static Object findContactDatabase(BridgeConfig config) {
        try {
            ClassLoader classLoader = WECHAT_CLASS_LOADER;
            if (classLoader == null) {
                classLoader = runtimeClassLoader(HookEntry.class.getClassLoader());
            } else {
                classLoader = runtimeClassLoader(classLoader);
            }
            Class<?> dbClass = findClass(classLoader, "com.tencent.wcdb.database.SQLiteDatabase");
            Field activeField = findField(dbClass, "sActiveDatabases");
            Object active = activeField.get(null);
            if (!(active instanceof Map)) {
                return null;
            }
            Object[] databases;
            synchronized (active) {
                databases = ((Map<?, ?>) active).keySet().toArray();
            }
            boolean verboseScanLog = shouldLogContactScan();
            if (verboseScanLog) {
                log("contact database scan active db count=" + databases.length);
            }
            for (Object db : databases) {
                if (db == null) {
                    continue;
                }
                try {
                    JSONArray contacts = readContacts(db, config);
                    if (contacts.length() > 0) {
                        if (!hasMessageTable(db)) {
                            if (LAST_DATABASE == db) {
                                LAST_DATABASE = null;
                            }
                            if (verboseScanLog) {
                                log("contact database candidate has no message table path=" + databasePath(db));
                            }
                            continue;
                        }
                        LAST_DATABASE = db;
                        log("captured WeChat database from active set path=" + databasePath(db)
                                + " contacts=" + contacts.length());
                        return db;
                    }
                    if (verboseScanLog) {
                        log("contact database candidate empty path=" + databasePath(db));
                    }
                } catch (Throwable ignored) {
                    if (verboseScanLog) {
                        log("contact database candidate failed path=" + databasePath(db)
                                + " error=" + shortError(ignored));
                    }
                }
            }
        } catch (Throwable t) {
            log("contact database scan failed: " + shortError(t));
        }
        return null;
    }

    private static boolean shouldLogContactScan() {
        long now = System.currentTimeMillis();
        if (now - LAST_CONTACT_SCAN_LOG_AT < 60000L) {
            return false;
        }
        LAST_CONTACT_SCAN_LOG_AT = now;
        return true;
    }

    private static Object findContactDatabaseOnMainThread(final BridgeConfig config) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return findContactDatabase(config);
        }
        FutureTask<Object> task = new FutureTask<>(new Callable<Object>() {
            @Override
            public Object call() {
                return findContactDatabase(config);
            }
        });
        new Handler(Looper.getMainLooper()).post(task);
        try {
            return task.get();
        } catch (Throwable t) {
            log("contact database main-thread scan failed: " + shortError(t));
            return null;
        }
    }

    private static String databasePath(Object db) {
        try {
            Object value = findNoArgMethod(db.getClass(), "getPath").invoke(db);
            return value == null ? "<unknown>" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "<unknown>";
        }
    }

    private static Object rawQuery(Object db, String sql, String[] args) throws Exception {
        try {
            Method rawQuery = findMethod(db.getClass(), "rawQuery", String.class, Object[].class);
            return rawQuery.invoke(db, sql, (Object) args);
        } catch (NoSuchMethodException ignored) {
            try {
                Method rawQuery = findMethod(db.getClass(), "rawQuery", String.class, Object[].class, findClass(db.getClass().getClassLoader(), "com.tencent.wcdb.support.CancellationSignal"));
                return rawQuery.invoke(db, sql, (Object) args, null);
            } catch (NoSuchMethodException ignoredObjectOverload) {
                try {
                    Method rawQuery = findMethod(db.getClass(), "rawQuery", String.class, String[].class);
                    return rawQuery.invoke(db, sql, (Object) args);
                } catch (NoSuchMethodException ignoredStringOverload) {
                    Method rawQuery = findMethod(db.getClass(), "rawQuery", String.class, String[].class, int.class);
                    return rawQuery.invoke(db, sql, (Object) args, 0);
                }
            }
        }
    }

    private static boolean shouldIncludeContact(String wxid, int type, boolean chatroom, BridgeConfig config) {
        if (isBlank(wxid)) {
            return false;
        }
        String normalized = wxid.trim().toLowerCase(Locale.US);
        if (!isBlank(config.selfWxid) && normalized.equals(config.selfWxid.trim().toLowerCase(Locale.US))) {
            return false;
        }
        if (isFileHelperContact(normalized)) {
            return true;
        }
        if (chatroom) {
            return config.includeChatrooms;
        }
        if ((type & 1) == 0) {
            return false;
        }
        if (normalized.startsWith("gh_")) {
            return false;
        }
        return !isSystemContact(normalized);
    }

    private static String contactNickname(String wxid, String nickname) {
        if (isFileHelperContact(wxid) && isBlank(nickname)) {
            return "文件传输助手";
        }
        return nickname;
    }

    private static boolean isFileHelperContact(String wxid) {
        return "filehelper".equals(wxid == null ? "" : wxid.trim().toLowerCase(Locale.US));
    }

    private static boolean isSystemContact(String wxid) {
        return "weixin".equals(wxid)
                || "medianote".equals(wxid)
                || "fmessage".equals(wxid)
                || "qmessage".equals(wxid)
                || "tmessage".equals(wxid)
                || "qqmail".equals(wxid)
                || "qqsync".equals(wxid)
                || "lbsapp".equals(wxid)
                || "shakeapp".equals(wxid)
                || "feedsapp".equals(wxid)
                || "masssendapp".equals(wxid)
                || "newsapp".equals(wxid)
                || "blogapp".equals(wxid)
                || "officialaccounts".equals(wxid)
                || "helper_entry".equals(wxid)
                || "voiceinputapp".equals(wxid)
                || "voicevoipapp".equals(wxid)
                || "weixinreminder".equals(wxid)
                || "notifymessage".equals(wxid);
    }

    private static String stringColumn(Object cursor, int index) {
        try {
            Object value = findMethod(cursor.getClass(), "getString", int.class).invoke(cursor, index);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable t) {
            return "";
        }
    }

    private static int intColumn(Object cursor, int index) {
        try {
            Object value = findMethod(cursor.getClass(), "getInt", int.class).invoke(cursor, index);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long longColumn(Object cursor, int index) {
        try {
            Object value = findMethod(cursor.getClass(), "getLong", int.class).invoke(cursor, index);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static void closeQuietly(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            findNoArgMethod(closeable.getClass(), "close").invoke(closeable);
        } catch (Throwable ignored) {
            // Ignore close failures from WeChat cursor implementations.
        }
    }

    private static void pollOutbox(BridgeConfig config, ClassLoader classLoader) throws Exception {
        String body = "{"
                + "\"api_key\":\"" + json(config.apiKey) + "\","
                + "\"device\":\"" + json(config.device) + "\","
                + "\"wxid\":\"" + json(config.selfWxid) + "\","
                + "\"limit\":" + config.pollLimit
                + "}";
        String response = postJson(config, "/module/outbox/poll", body);
        JSONObject root = new JSONObject(response);
        JSONArray items = root.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return;
        }

        handleOutboxItems(items, classLoader, config, new OutboxAckSink() {
            @Override
            public void ack(JSONArray ackItems) throws Exception {
                postOutboxAck(config, ackItems);
            }
        });
    }

    private static void postOutboxAck(BridgeConfig config, JSONArray ackItems) throws Exception {
        if (ackItems == null || ackItems.length() == 0) {
            return;
        }
        JSONObject ackBody = new JSONObject();
        ackBody.put("api_key", config.apiKey);
        ackBody.put("device", config.device);
        ackBody.put("wxid", config.selfWxid);
        ackBody.put("items", ackItems);
        postJson(config, "/module/outbox/ack", ackBody.toString());
        log("outbox ack posted items=" + ackItems.length());
    }

    private static boolean runOutboxWebSocket(BridgeConfig config, ClassLoader classLoader) {
        if (!supportsPlainHttp(config)) {
            return false;
        }
        try {
            websocketLoop(config, classLoader);
        } catch (Throwable t) {
            logWebSocketFailure("outbox websocket unavailable: " + shortError(t));
        }
        return false;
    }

    private static void websocketLoop(BridgeConfig config, ClassLoader classLoader) throws Exception {
        URL base = new URL(trimRight(config.baseUrl, "/"));
        int port = base.getPort() > 0 ? base.getPort() : 80;
        String host = base.getHost();
        String hostHeader = base.getPort() > 0 ? host + ":" + port : host;
        String path = trimRight(base.getPath(), "/") + "/module/outbox/ws"
                + "?api_key=" + urlEncode(config.apiKey)
                + "&device=" + urlEncode(config.device)
                + "&wxid=" + urlEncode(config.selfWxid);
        if (path.startsWith("//")) {
            path = path.substring(1);
        }
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        String key = Base64.encodeToString(nonce, Base64.NO_WRAP);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(30000);
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "\r\n";
            output.write(request.getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            verifyWebSocketHandshake(input, key);
            log("outbox websocket connected host=" + hostHeader + " device=" + config.device);
            writeWebSocketText(output, "{\"type\":\"poll\"}");

            while (true) {
                if (configChanged(config)) {
                    log("outbox websocket config changed; reconnect");
                    return;
                }
                WebSocketFrame frame = readWebSocketFrame(input);
                if (frame.opcode == 0x8) {
                    return;
                }
                if (frame.opcode == 0x9) {
                    writeWebSocketFrame(output, 0xA, frame.payload);
                    continue;
                }
                if (frame.opcode == 0xA) {
                    continue;
                }
                if (frame.opcode != 0x1) {
                    continue;
                }
                JSONObject root = new JSONObject(new String(frame.payload, StandardCharsets.UTF_8));
                String type = root.optString("type", "");
                if ("outbox".equals(type)) {
                    handleOutboxItems(root.optJSONArray("items"), classLoader, config, new OutboxAckSink() {
                        @Override
                        public void ack(JSONArray ackItems) throws Exception {
                            writeOutboxAck(output, config, ackItems);
                        }
                    });
                } else if ("ready".equals(type)) {
                    log("outbox websocket ready");
                } else if ("error".equals(type)) {
                    log("outbox websocket server error: " + root.optString("error", ""));
                }
            }
        }
    }

    private static void writeOutboxAck(OutputStream output, BridgeConfig config, JSONArray ackItems) throws Exception {
        if (ackItems == null || ackItems.length() == 0) {
            return;
        }
        JSONObject ackBody = new JSONObject();
        ackBody.put("type", "ack");
        JSONObject ack = new JSONObject();
        ack.put("api_key", config.apiKey);
        ack.put("device", config.device);
        ack.put("wxid", config.selfWxid);
        ack.put("items", ackItems);
        ackBody.put("ack", ack);
        writeWebSocketText(output, ackBody.toString());
        log("outbox websocket ack posted items=" + ackItems.length());
    }

    private static boolean configChanged(BridgeConfig config) {
        BridgeConfig latest = BridgeConfig.load(bridgeContext());
        return !String.valueOf(config.signature).equals(String.valueOf(latest.signature));
    }

    private static JSONArray handleOutboxItems(JSONArray items, ClassLoader classLoader, BridgeConfig config) throws Exception {
        return handleOutboxItems(items, classLoader, config, null);
    }

    private static JSONArray handleOutboxItems(
            JSONArray items,
            ClassLoader classLoader,
            BridgeConfig config,
            OutboxAckSink ackSink) throws Exception {
        List<OutboxWorkItem> workItems = outboxWorkItems(items);
        JSONArray ackItems = new JSONArray();
        if (workItems.isEmpty()) {
            return ackItems;
        }

        int parallelism = normalizedOutboxParallelism(config.outboxParallelism, workItems.size());
        List<OutboxAckResult> results = parallelism <= 1
                ? handleOutboxWorkItemsSerial(workItems, classLoader, config, ackSink)
                : handleOutboxWorkItemsParallel(workItems, classLoader, config, parallelism, ackSink);
        Collections.sort(results, new Comparator<OutboxAckResult>() {
            @Override
            public int compare(OutboxAckResult left, OutboxAckResult right) {
                return left.index - right.index;
            }
        });
        for (OutboxAckResult result : results) {
            ackItems.put(result.ack);
        }
        return ackItems;
    }

    private static List<OutboxWorkItem> outboxWorkItems(JSONArray items) {
        List<OutboxWorkItem> workItems = new ArrayList<>();
        if (items == null) {
            return workItems;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            long id = item.optLong("id", 0);
            if (id <= 0) {
                continue;
            }
            workItems.add(new OutboxWorkItem(i, id, item));
        }
        return workItems;
    }

    private static List<OutboxAckResult> handleOutboxWorkItemsSerial(
            List<OutboxWorkItem> workItems,
            ClassLoader classLoader,
            BridgeConfig config,
            OutboxAckSink ackSink) throws Exception {
        List<OutboxAckResult> results = new ArrayList<>();
        for (OutboxWorkItem workItem : workItems) {
            OutboxAckResult result = handleOutboxWorkItem(workItem, classLoader, config);
            if (ackSink == null) {
                results.add(result);
            } else {
                JSONArray ackItems = new JSONArray();
                ackItems.put(result.ack);
                ackSink.ack(ackItems);
            }
        }
        return results;
    }

    private static List<OutboxAckResult> handleOutboxWorkItemsParallel(
            List<OutboxWorkItem> workItems,
            final ClassLoader classLoader,
            final BridgeConfig config,
            int parallelism,
            final OutboxAckSink ackSink) throws Exception {
        Map<String, List<OutboxWorkItem>> lanes = new LinkedHashMap<>();
        for (OutboxWorkItem workItem : workItems) {
            String laneKey = outboxLaneKey(workItem.item);
            List<OutboxWorkItem> laneItems = lanes.get(laneKey);
            if (laneItems == null) {
                laneItems = new ArrayList<>();
                lanes.put(laneKey, laneItems);
            }
            laneItems.add(workItem);
        }

        int threadCount = Math.min(parallelism, lanes.size());
        if (threadCount <= 1) {
            return handleOutboxWorkItemsSerial(workItems, classLoader, config, ackSink);
        }
        log("outbox parallel dispatch items=" + workItems.size()
                + " lanes=" + lanes.size()
                + " parallelism=" + threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            private int index = 1;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "wechat-observatory-outbox-send-" + index++);
                thread.setDaemon(true);
                return thread;
            }
        });
        CompletionService<List<OutboxAckResult>> completionService = new ExecutorCompletionService<>(executor);
        int submitted = 0;
        try {
            for (final List<OutboxWorkItem> laneItems : lanes.values()) {
                completionService.submit(new Callable<List<OutboxAckResult>>() {
                    @Override
                    public List<OutboxAckResult> call() throws Exception {
                        return handleOutboxWorkItemsSerial(laneItems, classLoader, config, null);
                    }
                });
                submitted++;
            }

            List<OutboxAckResult> results = new ArrayList<>();
            for (int i = 0; i < submitted; i++) {
                try {
                    List<OutboxAckResult> laneResults = completionService.take().get();
                    if (ackSink == null) {
                        results.addAll(laneResults);
                    } else {
                        JSONArray ackItems = new JSONArray();
                        for (OutboxAckResult result : laneResults) {
                            ackItems.put(result.ack);
                        }
                        ackSink.ack(ackItems);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    throw new Exception("outbox parallel lane failed: " + shortError(cause), cause);
                }
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private static OutboxAckResult handleOutboxWorkItem(
            OutboxWorkItem workItem,
            ClassLoader classLoader,
            BridgeConfig config) throws Exception {
        SendResult result;
        try {
            result = executeOutboxItem(workItem.item, classLoader, config);
        } catch (Throwable t) {
            result = SendResult.failed("outbox item failed: " + shortError(t));
        }
        return new OutboxAckResult(workItem.index, outboxAck(workItem.id, result));
    }

    private static SendResult executeOutboxItem(JSONObject item, ClassLoader classLoader, BridgeConfig config) {
        String wxid = item.optString("wxid", "");
        String text = item.optString("text", "");
        String kind = item.optString("kind", "");
        if (isBlank(kind)) {
            kind = isBlank(item.optString("media_kind", "")) ? "text" : item.optString("media_kind", "");
        }
        kind = kind.trim().toLowerCase(Locale.US);
        if (isBlank(wxid)) {
            return SendResult.failed("wxid is required");
        } else if ("text".equals(kind)) {
            return isBlank(text)
                    ? SendResult.failed("text is required")
                    : sendText(config, classLoader, wxid, text);
        } else if ("image".equals(kind)) {
            return sendImageAction(config, classLoader, wxid, item);
        } else if ("video".equals(kind)) {
            return sendVideoAction(config, classLoader, wxid, item);
        } else if ("voice".equals(kind)) {
            return sendVoiceAction(config, classLoader, wxid, item);
        } else if ("file".equals(kind)) {
            return sendFileAction(config, classLoader, wxid, item);
        } else if ("emoji".equals(kind)) {
            return sendEmojiAction(config, classLoader, wxid, item);
        } else if ("location".equals(kind)) {
            return sendLocationAction(config, classLoader, wxid, text, item);
        } else if ("quote".equals(kind)) {
            return sendQuoteAction(config, classLoader, wxid, text, item);
        } else if ("link".equals(kind)) {
            return sendLinkAction(config, classLoader, wxid, text, item);
        } else if ("mini_program".equals(kind)) {
            return sendMiniProgramAction(config, classLoader, wxid, text, item);
        } else if ("chat_history".equals(kind)) {
            return sendChatHistoryAction(config, classLoader, wxid, text, item);
        } else if ("revoke".equals(kind)) {
            return sendRevokeAction(config, classLoader, wxid, item);
        }
        return SendResult.failed("unsupported outbox kind: " + kind);
    }

    private static int normalizedOutboxParallelism(int configured, int itemCount) {
        if (itemCount <= 1) {
            return 1;
        }
        int value = configured;
        if (value < 1) {
            value = 1;
        } else if (value > 8) {
            value = 8;
        }
        return Math.min(value, itemCount);
    }

    private static String outboxLaneKey(JSONObject item) {
        String wxid = item.optString("wxid", "").trim();
        if (!isBlank(wxid)) {
            return wxid;
        }
        return "outbox:" + item.optLong("id", 0L);
    }

    private static final class OutboxWorkItem {
        final int index;
        final long id;
        final JSONObject item;

        OutboxWorkItem(int index, long id, JSONObject item) {
            this.index = index;
            this.id = id;
            this.item = item;
        }
    }

    private static final class OutboxAckResult {
        final int index;
        final JSONObject ack;

        OutboxAckResult(int index, JSONObject ack) {
            this.index = index;
            this.ack = ack;
        }
    }

    private interface OutboxAckSink {
        void ack(JSONArray ackItems) throws Exception;
    }

    private static JSONObject outboxAck(long id, SendResult result) throws Exception {
        JSONObject ack = new JSONObject();
        ack.put("id", id);
        ack.put("status", result.ok ? "sent" : "failed");
        if (result.chatRecordId > 0) {
            ack.put("chat_record_id", result.chatRecordId);
        }
        if (!result.ok) {
            ack.put("error", result.error);
        }
        return ack;
    }

    private static SendResult sendImageAction(BridgeConfig config, ClassLoader classLoader, String wxid, JSONObject item) {
        return HookOutboxMediaActionRunner.run(
                "image",
                () -> outboxMediaFilePreparer(config).prepare(item, "image", false),
                media -> sendImage(config, classLoader, wxid, media.file, item.optString("text", "")));
    }

    private static SendResult sendVideoAction(BridgeConfig config, ClassLoader classLoader, String wxid, JSONObject item) {
        return HookOutboxMediaActionRunner.run(
                "video",
                () -> outboxMediaFilePreparer(config).prepare(item, "video", false),
                media -> sendVideo(config, classLoader, wxid, media.file));
    }

    private static SendResult sendVoiceAction(BridgeConfig config, ClassLoader classLoader, String wxid, JSONObject item) {
        return HookOutboxMediaActionRunner.run(
                "voice",
                () -> outboxMediaFilePreparer(config).prepareVoice(item),
                media -> sendVoice(config, classLoader, wxid, media.file, media.media.durationMs));
    }

    private static SendResult sendFileAction(BridgeConfig config, ClassLoader classLoader, String wxid, JSONObject item) {
        return HookOutboxMediaActionRunner.run(
                "file",
                () -> outboxMediaFilePreparer(config).prepare(item, "file", true),
                media -> sendFile(config, classLoader, wxid, media.file));
    }

    private static SendResult sendEmojiAction(BridgeConfig config, ClassLoader classLoader, String wxid, JSONObject item) {
        try {
            JSONObject payload = item.optJSONObject("payload_json");
            long sourceChatRecordId = firstPositiveLong(
                    item.optLong("source_chat_record_id", 0L),
                    payload == null ? 0L : payload.optLong("source_chat_record_id", 0L));
            String emojiMd5 = firstNonBlank(
                    item.optString("emoji_md5", ""),
                    payload == null ? "" : payload.optString("emoji_md5", ""));
            if (isBlank(emojiMd5) && sourceChatRecordId > 0L) {
                Object db = ensureMessageDatabase(config);
                if (db == null) {
                    return SendResult.failed("WeChat message database is not available for emoji source lookup");
                }
                ChatHistorySource source = loadChatHistorySource(db, sourceChatRecordId);
                if (source == null) {
                    return SendResult.failed("source emoji message was not found in local WeChat message database");
                }
                if (source.type != 47 && !(source.type == MESSAGE_TYPE_APPMSG && appMsgTypeFromContent(source.content) == 8)) {
                    return SendResult.failed("source_chat_record_id is not an emoji message");
                }
                emojiMd5 = HookMediaServices.emojiMd5FromWechatContent(source.talker, source.content);
            }
            if (isBlank(emojiMd5)) {
                return SendResult.failed("emoji_md5 or source_chat_record_id is required");
            }
            return sendEmoji(config, classLoader, wxid, emojiMd5);
        } catch (Throwable t) {
            return SendResult.failed("emoji send failed: " + shortError(t));
        }
    }

    private static SendResult sendLocationAction(BridgeConfig config, ClassLoader classLoader, String wxid, String text, JSONObject item) {
        try {
            JSONObject payload = item.optJSONObject("payload_json");
            double latitude = firstFiniteDouble(
                    item.optDouble("location_latitude", Double.NaN),
                    item.optDouble("latitude", Double.NaN),
                    payload == null ? Double.NaN : payload.optDouble("location_latitude", Double.NaN),
                    payload == null ? Double.NaN : payload.optDouble("latitude", Double.NaN));
            double longitude = firstFiniteDouble(
                    item.optDouble("location_longitude", Double.NaN),
                    item.optDouble("longitude", Double.NaN),
                    payload == null ? Double.NaN : payload.optDouble("location_longitude", Double.NaN),
                    payload == null ? Double.NaN : payload.optDouble("longitude", Double.NaN));
            int scale = firstPositiveInt(
                    item.optInt("location_scale", 0),
                    item.optInt("scale", 0),
                    payload == null ? 0 : payload.optInt("location_scale", 0),
                    payload == null ? 0 : payload.optInt("scale", 0),
                    16);
            String label = firstNonBlank(
                    item.optString("location_label", ""),
                    payload == null ? "" : payload.optString("location_label", ""),
                    text,
                    "[位置]");
            String poiName = firstNonBlank(
                    item.optString("location_poiname", ""),
                    item.optString("location_poi_name", ""),
                    payload == null ? "" : payload.optString("location_poiname", ""),
                    payload == null ? "" : payload.optString("location_poi_name", ""),
                    label);
            String infoURL = firstNonBlank(
                    item.optString("location_info_url", ""),
                    payload == null ? "" : payload.optString("location_info_url", ""));
            String poiID = firstNonBlank(
                    item.optString("location_poi_id", ""),
                    payload == null ? "" : payload.optString("location_poi_id", ""));
            String poiTips = firstNonBlank(
                    item.optString("location_poi_category_tips", ""),
                    payload == null ? "" : payload.optString("location_poi_category_tips", ""));
            boolean fromPoiList = item.optBoolean(
                    "location_from_poi_list",
                    payload != null && payload.optBoolean("location_from_poi_list", false));
            return sendLocation(config, classLoader, wxid, latitude, longitude, scale, label, poiName, infoURL, poiID, fromPoiList, poiTips);
        } catch (Throwable t) {
            return SendResult.failed("location send failed: " + shortError(t));
        }
    }

    private static SendResult sendQuoteAction(BridgeConfig config, ClassLoader classLoader, String wxid, String text, JSONObject item) {
        try {
            JSONObject payload = item.optJSONObject("payload_json");
            long quoteMsgId = firstPositiveLong(
                    item.optLong("quote_msg_id", 0L),
                    item.optLong("quote_chat_record_id", 0L),
                    payload == null ? 0L : payload.optLong("quote_msg_id", 0L),
                    payload == null ? 0L : payload.optLong("quote_chat_record_id", 0L));
            String quoteTalker = firstNonBlank(
                    item.optString("quote_talker", ""),
                    payload == null ? "" : payload.optString("quote_talker", ""),
                    wxid);
            String quoteSenderWxid = firstNonBlank(
                    item.optString("quote_sender_wxid", ""),
                    payload == null ? "" : payload.optString("quote_sender_wxid", ""));
            if (isBlank(text)) {
                return SendResult.failed("quote text is required");
            }
            if (quoteMsgId <= 0) {
                return SendResult.failed("quote_msg_id is required");
            }
            return sendQuote(config, classLoader, wxid, text, quoteMsgId, quoteTalker, quoteSenderWxid);
        } catch (Throwable t) {
            return SendResult.failed("quote send failed: " + shortError(t));
        }
    }

    private static SendResult sendLinkAction(BridgeConfig config, ClassLoader classLoader, String wxid, String text, JSONObject item) {
        try {
            JSONObject payload = item.optJSONObject("payload_json");
            long sourceChatRecordId = firstPositiveLong(
                    item.optLong("source_chat_record_id", 0L),
                    payload == null ? 0L : payload.optLong("source_chat_record_id", 0L));
            if (sourceChatRecordId > 0L) {
                return sendSourceAppMsg(config, classLoader, wxid, sourceChatRecordId, "link", APPMSG_TYPE_LINK);
            }
            String title = firstNonBlank(
                    item.optString("appmsg_title", ""),
                    payload == null ? "" : payload.optString("appmsg_title", ""),
                    text);
            String description = firstNonBlank(
                    item.optString("appmsg_description", ""),
                    payload == null ? "" : payload.optString("appmsg_description", ""));
            String url = firstNonBlank(
                    item.optString("appmsg_url", ""),
                    payload == null ? "" : payload.optString("appmsg_url", ""));
            String appName = firstNonBlank(
                    item.optString("appmsg_app_name", ""),
                    payload == null ? "" : payload.optString("appmsg_app_name", ""));
            String thumbUrl = firstNonBlank(
                    item.optString("appmsg_thumb_url", ""),
                    payload == null ? "" : payload.optString("appmsg_thumb_url", ""));
            if (isBlank(title)) {
                return SendResult.failed("appmsg_title is required");
            }
            if (isBlank(url)) {
                return SendResult.failed("appmsg_url is required");
            }
            return sendLink(config, classLoader, wxid, title, description, url, appName, thumbUrl);
        } catch (Throwable t) {
            return SendResult.failed("link send failed: " + shortError(t));
        }
    }

    private static SendResult sendMiniProgramAction(BridgeConfig config, ClassLoader classLoader, String wxid, String text, JSONObject item) {
        try {
            JSONObject payload = item.optJSONObject("payload_json");
            long sourceChatRecordId = firstPositiveLong(
                    item.optLong("source_chat_record_id", 0L),
                    payload == null ? 0L : payload.optLong("source_chat_record_id", 0L));
            if (sourceChatRecordId > 0L) {
                return sendSourceAppMsg(config, classLoader, wxid, sourceChatRecordId, "mini_program",
                        APPMSG_TYPE_MINI_PROGRAM, APPMSG_TYPE_MINI_PROGRAM_LEGACY);
            }
            String title = firstNonBlank(
                    item.optString("appmsg_title", ""),
                    payload == null ? "" : payload.optString("appmsg_title", ""),
                    text);
            String description = firstNonBlank(
                    item.optString("appmsg_description", ""),
                    payload == null ? "" : payload.optString("appmsg_description", ""));
            String url = firstNonBlank(
                    item.optString("appmsg_url", ""),
                    payload == null ? "" : payload.optString("appmsg_url", ""));
            String appName = firstNonBlank(
                    item.optString("appmsg_app_name", ""),
                    payload == null ? "" : payload.optString("appmsg_app_name", ""));
            String username = firstNonBlank(
                    item.optString("mini_program_username", ""),
                    payload == null ? "" : payload.optString("mini_program_username", ""));
            String pagePath = firstNonBlank(
                    item.optString("mini_program_page_path", ""),
                    payload == null ? "" : payload.optString("mini_program_page_path", ""));
            String appId = firstNonBlank(
                    item.optString("mini_program_appid", ""),
                    payload == null ? "" : payload.optString("mini_program_appid", ""));
            String iconUrl = firstNonBlank(
                    item.optString("mini_program_icon_url", ""),
                    payload == null ? "" : payload.optString("mini_program_icon_url", ""),
                    item.optString("appmsg_thumb_url", ""),
                    payload == null ? "" : payload.optString("appmsg_thumb_url", ""));
            int version = firstPositiveInt(
                    item.optInt("mini_program_version", 0),
                    payload == null ? 0 : payload.optInt("mini_program_version", 0));
            int miniType = firstPositiveInt(
                    item.optInt("mini_program_type", 0),
                    payload == null ? 0 : payload.optInt("mini_program_type", 0));
            if (isBlank(title)) {
                return SendResult.failed("appmsg_title is required");
            }
            if (isBlank(username)) {
                return SendResult.failed("mini_program_username is required");
            }
            if (isBlank(pagePath)) {
                return SendResult.failed("mini_program_page_path is required");
            }
            return sendMiniProgram(config, classLoader, wxid, title, description, url, appName, username, pagePath, appId, iconUrl, version, miniType);
        } catch (Throwable t) {
            return SendResult.failed("mini_program send failed: " + shortError(t));
        }
    }

    private static SendResult sendChatHistoryAction(BridgeConfig config, ClassLoader classLoader, String wxid, String text, JSONObject item) {
        try {
            JSONObject payload = item.optJSONObject("payload_json");
            String title = firstNonBlank(
                    item.optString("record_title", ""),
                    payload == null ? "" : payload.optString("record_title", ""),
                    text,
                    "聊天记录");
            String description = firstNonBlank(
                    item.optString("record_description", ""),
                    payload == null ? "" : payload.optString("record_description", ""));
            String recordItemXML = firstNonBlank(
                    item.optString("recorditem_xml", ""),
                    payload == null ? "" : payload.optString("recorditem_xml", ""),
                    payload == null ? "" : payload.optString("record_xml", ""));
            recordItemXML = normalizeRecordItemXML(recordItemXML);
            List<Long> sourceChatRecordIds = readSourceChatRecordIds(
                    item.optJSONArray("source_chat_record_ids"),
                    payload == null ? null : payload.optJSONArray("source_chat_record_ids"));
            boolean forwardOriginal = item.optBoolean("forward_original", payload != null && payload.optBoolean("forward_original", false));
            long sourceChatRecordId = firstPositiveLong(
                    item.optLong("source_chat_record_id", 0L),
                    payload == null ? 0L : payload.optLong("source_chat_record_id", 0L),
                    sourceChatRecordIds.isEmpty() ? 0L : sourceChatRecordIds.get(0).longValue());
            if (forwardOriginal) {
                if (sourceChatRecordId <= 0L) {
                    return SendResult.failed("source_chat_record_id is required when forward_original is true");
                }
                Object db = ensureMessageDatabase(config);
                if (db == null) {
                    return SendResult.failed("WeChat message database is not available for original chat_history forward");
                }
                ChatHistorySource source = loadChatHistorySource(db, sourceChatRecordId);
                if (source == null) {
                    return SendResult.failed("source chat_history message was not found in local WeChat message database");
                }
                String sourceContent = normalizeMessageText(source.talker, source.content);
                if (source.type != MESSAGE_TYPE_APPMSG || appMsgTypeFromContent(sourceContent) != APPMSG_TYPE_CHAT_HISTORY) {
                    return SendResult.failed("source_chat_record_id must point to a chat_history appmsg");
                }
                String originalRecordItemXML = normalizeRecordItemXML(extractXmlFieldRaw(sourceContent, "recorditem"));
                if (isBlank(originalRecordItemXML)) {
                    return SendResult.failed("source chat_history recorditem is missing");
                }
                if (!originalRecordItemXML.trim().startsWith("<recordinfo")) {
                    return SendResult.failed("source chat_history recorditem must contain recordinfo XML");
                }
                String originalTitle = firstNonBlank(
                        extractXmlField(originalRecordItemXML, "title"),
                        extractXmlField(sourceContent, "title"));
                String originalDescription = firstNonBlank(
                        extractXmlField(sourceContent, "des"),
                        extractXmlField(sourceContent, "desc"),
                        extractXmlField(originalRecordItemXML, "desc"));
                title = firstNonBlank(
                        item.optString("record_title", ""),
                        payload == null ? "" : payload.optString("record_title", ""),
                        originalTitle,
                        text,
                        "聊天记录");
                description = firstNonBlank(
                        item.optString("record_description", ""),
                        payload == null ? "" : payload.optString("record_description", ""),
                        originalDescription);
                return sendChatHistory(config, classLoader, wxid, title, description, originalRecordItemXML);
            }
            if (isBlank(recordItemXML) && !sourceChatRecordIds.isEmpty()) {
                Object db = ensureMessageDatabase(config);
                if (db == null) {
                    return SendResult.failed("WeChat message database is not available for chat_history source build");
                }
                recordItemXML = buildChatHistoryRecordItemXML(db, sourceChatRecordIds);
                if (isBlank(description)) {
                    description = "共" + sourceChatRecordIds.size() + "条";
                }
            }
            if (isBlank(recordItemXML)) {
                return SendResult.failed("recorditem_xml or source_chat_record_ids is required");
            }
            if (!recordItemXML.trim().startsWith("<recordinfo")) {
                return SendResult.failed("recorditem_xml must contain recordinfo XML");
            }
            return sendChatHistory(config, classLoader, wxid, title, description, recordItemXML);
        } catch (Throwable t) {
            return SendResult.failed("chat_history send failed: " + shortError(t));
        }
    }

    private static SendResult sendRevokeAction(BridgeConfig config, ClassLoader classLoader, String wxid, JSONObject item) {
        try {
            JSONObject payload = item.optJSONObject("payload_json");
            long targetChatRecordId = firstPositiveLong(
                    item.optLong("target_chat_record_id", 0L),
                    payload == null ? 0L : payload.optLong("target_chat_record_id", 0L),
                    payload == null ? 0L : payload.optLong("source_chat_record_id", 0L),
                    payload == null ? 0L : payload.optLong("client_msg_id", 0L),
                    payload == null ? 0L : payload.optLong("new_msg_id", 0L));
            long targetMsgSvrId = firstPositiveLong(
                    item.optLong("target_msg_svr_id", 0L),
                    payload == null ? 0L : payload.optLong("target_msg_svr_id", 0L),
                    payload == null ? 0L : payload.optLong("msg_svr_id", 0L),
                    payload == null ? 0L : payload.optLong("new_msg_id", 0L));
            String revokeTicket = firstNonBlank(
                    item.optString("revoke_ticket", ""),
                    payload == null ? "" : payload.optString("revoke_ticket", ""));
            if (targetChatRecordId <= 0L && targetMsgSvrId <= 0L) {
                return SendResult.failed("target_chat_record_id is required");
            }
            return sendRevoke(config, classLoader, wxid, targetChatRecordId, targetMsgSvrId, revokeTicket);
        } catch (Throwable t) {
            return SendResult.failed("revoke send failed: " + shortError(t));
        }
    }

    private static OutboxMediaFilePreparer outboxMediaFilePreparer(BridgeConfig config) {
        return HookMediaServices.outboxMediaFilePreparer(
                config,
                new HookOutboxMediaDownloadEnvironment.CacheDirProvider() {
                    @Override
                    public File cacheDir() throws IOException {
                        return outboxMediaDir();
                    }
                },
                HookEntry::log);
    }

    private static File outboxMediaDir() throws IOException {
        Context context = APP_CONTEXT;
        if (context == null) {
            Object app = currentApplication();
            if (app instanceof Context) {
                context = (Context) app;
            }
        }
        if (context == null || context.getCacheDir() == null) {
            throw new IOException("cache dir is not available");
        }
        return new File(context.getCacheDir(), "wechat-observatory-outbox");
    }

    private static SendResult sendImage(BridgeConfig config, ClassLoader classLoader, String wxid, File imageFile, String text) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || imageFile == null || !imageFile.isFile()) {
            return SendResult.failed("wxid and image file are required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for image send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final File targetImageFile = imageFile;
        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed("image send verification failed before send: " + shortError(t));
        }
        MEDIA_ATTACHMENT_CONTROLLER.rememberOutgoingSource(targetWxid, 3, targetImageFile, beforeMsgId);
        try {
            callOnMainThread(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    sendViaSendImgMgr(targetClassLoader, targetWxid, targetImageFile);
                    return null;
                }
            });
        } catch (Throwable t) {
            return SendResult.failed("WeChat image send failed via dk5.s5.Vi: " + shortError(t));
        }

        long msgId = waitForOutgoingImageMessage(config, targetWxid, beforeMsgId, 8000L);
        if (msgId > 0) {
            MEDIA_ATTACHMENT_CONTROLLER.bindOutgoingSource(targetWxid, 3, msgId, targetImageFile);
            log("sendImage verified outgoing image msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat image send invoked but no outgoing image message was recorded");
    }

    private static SendResult sendVideo(BridgeConfig config, ClassLoader classLoader, String wxid, File videoFile) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || videoFile == null || !videoFile.isFile()) {
            return SendResult.failed("wxid and video file are required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for video send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final File targetVideoFile = videoFile;
        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed("video send verification failed before send: " + shortError(t));
        }
        try {
            callOnMainThread(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    sendViaVideoSendFeature(targetClassLoader, targetWxid, targetVideoFile);
                    return null;
                }
            });
        } catch (Throwable t) {
            return SendResult.failed("WeChat video send failed via video feature: " + shortError(t));
        }

        long msgId = waitForOutgoingMediaMessage(config, targetWxid, beforeMsgId, 8000L, "video", "43,62");
        if (msgId > 0) {
            log("sendVideo verified outgoing video msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat video send invoked but no outgoing video message was recorded");
    }

    private static SendResult sendVoice(BridgeConfig config, ClassLoader classLoader, String wxid, File voiceFile, int durationMs) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || voiceFile == null || !voiceFile.isFile()) {
            return SendResult.failed("wxid and voice file are required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for voice send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final File targetVoiceFile = voiceFile;
        final int targetDurationMs = Math.max(1000, durationMs);
        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed("voice send verification failed before send: " + shortError(t));
        }
        try {
            callOnMainThread(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    sendViaVoiceSendTask(targetClassLoader, targetWxid, targetVoiceFile, targetDurationMs);
                    return null;
                }
            });
        } catch (Throwable t) {
            return SendResult.failed("WeChat voice send failed via VoiceMsgSendTask: " + shortError(t));
        }

        long msgId = waitForOutgoingMediaMessage(config, targetWxid, beforeMsgId, 12000L, "voice", "34");
        if (msgId > 0) {
            log("sendVoice verified outgoing voice msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat voice send invoked but no outgoing voice message was recorded");
    }

    private static SendResult sendFile(BridgeConfig config, ClassLoader classLoader, String wxid, File file) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || file == null || !file.isFile()) {
            return SendResult.failed("wxid and file are required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for file send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final File targetFile = file;
        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed("file send verification failed before send: " + shortError(t));
        }
        try {
            callOnMainThread(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    sendViaFileSendLogic(targetClassLoader, targetWxid, targetFile);
                    return null;
                }
            });
        } catch (Throwable t) {
            return SendResult.failed("WeChat file send failed via FileSendLogic: " + shortError(t));
        }

        long msgId = waitForOutgoingMediaMessage(config, targetWxid, beforeMsgId, 12000L, "file", String.valueOf(MESSAGE_TYPE_FILE_TRANSFER));
        if (msgId <= 0L) {
            msgId = waitForOutgoingAppMsgMessage(config, targetWxid, beforeMsgId, 2000L, "file", APPMSG_TYPE_FILE);
        }
        if (msgId > 0) {
            postVerifiedOutgoingMessage(config, db, msgId, "file");
            log("sendFile verified outgoing file msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat file send invoked but no outgoing file message was recorded");
    }

    private static SendResult sendEmoji(BridgeConfig config, ClassLoader classLoader, String wxid, String emojiMd5) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || isBlank(emojiMd5)) {
            return SendResult.failed("wxid and emoji_md5 are required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for emoji send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final String targetEmojiMd5 = emojiMd5.trim();
        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed("emoji send verification failed before send: " + shortError(t));
        }
        try {
            callOnMainThread(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    sendViaEmojiMgr(targetClassLoader, targetWxid, targetEmojiMd5);
                    return null;
                }
            });
        } catch (Throwable t) {
            return SendResult.failed("WeChat emoji send failed via EmojiMgrImpl: " + shortError(t));
        }

        long msgId = waitForOutgoingMediaMessage(config, targetWxid, beforeMsgId, 8000L, "emoji", "47");
        if (msgId > 0L) {
            log("sendEmoji verified outgoing emoji msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat emoji send invoked but no outgoing emoji message was recorded");
    }

    private static SendResult sendLocation(BridgeConfig config, ClassLoader classLoader, String wxid, double latitude, double longitude, int scale, String label, String poiName, String infoURL, String poiID, boolean fromPoiList, String poiTips) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || !isFinite(latitude) || !isFinite(longitude)) {
            return SendResult.failed("wxid, location_latitude, and location_longitude are required");
        }
        if (latitude < -90D || latitude > 90D || longitude < -180D || longitude > 180D) {
            return SendResult.failed("location coordinates are out of range");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for location send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final double targetLatitude = latitude;
        final double targetLongitude = longitude;
        final int targetScale = Math.max(1, scale);
        final String targetLabel = isBlank(label) ? "[位置]" : label;
        final String targetPoiName = isBlank(poiName) ? targetLabel : poiName;
        final String targetInfoURL = infoURL == null ? "" : infoURL;
        final String targetPoiID = poiID == null ? "" : poiID;
        final boolean targetFromPoiList = fromPoiList;
        final String targetPoiTips = poiTips == null ? "" : poiTips;
        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed("location send verification failed before send: " + shortError(t));
        }
        try {
            callOnMainThread(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    sendViaLocationFeature(targetClassLoader, targetWxid, targetLatitude, targetLongitude, targetScale, targetLabel, targetPoiName, targetInfoURL, targetPoiID, targetFromPoiList, targetPoiTips);
                    return null;
                }
            });
        } catch (Throwable t) {
            return SendResult.failed("WeChat location send failed via LocationMsgSendTask: " + shortError(t));
        }

        long msgId = waitForOutgoingMediaMessage(config, targetWxid, beforeMsgId, 10000L, "location", "48");
        if (msgId > 0L) {
            log("sendLocation verified outgoing location msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat location send invoked but no outgoing location message was recorded");
    }

    private static SendResult sendQuote(BridgeConfig config, ClassLoader classLoader, String wxid, String text, long quoteMsgId, String quoteTalker, String quoteSenderWxid) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || isBlank(text) || quoteMsgId <= 0L || isBlank(quoteTalker)) {
            return SendResult.failed("wxid, text, quote_msg_id, and quote_talker are required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for quote send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final String targetText = text;
        final long beforeMsgId;
        final QuoteSource quoteSource;
        try {
            beforeMsgId = readMaxMessageId(db);
            quoteSource = loadQuoteSource(db, quoteMsgId, quoteTalker);
            if (quoteSource != null && !isBlank(quoteSenderWxid)) {
                quoteSource.senderWxid = quoteSenderWxid.trim();
            }
        } catch (Throwable t) {
            return SendResult.failed("quote send verification failed before send: " + shortError(t));
        }
        if (quoteSource == null) {
            return SendResult.failed("quoted message was not found in local WeChat message database");
        }
        try {
            Long createdMsgId = callOnMainThread(new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    return Long.valueOf(sendViaQuoteAppMsg(targetClassLoader, targetWxid, targetText, quoteSource));
                }
            });
            if (createdMsgId == null || createdMsgId.longValue() <= 0L) {
                log("sendQuote appmsg sender returned no message id; wait for outgoing quote record");
            }
        } catch (Throwable t) {
            return SendResult.failed("WeChat quote send failed via appmsg quote path: " + shortError(t));
        }

        long msgId = waitForOutgoingMediaMessage(config, targetWxid, beforeMsgId, 8000L, "quote", String.valueOf(MESSAGE_TYPE_QUOTE));
        if (msgId > 0L) {
            try {
                boolean inserted = insertMsgQuoteRecord(targetClassLoader, msgId, quoteSource);
                log("sendQuote MsgQuote record ensured msgId=" + msgId + " inserted=" + inserted);
            } catch (Throwable t) {
                log("sendQuote MsgQuote record ensure failed: " + shortError(t));
            }
            log("sendQuote verified outgoing quote msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat quote send invoked but no outgoing quote message was recorded");
    }

    private static SendResult sendChatHistory(BridgeConfig config, ClassLoader classLoader, String wxid, String title, String description, String recordItemXML) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || isBlank(title) || isBlank(recordItemXML)) {
            return SendResult.failed("wxid, title, and recorditem_xml are required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for chat_history send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final String targetTitle = title;
        final String targetDescription = description;
        final String targetRecordItemXML = recordItemXML;
        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed("chat_history send verification failed before send: " + shortError(t));
        }
        try {
            Long createdMsgId = callOnMainThread(new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    return Long.valueOf(sendViaChatHistoryAppMsg(targetClassLoader, targetWxid, targetTitle, targetDescription, targetRecordItemXML));
                }
            });
            if (createdMsgId == null || createdMsgId.longValue() <= 0L) {
                log("sendChatHistory appmsg sender returned no message id; wait for outgoing chat_history record");
            }
        } catch (Throwable t) {
            return SendResult.failed("WeChat chat_history send failed via appmsg path: " + shortError(t));
        }

        long msgId = waitForOutgoingAppMsgMessage(config, targetWxid, beforeMsgId, 10000L, "chat_history", APPMSG_TYPE_CHAT_HISTORY);
        if (msgId > 0L) {
            postVerifiedOutgoingMessage(config, db, msgId, "chat_history");
            log("sendChatHistory verified outgoing chat_history msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat chat_history send invoked but no outgoing chat_history message was recorded");
    }

    private static SendResult sendLink(BridgeConfig config, ClassLoader classLoader, String wxid, String title, String description, String url, String appName, String thumbUrl) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || isBlank(title) || isBlank(url)) {
            return SendResult.failed("wxid, appmsg_title, and appmsg_url are required");
        }
        try {
            Class<?> appMsgClass = findClass(classLoader, "ot0.q");
            Object appMsg = appMsgClass.getDeclaredConstructor().newInstance();
            setObjectField(appMsg, title, "f");
            setObjectField(appMsg, description == null ? "" : description, "g");
            setIntFieldAny(appMsg, APPMSG_TYPE_LINK, "i");
            setObjectField(appMsg, url, "k");
            setObjectField(appMsg, url, "l");
            if (!isBlank(appName)) {
                setObjectField(appMsg, appName, "x");
            }
            if (!isBlank(thumbUrl)) {
                setObjectField(appMsg, thumbUrl, "z");
            }
            return sendAppMsgObject(config, classLoader, wxid, appMsg, "link", APPMSG_TYPE_LINK);
        } catch (Throwable t) {
            return SendResult.failed("WeChat link send failed via appmsg path: " + shortError(t));
        }
    }

    private static SendResult sendMiniProgram(BridgeConfig config, ClassLoader classLoader, String wxid, String title, String description, String url, String appName, String username, String pagePath, String appId, String iconUrl, int version, int miniType) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || isBlank(title) || isBlank(username) || isBlank(pagePath)) {
            return SendResult.failed("wxid, appmsg_title, mini_program_username, and mini_program_page_path are required");
        }
        try {
            Class<?> appMsgClass = findClass(classLoader, "ot0.q");
            Object appMsg = appMsgClass.getDeclaredConstructor().newInstance();
            setObjectField(appMsg, title, "f");
            setObjectField(appMsg, description == null ? "" : description, "g");
            setIntFieldAny(appMsg, APPMSG_TYPE_MINI_PROGRAM, "i");
            setObjectField(appMsg, url == null ? "" : url, "k");
            setObjectField(appMsg, username, "j2");
            setObjectField(appMsg, pagePath, "i2");
            setObjectField(appMsg, appId == null ? "" : appId, "k2");
            setObjectField(appMsg, iconUrl == null ? "" : iconUrl, "B2");
            if (!isBlank(appName)) {
                setObjectField(appMsg, appName, "x");
                setObjectField(appMsg, appName, "s2");
            }
            if (version > 0) {
                setIntFieldAny(appMsg, version, "A2");
            }
            if (miniType > 0) {
                setIntFieldAny(appMsg, miniType, "l2");
            }
            return sendAppMsgObject(config, classLoader, wxid, appMsg, "mini_program", APPMSG_TYPE_MINI_PROGRAM, APPMSG_TYPE_MINI_PROGRAM_LEGACY);
        } catch (Throwable t) {
            return SendResult.failed("WeChat mini_program send failed via appmsg path: " + shortError(t));
        }
    }

    private static SendResult sendSourceAppMsg(BridgeConfig config, ClassLoader classLoader, String wxid, long sourceChatRecordId, String kind, int... expectedAppMsgTypes) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for " + kind + " source send");
        }
        try {
            ChatHistorySource source = loadChatHistorySource(db, sourceChatRecordId);
            if (source == null) {
                return SendResult.failed("source appmsg was not found in local WeChat message database");
            }
            String sourceContent = normalizeMessageText(source.talker, source.content);
            if (source.type != MESSAGE_TYPE_APPMSG) {
                return SendResult.failed("source_chat_record_id must point to an appmsg");
            }
            int appMsgType = appMsgTypeFromContent(sourceContent);
            if (!containsInt(expectedAppMsgTypes, appMsgType)) {
                return SendResult.failed("source appmsg type does not match " + kind);
            }
            Object appMsg = parseAppMsgObject(classLoader, sourceContent);
            if (appMsg == null) {
                return SendResult.failed("source appmsg XML could not be parsed by WeChat");
            }
            return sendAppMsgObject(config, classLoader, wxid, appMsg, kind, expectedAppMsgTypes);
        } catch (Throwable t) {
            return SendResult.failed(kind + " source send failed: " + shortError(t));
        }
    }

    private static Object parseAppMsgObject(ClassLoader classLoader, String sourceContent) throws Exception {
        if (isBlank(sourceContent)) {
            return null;
        }
        Class<?> appMsgClass = findClass(classLoader, "ot0.q");
        Object parsed = findMethod(appMsgClass, "v", String.class).invoke(null, sourceContent);
        return appMsgClass.isInstance(parsed) ? parsed : null;
    }

    private static SendResult sendAppMsgObject(BridgeConfig config, ClassLoader classLoader, String wxid, Object appMsg, String kind, int... expectedAppMsgTypes) {
        if (isBlank(wxid) || appMsg == null || expectedAppMsgTypes == null || expectedAppMsgTypes.length == 0) {
            return SendResult.failed("wxid, appmsg, and expected appmsg type are required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for " + kind + " send verification");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetWxid = wxid;
        final Object targetAppMsg = appMsg;
        final int[] targetTypes = expectedAppMsgTypes;
        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed(kind + " send verification failed before send: " + shortError(t));
        }
        try {
            Long createdMsgId = callOnMainThread(new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    return Long.valueOf(sendViaAppMsgObject(targetClassLoader, targetWxid, targetAppMsg));
                }
            });
            if (createdMsgId == null || createdMsgId.longValue() <= 0L) {
                log("send " + kind + " appmsg sender returned no message id; wait for outgoing appmsg record");
            }
        } catch (Throwable t) {
            return SendResult.failed("WeChat " + kind + " send failed via appmsg path: " + shortError(t));
        }
        long msgId = waitForOutgoingAppMsgMessage(config, targetWxid, beforeMsgId, 10000L, kind, targetTypes);
        if (msgId > 0L) {
            postVerifiedOutgoingMessage(config, db, msgId, kind);
            log("send " + kind + " verified outgoing appmsg msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        return SendResult.failed("WeChat " + kind + " send invoked but no outgoing appmsg message was recorded");
    }

    private static long sendViaAppMsgObject(ClassLoader classLoader, String wxid, Object appMsg) throws Exception {
        Class<?> appMsgClass = findClass(classLoader, "ot0.q");
        Class<?> appMsgLogicClass = findClass(classLoader, "com.tencent.mm.pluginsdk.model.app.k0");
        Method send = findMethod(appMsgLogicClass, "I", appMsgClass,
                String.class, String.class, String.class, String.class, byte[].class);
        Object pair = send.invoke(null, appMsg, "", "", wxid, "", null);
        return pair == null ? 0L : longPairField(pair, "second");
    }

    private static SendResult sendRevoke(
            BridgeConfig config,
            ClassLoader classLoader,
            String wxid,
            long targetChatRecordId,
            long targetMsgSvrId,
            String revokeTicket) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid)) {
            return SendResult.failed("wxid is required");
        }
        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for revoke verification");
        }

        MessagePayload source = null;
        long msgSvrId = targetMsgSvrId;
        try {
            if (targetChatRecordId > 0L) {
                source = loadMessagePayloadById(config, db, targetChatRecordId);
                long resolvedMsgSvrId = messageServerIdByLocalId(db, targetChatRecordId);
                if (resolvedMsgSvrId > 0L) {
                    msgSvrId = resolvedMsgSvrId;
                }
            }
        } catch (Throwable t) {
            return SendResult.failed("revoke source lookup failed: " + shortError(t));
        }
        if (source != null && !"sent".equals(source.direction)) {
            return SendResult.failed("target message is not sent by current account");
        }
        String talker = firstNonBlank(source == null ? "" : source.chatId, wxid);
        if (!sameTalker(talker, wxid)) {
            return SendResult.failed("target message chat does not match wxid");
        }
        final ClassLoader targetClassLoader = classLoader;
        final String targetTalker = talker;
        final long localMsgId = targetChatRecordId;
        final long serverMsgId = msgSvrId;
        final String ticket = revokeTicket == null ? "" : revokeTicket;
        final Object message;
        try {
            message = loadWechatMessageObject(targetClassLoader, targetTalker, localMsgId, serverMsgId);
        } catch (Throwable t) {
            return SendResult.failed("WeChat message object lookup failed: " + shortError(t));
        }
        if (message == null) {
            return SendResult.failed("target message was not found in local WeChat message storage");
        }

        final long beforeMsgId;
        try {
            beforeMsgId = readMaxMessageId(db);
        } catch (Throwable t) {
            return SendResult.failed("revoke verification failed before send: " + shortError(t));
        }
        try {
            callOnMainThread(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    invokeWeChatRevoke(targetClassLoader, message, ticket);
                    return null;
                }
            });
        } catch (Throwable t) {
            return SendResult.failed("WeChat revoke invoke failed: " + shortError(t));
        }

        long confirmedId = waitForRevokeConfirmation(config, targetTalker, localMsgId, beforeMsgId, 10000L);
        if (confirmedId > 0L || hasRevokeConfirmed(localMsgId)) {
            log("sendRevoke verified targetMsgId=" + localMsgId + " revokeRecordId=" + confirmedId);
            return SendResult.sent(localMsgId);
        }
        return SendResult.failed("WeChat revoke invoked but no revoke event was recorded");
    }

    private static Object loadWechatMessageObject(ClassLoader classLoader, String talker, long msgId, long msgSvrId) throws Exception {
        Object storage = currentMessageStorage(classLoader);
        if (storage == null) {
            return null;
        }
        Object message = tryLoadWechatMessageObject(storage, talker, msgId, msgSvrId);
        if (message != null) {
            return message;
        }
        return scanLoadWechatMessageObject(classLoader, storage, talker, msgId, msgSvrId);
    }

    private static Object currentMessageStorage(ClassLoader classLoader) throws Exception {
        Object accountStorage = findNoArgMethod(findClass(classLoader, "c01.d9"), "b").invoke(null);
        if (accountStorage == null) {
            return null;
        }
        return findNoArgMethod(accountStorage.getClass(), "u").invoke(accountStorage);
    }

    private static Object tryLoadWechatMessageObject(Object storage, String talker, long msgId, long msgSvrId) {
        long[] ids = uniquePositiveLongs(msgSvrId, msgId);
        try {
            Method byTalkerAndId = findMethod(storage.getClass(), "o2", String.class, long.class);
            for (long id : ids) {
                Object message = byTalkerAndId.invoke(storage, talker, id);
                if (matchesWechatMessageObject(message, talker, msgId, msgSvrId)) {
                    return message;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to reflective scan for other storage implementations.
        }
        return null;
    }

    private static Object scanLoadWechatMessageObject(ClassLoader classLoader, Object storage, String talker, long msgId, long msgSvrId) throws Exception {
        Class<?> messageClass = findClass(classLoader, "com.tencent.mm.storage.f9");
        List<Method> methods = methodsOf(storage.getClass());
        long[] ids = uniquePositiveLongs(msgSvrId, msgId);
        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();
            if (!messageClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            method.setAccessible(true);
            if (types.length == 2 && types[0] == String.class && isLongParameter(types[1])) {
                for (long id : ids) {
                    Object message = method.invoke(storage, talker, Long.valueOf(id));
                    if (matchesWechatMessageObject(message, talker, msgId, msgSvrId)) {
                        return message;
                    }
                }
            } else if (types.length == 1 && isLongParameter(types[0])) {
                for (long id : ids) {
                    Object message = method.invoke(storage, Long.valueOf(id));
                    if (matchesWechatMessageObject(message, talker, msgId, msgSvrId)) {
                        return message;
                    }
                }
            }
        }
        return null;
    }

    private static List<Method> methodsOf(Class<?> cls) {
        List<Method> methods = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Class<?> current = cls;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                String key = methodKey(method);
                if (seen.add(key)) {
                    methods.add(method);
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : cls.getMethods()) {
            String key = methodKey(method);
            if (seen.add(key)) {
                methods.add(method);
            }
        }
        return methods;
    }

    private static String methodKey(Method method) {
        StringBuilder out = new StringBuilder(method.getName()).append('#').append(method.getParameterTypes().length);
        for (Class<?> type : method.getParameterTypes()) {
            out.append(':').append(type.getName());
        }
        return out.toString();
    }

    private static boolean matchesWechatMessageObject(Object message, String talker, long msgId, long msgSvrId) {
        if (message == null) {
            return false;
        }
        long objectMsgId = firstPositiveLong(
                optionalLongMethod(message, "getMsgId"),
                optionalLongField(message, "field_msgId", "msgId"));
        long objectMsgSvrId = firstPositiveLong(
                optionalLongMethod(message, "I0"),
                optionalLongMethod(message, "getMsgSvrId"),
                optionalLongField(message, "field_msgSvrId", "msgSvrId"));
        String objectTalker = firstNonBlank(
                optionalStringMethod(message, "Q0"),
                optionalStringMethod(message, "getTalker"),
                optionalStringField(message, "field_talker", "talker"));
        boolean idMatches = (msgId <= 0L || objectMsgId == msgId)
                || (msgSvrId > 0L && objectMsgSvrId == msgSvrId);
        return idMatches && (isBlank(talker) || isBlank(objectTalker) || sameTalker(objectTalker, talker));
    }

    private static void invokeWeChatRevoke(ClassLoader classLoader, Object message, String revokeTicket) throws Exception {
        try {
            Class<?> messageClass = findClass(classLoader, "com.tencent.mm.storage.f9");
            Class<?> handlerClass = findClass(classLoader, "cd0.b0");
            Object handler = findFieldAny(handlerClass, "a").get(null);
            if (handler == null) {
                handler = handlerClass.getDeclaredConstructor().newInstance();
            }
            Method revoke = findMethod(handlerClass, "b", messageClass, String.class, String.class);
            revoke.invoke(handler, messageClass.cast(message), "", revokeTicket == null ? "" : revokeTicket);
            log("sendRevoke invoked RevokeMsgHandler");
            return;
        } catch (Throwable t) {
            log("RevokeMsgHandler path failed, try direct NetSceneRevokeMsg: " + shortError(t));
        }
        invokeWeChatRevokeScene(classLoader, message, revokeTicket);
    }

    private static void invokeWeChatRevokeScene(ClassLoader classLoader, Object message, String revokeTicket) throws Exception {
        Class<?> messageClass = findClass(classLoader, "com.tencent.mm.storage.f9");
        Class<?> sceneClass = findClass(classLoader, "com.tencent.mm.modelsimple.d1");
        Object scene = findConstructor(sceneClass, messageClass, String.class, String.class)
                .newInstance(messageClass.cast(message), "", revokeTicket == null ? "" : revokeTicket);
        Object queue = findNoArgMethod(findClass(classLoader, "gm0.j1"), "d").invoke(null);
        try {
            Class<?> callbackClass = findClass(classLoader, "cd0.t");
            Object callback = findConstructor(callbackClass, sceneClass).newInstance(scene);
            findMethod(queue.getClass(), "a", int.class, findClass(classLoader, "com.tencent.mm.modelbase.u0"))
                    .invoke(queue, Integer.valueOf(594), callback);
        } catch (Throwable t) {
            log("revoke scene callback registration skipped: " + shortError(t));
        }
        findMethod(queue.getClass(), "g", findClass(classLoader, "com.tencent.mm.modelbase.m1")).invoke(queue, scene);
        log("sendRevoke invoked NetSceneRevokeMsg directly");
    }

    private static QuoteSource loadQuoteSource(Object db, long quoteMsgId, String quoteTalker) throws Exception {
        QuoteSource source = queryQuoteSource(db, quoteMsgId, quoteTalker, true, true);
        if (source != null) {
            return source;
        }
        source = queryQuoteSource(db, quoteMsgId, quoteTalker, true, false);
        if (source != null) {
            return source;
        }
        source = queryQuoteSource(db, quoteMsgId, quoteTalker, false, true);
        if (source != null) {
            return source;
        }
        return queryQuoteSource(db, quoteMsgId, quoteTalker, false, false);
    }

    private static QuoteSource queryQuoteSource(Object db, long quoteMsgId, String quoteTalker, boolean requireTalker, boolean includeMsgSource) throws Exception {
        String msgSourceSelect = includeMsgSource ? ",COALESCE(msgSource,'')" : ",''";
        String where = requireTalker ? "msgId = ? AND talker = ?" : "msgId = ?";
        String[] args = requireTalker
                ? new String[]{String.valueOf(quoteMsgId), quoteTalker}
                : new String[]{String.valueOf(quoteMsgId)};
        Object cursor;
        try {
            cursor = rawQuery(db, ""
                    + "SELECT msgId,COALESCE(msgSvrId,0),talker,COALESCE(content,''),isSend,createTime,type"
                    + msgSourceSelect + " "
                    + "FROM message "
                    + "WHERE " + where + " "
                    + "LIMIT 1", args);
        } catch (Throwable t) {
            if (includeMsgSource) {
                return null;
            }
            throw t;
        }
        if (cursor == null) {
            return null;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (!Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return null;
            }
            QuoteSource source = new QuoteSource();
            int column = 0;
            source.msgId = longColumn(cursor, column++);
            source.msgSvrId = longColumn(cursor, column++);
            source.talker = stringColumn(cursor, column++);
            source.content = stringColumn(cursor, column++);
            source.isSend = intColumn(cursor, column++);
            source.createTime = normalizeCreateTime(longColumn(cursor, column++));
            source.type = intColumn(cursor, column++);
            source.msgSource = stringColumn(cursor, column);
            return source.msgId > 0L ? source : null;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static long sendViaQuoteAppMsg(ClassLoader classLoader, String wxid, String text, QuoteSource source) throws Exception {
        Class<?> appMsgClass = findClass(classLoader, "ot0.q");
        Object appMsg = appMsgClass.getDeclaredConstructor().newInstance();
        setObjectField(appMsg, text, "f");
        setIntFieldAny(appMsg, APPMSG_TYPE_QUOTE, "i");

        Object quoteItem = createMsgQuoteItem(classLoader, source);
        setObjectField(appMsg, quoteItem, "w2");

        Class<?> appMsgLogicClass = findClass(classLoader, "com.tencent.mm.pluginsdk.model.app.k0");
        Method send = findMethod(appMsgLogicClass, "I", appMsgClass,
                String.class, String.class, String.class, String.class, byte[].class);
        Object pair = send.invoke(null, appMsg, "", "", wxid, "", null);
        long newMsgId = pair == null ? 0L : longPairField(pair, "second");
        if (newMsgId <= 0L) {
            return 0L;
        }
        try {
            boolean inserted = insertMsgQuoteRecord(classLoader, newMsgId, source);
            log("sendQuote appmsg created msgId=" + newMsgId + " quoteRecordInserted=" + inserted);
        } catch (Throwable t) {
            log("sendQuote appmsg created but MsgQuote insert failed: " + shortError(t));
        }
        return newMsgId;
    }

    private static long sendViaChatHistoryAppMsg(ClassLoader classLoader, String wxid, String title, String description, String recordItemXML) throws Exception {
        Class<?> appMsgClass = findClass(classLoader, "ot0.q");
        Object appMsg = appMsgClass.getDeclaredConstructor().newInstance();
        setObjectField(appMsg, title, "f");
        setObjectField(appMsg, description == null ? "" : description, "g");
        setIntFieldAny(appMsg, APPMSG_TYPE_CHAT_HISTORY, "i");
        setObjectField(appMsg, recordItemXML, "h0");

        Class<?> appMsgLogicClass = findClass(classLoader, "com.tencent.mm.pluginsdk.model.app.k0");
        Method send = findMethod(appMsgLogicClass, "I", appMsgClass,
                String.class, String.class, String.class, String.class, byte[].class);
        Object pair = send.invoke(null, appMsg, "", "", wxid, "", null);
        return pair == null ? 0L : longPairField(pair, "second");
    }

    private static Object createMsgQuoteItem(ClassLoader classLoader, QuoteSource source) throws Exception {
        Object quoteItem = newMsgQuoteItem(classLoader);
        String sender = quoteSender(source);
        String quoteContent = normalizeMessageText(source.talker, source.content);
        String msgSource = source.msgSource == null ? "" : source.msgSource;

        setIntFieldAny(quoteItem, quoteAppMsgSourceType(classLoader, source.type), "d");
        setLongFieldAny(quoteItem, firstPositiveLong(source.msgSvrId, source.msgId), "e");
        setObjectField(quoteItem, firstNonBlank(source.talker, ""), "f");
        setObjectField(quoteItem, sender, "g");
        setObjectField(quoteItem, sender, "h");
        setObjectField(quoteItem, msgSource, "i");
        setObjectField(quoteItem, quoteContent, "m");
        setObjectField(quoteItem, msgSource, "n");
        setObjectField(quoteItem, quoteStrID(classLoader, msgSource), "p");
        setLongFieldAny(quoteItem, source.createTime, "q");
        return quoteItem;
    }

    private static Object newMsgQuoteItem(ClassLoader classLoader) throws Exception {
        Class<?> quoteItemClass = findClass(classLoader, "com.tencent.mm.plugin.msgquote.model.MsgQuoteItem");
        try {
            Constructor<?> ctor = quoteItemClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException ignored) {
            Parcel parcel = Parcel.obtain();
            try {
                parcel.writeInt(0);
                parcel.writeLong(0L);
                parcel.writeString("");
                parcel.writeString("");
                parcel.writeString("");
                parcel.writeString("");
                parcel.writeString("");
                parcel.writeString("");
                parcel.writeInt(0);
                parcel.writeString("");
                parcel.writeLong(0L);
                parcel.writeString("");
                parcel.setDataPosition(0);
                Constructor<?> ctor = quoteItemClass.getDeclaredConstructor(Parcel.class);
                ctor.setAccessible(true);
                return ctor.newInstance(parcel);
            } finally {
                parcel.recycle();
            }
        }
    }

    private static int quoteAppMsgSourceType(ClassLoader classLoader, int messageType) {
        try {
            Class<?> appMsgLogicClass = findClass(classLoader, "com.tencent.mm.pluginsdk.model.app.k0");
            Object value = findMethod(appMsgLogicClass, "c", int.class).invoke(null, messageType);
            if (value instanceof Number) {
                int mapped = ((Number) value).intValue();
                return mapped > 0 ? mapped : messageType;
            }
        } catch (Throwable t) {
            log("quote source type mapping skipped: " + shortError(t));
        }
        return messageType;
    }

    private static String quoteSender(QuoteSource source) {
        if (source == null) {
            return "";
        }
        String sender = isChatroomTalker(source.talker) ? firstNonBlank(source.senderWxid, extractChatroomSender(source.content)) : "";
        return firstNonBlank(sender, source.talker);
    }

    private static String quoteStrID(ClassLoader classLoader, String msgSource) {
        if (isBlank(msgSource)) {
            return "";
        }
        try {
            Object value = findMethod(findClass(classLoader, "c01.ia"), "t", String.class).invoke(null, msgSource);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean insertMsgQuoteRecord(ClassLoader classLoader, long newMsgId, QuoteSource source) throws Exception {
        Class<?> pluginInterface = findClass(classLoader, "aa0.e");
        Object plugin = findMethod(findClass(classLoader, "i95.n0"), "c", Class.class).invoke(null, pluginInterface);
        if (plugin == null) {
            throw new IllegalStateException("msgquote plugin service is unavailable");
        }
        Object storage = findNoArgMethod(plugin.getClass(), "Di").invoke(plugin);
        if (storage == null) {
            throw new IllegalStateException("MsgQuote storage is unavailable");
        }

        Class<?> recordClass = findClass(classLoader, "ui3.b");
        Object record = recordClass.getDeclaredConstructor().newInstance();
        setLongFieldAny(record, newMsgId, "field_msgId");
        setLongFieldAny(record, source.msgId, "field_quotedMsgId");
        setLongFieldAny(record, source.msgSvrId, "field_quotedMsgSvrId");
        setObjectField(record, source.talker, "field_quotedMsgTalker");
        Object result = findMethod(storage.getClass(), "J0", recordClass).invoke(storage, record);
        return Boolean.TRUE.equals(result);
    }

    private static long longPairField(Object pair, String fieldName) throws Exception {
        Field field = findFieldAny(pair.getClass(), fieldName);
        Object value = field.get(pair);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private static void sendViaSendImgMgr(ClassLoader classLoader, String wxid, File imageFile) throws Exception {
        Context context = APP_CONTEXT;
        if (context == null) {
            Object application = currentApplication();
            if (application instanceof Context) {
                context = (Context) application;
            }
        }
        if (context == null) {
            throw new IllegalStateException("WeChat context is not available");
        }
        Class<?> accessorClass = findClass(classLoader, "tg3.t1");
        Method accessor = findNoArgMethod(accessorClass, "a");
        Object service = accessor.invoke(null);
        if (service == null) {
            throw new IllegalStateException("tg3.t1.a returned null");
        }
        Class<?> forwardInfoClass = findClass(classLoader, "c01.h7");
        try {
            Method send = findMethod(service.getClass(), "Vi",
                    Context.class, String.class, String.class, int.class, String.class, String.class, forwardInfoClass);
            send.invoke(service, context, wxid, imageFile.getAbsolutePath(), 1, "", "", null);
            return;
        } catch (NoSuchMethodException unavailable) {
            Class<?> scanCodeInfoClass = findClass(classLoader, "com.tencent.mm.modelscan.ScanCodeInfo");
            Class<?> sourceImgInfoClass = findClass(classLoader, "com.tencent.mm.modelmulti.SourceImgInfo");
            Method send = findMethod(service.getClass(), "aj",
                    Context.class, String.class, String.class, int.class, String.class, String.class,
                    forwardInfoClass, scanCodeInfoClass, sourceImgInfoClass);
            send.invoke(service, context, wxid, imageFile.getAbsolutePath(), 1, "", "", null, null, null);
        }
    }

    private static void sendViaVideoSendFeature(ClassLoader classLoader, String wxid, File videoFile) throws Exception {
        Class<?> r2Class = findClass(classLoader, "vf0.r2");
        Object crossParams = defaultVideoCrossParams(classLoader, r2Class);

        Class<?> forwardInfoClass = findClass(classLoader, "c01.h7");
        Class<?> u2Class = findClass(classLoader, "vf0.u2");
        Constructor<?> elementCtor = findConstructor(
                u2Class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                int.class,
                r2Class,
                forwardInfoClass);
        Object element = elementCtor.newInstance(
                HookMediaServices.videoBaseName(videoFile),
                videoFile.getAbsolutePath(),
                "",
                false,
                0,
                crossParams,
                null);

        Class<?> i3Class = findClass(classLoader, "vf0.i3");
        Class<?> h3Class = findClass(classLoader, "vf0.h3");
        Constructor<?> paramsCtor = findConstructor(h3Class, String.class, u2Class, boolean.class, i3Class);
        Object params = paramsCtor.newInstance(wxid, element, false, null);

        Class<?> serviceInterface = findClass(classLoader, "wf0.b2");
        Method getService = findMethod(findClass(classLoader, "i95.n0"), "c", Class.class);
        Object videoService = getService.invoke(null, serviceInterface);
        if (videoService == null) {
            throw new IllegalStateException("wf0.b2 service is not available");
        }
        Method buildTask = findMethod(videoService.getClass(), "bj", h3Class);
        Object task = buildTask.invoke(videoService, params);
        if (task == null) {
            throw new IllegalStateException("video task build returned null");
        }

        Class<?> taskInterface = findClass(classLoader, "qi3.b0");
        Class<?> dispatcherClass = findClass(classLoader, "qi3.x");
        Object dispatcher = findField(dispatcherClass, "a").get(null);
        if (dispatcher == null) {
            throw new IllegalStateException("video task dispatcher is not available");
        }
        Method sendAsync = findMethod(dispatcherClass, "d", taskInterface);
        sendAsync.invoke(dispatcher, task);
        log("sendVideo invoked VideoSendFeature task=" + task.getClass().getName() + " size=" + videoFile.length());
    }

    private static void sendViaVoiceSendTask(ClassLoader classLoader, String wxid, File voiceFile, int durationMs) throws Exception {
        String voiceFileName = HookMediaServices.voiceBaseName(voiceFile);
        String preparedPath = HookWechatVoiceFilePreparer.prepare(
                classLoader,
                voiceFileName,
                voiceFile,
                WECHAT_VOICE_FILE_REFLECTION,
                HookEntry::log);

        Class<?> paramsClass = findClass(classLoader, "cg0.d");
        Constructor<?> paramsCtor = findConstructor(paramsClass, String.class, String.class);
        Object params = paramsCtor.newInstance(wxid, voiceFileName);
        setOptionalIntField(params, Math.max(1000, durationMs), "h");
        setOptionalIntField(params, 0, "i");

        Class<?> taskClass = findClass(classLoader, "jg0.x");
        Constructor<?> taskCtor = findConstructor(taskClass, paramsClass);
        Object task = taskCtor.newInstance(params);

        Class<?> serviceInterface = findClass(classLoader, "dg0.f");
        Object service = findMethod(findClass(classLoader, "i95.n0"), "c", Class.class).invoke(null, serviceInterface);
        if (service == null) {
            throw new IllegalStateException("VoiceMsgFeatureService is not available");
        }
        Class<?> taskInterface = findClass(classLoader, "qi3.b0");
        Method send = findMethod(service.getClass(), "hj", taskInterface);
        send.invoke(service, task);
        log("sendVoice invoked VoiceMsgSendTask fileName=" + voiceFileName + " path=" + preparedPath + " size=" + voiceFile.length());
    }

    private static void sendViaFileSendLogic(ClassLoader classLoader, String wxid, File file) throws Exception {
        Class<?> paramsClass = findClass(classLoader, "ut.s0");
        Constructor<?> paramsCtor = findConstructor(paramsClass, String.class, String.class);
        Object params = paramsCtor.newInstance(wxid, file.getAbsolutePath());

        Class<?> fileSendLogicHolder = findClass(classLoader, "dk5.w");
        Object logic = findField(fileSendLogicHolder, "a").get(null);
        if (logic == null) {
            throw new IllegalStateException("FileSendLogic is not available");
        }
        Method send = findMethod(logic.getClass(), "c", paramsClass);
        send.invoke(logic, params);
        log("sendFile invoked FileSendLogic size=" + file.length());
    }

    private static void sendViaEmojiMgr(ClassLoader classLoader, String wxid, String emojiMd5) throws Exception {
        Object emojiInfo = loadEmojiInfoByMd5(classLoader, emojiMd5);
        if (emojiInfo == null) {
            throw new IllegalStateException("emoji info was not found in local WeChat storage");
        }
        Class<?> featureClass = findClass(classLoader, "com.tencent.mm.feature.emoji.b0");
        Object feature = findMethod(findClass(classLoader, "i95.n0"), "c", Class.class).invoke(null, featureClass);
        if (feature == null) {
            throw new IllegalStateException("EmojiFeatureService is not available");
        }
        Object emojiMgr = findNoArgMethod(feature.getClass(), "Ni").invoke(feature);
        if (emojiMgr == null) {
            throw new IllegalStateException("EmojiMgrImpl is not available");
        }
        Class<?> emojiInfoClass = findClass(classLoader, "com.tencent.mm.storage.emotion.EmojiInfo");
        Class<?> messageClass = findClass(classLoader, "com.tencent.mm.storage.f9");
        Class<?> callbackClass = findClass(classLoader, "r15.b");
        Method send = findMethod(emojiMgr.getClass(), "Y", String.class, emojiInfoClass, messageClass, callbackClass, int.class);
        send.invoke(emojiMgr, wxid, emojiInfoClass.cast(emojiInfo), null, null, emojiOrdinal(emojiInfo));
        log("sendEmoji invoked EmojiMgrImpl");
    }

    private static void logEmojiInfoDiagnostic(String emojiMd5) {
        EMOJI_INFO_DIAGNOSTIC_REPORTER.report(emojiMd5);
    }

    private static Object loadEmojiInfoByMd5(ClassLoader classLoader, String emojiMd5) throws Exception {
        if (isBlank(emojiMd5)) {
            return null;
        }
        Object storageRoot = findNoArgMethod(findClass(classLoader, "com.tencent.mm.storage.n5"), "f").invoke(null);
        Object storage = findNoArgMethod(storageRoot.getClass(), "c").invoke(storageRoot);
        Object emojiInfo = findMethod(storage.getClass(), "u1", String.class).invoke(storage, emojiMd5.trim());
        if (emojiInfo != null) {
            return emojiInfo;
        }
        try {
            Object service = findMethod(findClass(classLoader, "i95.n0"), "c", Class.class)
                    .invoke(null, findClass(classLoader, "k12.s"));
            if (service != null) {
                Object repository = findNoArgMethod(service.getClass(), "Bi").invoke(service);
                if (repository != null) {
                    return findMethod(repository.getClass(), "y", String.class).invoke(repository, emojiMd5.trim());
                }
            }
        } catch (Throwable t) {
            log("emoji repository fallback lookup skipped: " + shortError(t));
        }
        return null;
    }

    private static int emojiOrdinal(Object emojiInfo) {
        if (emojiInfo == null) {
            return 0;
        }
        try {
            Object value = findNoArgMethod(emojiInfo.getClass(), "c0").invoke(emojiInfo);
            if (value == null) {
                return 0;
            }
            Object ordinal = findNoArgMethod(value.getClass(), "ordinal").invoke(value);
            if (ordinal instanceof Number) {
                return ((Number) ordinal).intValue();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static void sendViaLocationFeature(ClassLoader classLoader, String wxid, double latitude, double longitude, int scale, String label, String poiName, String infoURL, String poiID, boolean fromPoiList, String poiTips) throws Exception {
        Class<?> paramsClass = findClass(classLoader, "y80.q1");
        Constructor<?> paramsCtor = findConstructor(paramsClass, String.class);
        Object params = paramsCtor.newInstance(wxid);
        setDoubleFieldAny(params, latitude, "d");
        setDoubleFieldAny(params, longitude, "e");
        setIntFieldAny(params, scale, "f");
        setObjectField(params, label == null ? "" : label, "g");
        setObjectField(params, poiName == null ? "" : poiName, "h");
        setObjectField(params, infoURL == null ? "" : infoURL, "i");
        setObjectField(params, poiID == null ? "" : poiID, "j");
        setBooleanFieldAny(params, fromPoiList, "k");
        setObjectField(params, poiTips == null ? "" : poiTips, "l");

        Method getService = findMethod(findClass(classLoader, "i95.n0"), "c", Class.class);
        Object service = getService.invoke(null, findClass(classLoader, "z80.h0"));
        if (service == null) {
            service = getService.invoke(null, findClass(classLoader, "y80.p0"));
        }
        if (service == null) {
            throw new IllegalStateException("LocationMsgFeatureService is not available");
        }
        Method buildTask = findMethod(service.getClass(), "Zi", paramsClass);
        Object task = buildTask.invoke(service, params);
        if (task == null) {
            throw new IllegalStateException("location task build returned null");
        }
        Method send = findMethod(service.getClass(), "bj", findClass(classLoader, "qi3.b0"));
        send.invoke(service, task);
        log("sendLocation invoked LocationMsgSendTask scale=" + scale);
    }

    private static Object defaultVideoCrossParams(ClassLoader classLoader, Class<?> r2Class) throws Exception {
        Class<?> f7Class = findClass(classLoader, "c01.f7");
        Class<?> uf6Class = findClass(classLoader, "r45.uf6");
        Class<?> xz6Class = findClass(classLoader, "r45.xz6");
        Class<?> vh4Class = findClass(classLoader, "r45.vh4");
        Class<?> p2Class = findClass(classLoader, "vf0.p2");
        Class<?> r15dClass = findClass(classLoader, "r15.d");
        Constructor<?> ctor = findConstructor(
                r2Class,
                f7Class,
                uf6Class,
                String.class,
                xz6Class,
                String.class,
                vh4Class,
                boolean.class,
                p2Class,
                String.class,
                r15dClass,
                boolean.class,
                boolean.class);
        return ctor.newInstance(null, null, "", null, "", null, false, null, "", null, false, false);
    }

    private static Object ensureMessageDatabase(BridgeConfig config) {
        Object db = LAST_DATABASE;
        if (db != null && hasMessageTable(db)) {
            return db;
        }
        if (db != null) {
            log("cached WeChat database skipped: message table unavailable path=" + databasePath(db));
            if (LAST_DATABASE == db) {
                LAST_DATABASE = null;
            }
        }
        if (config == null) {
            return null;
        }
        db = findContactDatabaseOnMainThread(config);
        if (db != null && hasMessageTable(db)) {
            LAST_DATABASE = db;
            return db;
        }
        if (db != null) {
            log("discovered WeChat database skipped: message table unavailable path=" + databasePath(db));
            if (LAST_DATABASE == db) {
                LAST_DATABASE = null;
            }
        }
        return null;
    }

    private static boolean hasMessageTable(Object db) {
        if (db == null) {
            return false;
        }
        Object cursor = null;
        try {
            cursor = rawQuery(db, "SELECT 1 FROM message LIMIT 1", new String[]{});
            return cursor != null;
        } catch (Throwable ignored) {
            return false;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static long waitForOutgoingImageMessage(BridgeConfig config, String wxid, long afterMsgId, long timeoutMs) {
        return waitForOutgoingMediaMessage(config, wxid, afterMsgId, timeoutMs, "image", "3");
    }

    private static long waitForOutgoingMediaMessage(BridgeConfig config, String wxid, long afterMsgId, long timeoutMs, String kind, String typeFilter) {
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
        Throwable lastError = null;
        while (System.currentTimeMillis() <= deadline) {
            try {
                Object db = ensureMessageDatabase(config);
                if (db != null) {
                    long msgId = findOutgoingMediaMessageId(db, wxid, afterMsgId, typeFilter);
                    if (msgId > 0) {
                        return msgId;
                    }
                }
            } catch (Throwable t) {
                lastError = t;
            }
            try {
                Thread.sleep(350L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (lastError != null) {
            log(kind + " send DB verification failed: " + shortError(lastError));
        }
        return 0L;
    }

    private static long waitForOutgoingAppMsgMessage(BridgeConfig config, String wxid, long afterMsgId, long timeoutMs, String kind, int... appMsgTypes) {
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
        Throwable lastError = null;
        while (System.currentTimeMillis() <= deadline) {
            try {
                Object db = ensureMessageDatabase(config);
                if (db != null) {
                    if (appMsgTypes != null) {
                        for (int appMsgType : appMsgTypes) {
                            long msgId = findOutgoingAppMsgMessageId(db, wxid, afterMsgId, appMsgType);
                            if (msgId > 0) {
                                return msgId;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                lastError = t;
            }
            try {
                Thread.sleep(350L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (lastError != null) {
            log(kind + " appmsg send DB verification failed: " + shortError(lastError));
        }
        return 0L;
    }

    private static long waitForOutgoingTextMessage(BridgeConfig config, String wxid, String text, long afterMsgId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
        Throwable lastError = null;
        while (System.currentTimeMillis() <= deadline) {
            try {
                Object db = ensureMessageDatabase(config);
                if (db != null) {
                    long msgId = findOutgoingTextMessageId(db, wxid, text, afterMsgId);
                    if (msgId > 0) {
                        return msgId;
                    }
                }
            } catch (Throwable t) {
                lastError = t;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (lastError != null) {
            log("text send DB verification failed: " + shortError(lastError));
        }
        return 0L;
    }

    private static void postVerifiedOutgoingMessage(BridgeConfig config, Object db, long msgId, String kind) {
        if (config == null || db == null || msgId <= 0L) {
            return;
        }
        try {
            MessagePayload payload = loadMessagePayloadById(config, db, msgId);
            if (payload == null) {
                log("send " + kind + " verified msgId=" + msgId + " but message row was not reportable");
                return;
            }
            post(config, payload);
            MEDIA_RETRY_SCHEDULER.rememberUploaded(payload);
            scheduleVerifiedOutgoingMediaRetryIfNeeded(config, payload);
        } catch (Throwable t) {
            log("send " + kind + " verified message upload failed msgId=" + msgId + " error=" + shortError(t));
        }
    }

    private static void scheduleVerifiedOutgoingMediaRetryIfNeeded(BridgeConfig config, MessagePayload payload) {
        if (payload == null) {
            return;
        }
        MEDIA_RETRY_SCHEDULER.scheduleIfNeeded(
                config != null && config.mediaUploadEnabled,
                payload.chatRecordId > 0L ? Long.valueOf(payload.chatRecordId) : null,
                null,
                payload.chatId,
                firstNonBlank(payload.rawXml, payload.text),
                Integer.valueOf("sent".equals(payload.direction) ? 1 : 0),
                payload.createTime > 0L ? Long.valueOf(payload.createTime) : null,
                payload.messageType,
                "",
                payload);
    }

    private static MessagePayload loadMessagePayloadById(BridgeConfig config, Object db, long msgId) throws Exception {
        int queryMode = 2;
        Object cursor;
        try {
            cursor = rawQuery(db, ""
                    + "SELECT COALESCE(msgSvrId,0),talker,COALESCE(content,''),isSend,createTime,type,COALESCE(imgPath,'') "
                    + "FROM message "
                    + "WHERE msgId = ? AND talker IS NOT NULL AND talker <> '' "
                    + "LIMIT 1", new String[]{String.valueOf(msgId)});
        } catch (Throwable t) {
            try {
                queryMode = 1;
                cursor = rawQuery(db, ""
                        + "SELECT talker,COALESCE(content,''),isSend,createTime,type,COALESCE(imgPath,'') "
                        + "FROM message "
                        + "WHERE msgId = ? AND talker IS NOT NULL AND talker <> '' "
                        + "LIMIT 1", new String[]{String.valueOf(msgId)});
            } catch (Throwable ignored) {
                queryMode = 0;
                cursor = rawQuery(db, ""
                        + "SELECT talker,COALESCE(content,''),isSend,createTime,type "
                        + "FROM message "
                        + "WHERE msgId = ? AND talker IS NOT NULL AND talker <> '' "
                        + "LIMIT 1", new String[]{String.valueOf(msgId)});
            }
        }
        if (cursor == null) {
            return null;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (!Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return null;
            }
            int column = 0;
            long msgSvrId = queryMode == 2 ? longColumn(cursor, column++) : 0L;
            String talker = stringColumn(cursor, column++);
            String content = stringColumn(cursor, column++);
            int isSend = intColumn(cursor, column++);
            long createTime = normalizeCreateTime(longColumn(cursor, column++));
            int type = intColumn(cursor, column++);
            String imgPath = queryMode >= 1 ? stringColumn(cursor, column) : "";
            if (!shouldReportMessage(talker, content, type)) {
                return null;
            }
            int messageType = type <= 0 ? 1 : type;
            Long serverId = msgSvrId > 0L ? Long.valueOf(msgSvrId) : null;
            String mediaHint = MEDIA_ATTACHMENT_CONTROLLER.resolveMediaHint(
                    db,
                    messageType,
                    Long.valueOf(msgId),
                    serverId,
                    imgPath);
            return buildMessagePayload(config, talker, content, isSend == 1 ? Integer.valueOf(1) : Integer.valueOf(0), Long.valueOf(msgId), serverId, Long.valueOf(createTime), messageType, mediaHint);
        } finally {
            closeQuietly(cursor);
        }
    }

    private static MessagePayload loadRevokeUpdatePayload(
            BridgeConfig config,
            Object db,
            ContentValues values,
            Object[] args) throws Exception {
        Long msgId = positiveLong(contentValueLong(values, "msgId"));
        if (msgId == null) {
            msgId = updateLongIdentifier(args, "msgId");
        }
        Long msgSvrId = positiveLong(contentValueLong(values, "msgSvrId"));
        if (msgSvrId == null) {
            msgSvrId = updateLongIdentifier(args, "msgSvrId");
        }

        MessagePayload payload = null;
        if (db != null && msgId != null) {
            payload = loadRevokeUpdatePayloadByColumn(config, db, "msgId", msgId.longValue(), values);
        }
        if (payload == null && db != null && msgSvrId != null) {
            payload = loadRevokeUpdatePayloadByColumn(config, db, "msgSvrId", msgSvrId.longValue(), values);
        }
        if (payload != null) {
            return payload;
        }
        return buildRevokePayloadFromValues(config, values, msgId);
    }

    private static MessagePayload loadRevokeUpdatePayloadByColumn(
            BridgeConfig config,
            Object db,
            String column,
            long value,
            ContentValues values) throws Exception {
        if (!"msgId".equals(column) && !"msgSvrId".equals(column)) {
            return null;
        }
        Object cursor = rawQuery(db, ""
                + "SELECT msgId,COALESCE(msgSvrId,0),talker,COALESCE(content,''),isSend,createTime,type,COALESCE(imgPath,'') "
                + "FROM message "
                + "WHERE " + column + " = ? AND talker IS NOT NULL AND talker <> '' "
                + "ORDER BY msgId DESC "
                + "LIMIT 1", new String[]{String.valueOf(value)});
        if (cursor == null) {
            return null;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (!Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return null;
            }
            int columnIndex = 0;
            long msgId = longColumn(cursor, columnIndex++);
            long msgSvrId = longColumn(cursor, columnIndex++);
            String talker = stringColumn(cursor, columnIndex++);
            String rowContent = stringColumn(cursor, columnIndex++);
            int isSend = intColumn(cursor, columnIndex++);
            long createTime = normalizeCreateTime(longColumn(cursor, columnIndex++));
            int rowType = intColumn(cursor, columnIndex++);
            String imgPath = stringColumn(cursor, columnIndex);

            String content = firstNonBlank(values.getAsString("content"), rowContent);
            if (isBlank(talker) || !containsRevokePayload(content)) {
                return null;
            }
            Integer updateType = contentValueInteger(values, "type");
            int messageType = updateType != null && updateType > 0 ? updateType.intValue() : rowType;
            if (containsRevokePayload(content) && messageType != 10000) {
                messageType = 10000;
            }
            Long serverId = msgSvrId > 0L ? Long.valueOf(msgSvrId) : null;
            String mediaHint = MEDIA_ATTACHMENT_CONTROLLER.resolveMediaHint(
                    db,
                    messageType,
                    Long.valueOf(msgId),
                    serverId,
                    imgPath);
            return buildMessagePayload(config, talker, content, Integer.valueOf(isSend == 1 ? 1 : 0), Long.valueOf(msgId), serverId, Long.valueOf(createTime), messageType, mediaHint);
        } finally {
            closeQuietly(cursor);
        }
    }

    private static MessagePayload buildRevokePayloadFromValues(BridgeConfig config, ContentValues values, Long msgId) {
        String talker = values.getAsString("talker");
        String content = values.getAsString("content");
        if (isBlank(talker) || !containsRevokePayload(content)) {
            return null;
        }
        Integer type = contentValueInteger(values, "type");
        int messageType = type != null && type > 0 ? type.intValue() : 10000;
        if (messageType != 10000) {
            messageType = 10000;
        }
        return buildMessagePayload(
                config,
                talker,
                content,
                contentValueInteger(values, "isSend"),
                msgId,
                contentValueLong(values, "msgSvrId"),
                contentValueLong(values, "createTime"),
                messageType,
                values.getAsString("imgPath"));
    }

    private static void markRevokeUpdatePayload(MessagePayload payload) {
        if (payload == null) {
            return;
        }
        long recordId = firstPositiveLong(payload.chatRecordId, payload.eventId);
        if (recordId <= 0L) {
            return;
        }
        payload.id = String.valueOf(recordId) + ":revoke";
        payload.eventId = recordId;
        payload.chatRecordId = recordId;
    }

    private static boolean rememberRevokeUpdate(MessagePayload payload) {
        long recordId = payload == null ? 0L : firstPositiveLong(payload.chatRecordId, payload.eventId);
        if (recordId <= 0L) {
            return true;
        }
        synchronized (REVOKE_UPDATE_REPORTED_IDS) {
            return REVOKE_UPDATE_REPORTED_IDS.add(Long.valueOf(recordId));
        }
    }

    private static void rememberRevokeConfirmed(long msgId) {
        if (msgId <= 0L) {
            return;
        }
        synchronized (REVOKE_CONFIRMED_IDS) {
            REVOKE_CONFIRMED_IDS.add(Long.valueOf(msgId));
            if (REVOKE_CONFIRMED_IDS.size() > 1024) {
                REVOKE_CONFIRMED_IDS.clear();
                REVOKE_CONFIRMED_IDS.add(Long.valueOf(msgId));
            }
        }
    }

    private static boolean hasRevokeConfirmed(long msgId) {
        if (msgId <= 0L) {
            return false;
        }
        synchronized (REVOKE_CONFIRMED_IDS) {
            return REVOKE_CONFIRMED_IDS.contains(Long.valueOf(msgId));
        }
    }

    private static Long contentValueLong(ContentValues values, String key) {
        if (values == null || isBlank(key)) {
            return null;
        }
        try {
            return values.getAsLong(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer contentValueInteger(ContentValues values, String key) {
        if (values == null || isBlank(key)) {
            return null;
        }
        try {
            return values.getAsInteger(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Long positiveLong(Long value) {
        if (value == null || value.longValue() <= 0L) {
            return null;
        }
        return value;
    }

    private static Long updateLongIdentifier(Object[] args, String columnName) {
        String whereClause = updateWhereClause(args);
        if (isBlank(whereClause) || isBlank(columnName)) {
            return null;
        }
        String lowerWhere = whereClause.toLowerCase(Locale.US);
        String lowerColumn = columnName.toLowerCase(Locale.US);
        if (!lowerWhere.contains(lowerColumn)) {
            return null;
        }
        String[] whereArgs = updateWhereArgs(args);
        if (whereArgs != null) {
            for (String arg : whereArgs) {
                Long value = parsePositiveLong(arg);
                if (value != null) {
                    return value;
                }
            }
        }
        return parsePositiveLongAfterColumn(whereClause, lowerColumn);
    }

    private static String updateWhereClause(Object[] args) {
        boolean afterValues = false;
        if (args == null) {
            return "";
        }
        for (Object arg : args) {
            if (arg instanceof ContentValues) {
                afterValues = true;
                continue;
            }
            if (afterValues && arg instanceof String) {
                return String.valueOf(arg);
            }
        }
        return "";
    }

    private static String[] updateWhereArgs(Object[] args) {
        boolean afterValues = false;
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof ContentValues) {
                afterValues = true;
                continue;
            }
            if (afterValues && arg instanceof String[]) {
                return (String[]) arg;
            }
        }
        return null;
    }

    private static Long parsePositiveLongAfterColumn(String whereClause, String lowerColumn) {
        String lowerWhere = whereClause.toLowerCase(Locale.US);
        int index = lowerWhere.indexOf(lowerColumn);
        while (index >= 0) {
            int start = index + lowerColumn.length();
            while (start < whereClause.length() && !Character.isDigit(whereClause.charAt(start))) {
                start++;
            }
            int end = start;
            while (end < whereClause.length() && Character.isDigit(whereClause.charAt(end))) {
                end++;
            }
            if (end > start) {
                Long value = parsePositiveLong(whereClause.substring(start, end));
                if (value != null) {
                    return value;
                }
            }
            index = lowerWhere.indexOf(lowerColumn, index + lowerColumn.length());
        }
        return null;
    }

    private static Long parsePositiveLong(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0L ? Long.valueOf(parsed) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long findOutgoingMediaMessageId(Object db, String wxid, long afterMsgId, String typeFilter) throws Exception {
        Object cursor = rawQuery(db, ""
                + "SELECT msgId "
                + "FROM message "
                + "WHERE msgId > ? AND talker = ? AND isSend = 1 AND type IN (" + typeFilter + ") "
                + "ORDER BY msgId DESC "
                + "LIMIT 1", new String[]{String.valueOf(afterMsgId), wxid});
        if (cursor == null) {
            return 0L;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return longColumn(cursor, 0);
            }
            return 0L;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static long findOutgoingTextMessageId(Object db, String wxid, String text, long afterMsgId) throws Exception {
        Object cursor = rawQuery(db, ""
                + "SELECT msgId "
                + "FROM message "
                + "WHERE msgId > ? AND talker = ? AND isSend = 1 AND type = 1 AND COALESCE(content,'') = ? "
                + "ORDER BY msgId DESC "
                + "LIMIT 1", new String[]{String.valueOf(afterMsgId), wxid, text});
        if (cursor == null) {
            return 0L;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return longColumn(cursor, 0);
            }
            return 0L;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static long findOutgoingAppMsgMessageId(Object db, String wxid, long afterMsgId, int appMsgType) throws Exception {
        Object cursor = rawQuery(db, ""
                + "SELECT msgId "
                + "FROM message "
                + "WHERE msgId > ? AND talker = ? AND isSend = 1 AND type = ? AND content LIKE ? "
                + "ORDER BY msgId DESC "
                + "LIMIT 1", new String[]{
                String.valueOf(afterMsgId),
                wxid,
                String.valueOf(MESSAGE_TYPE_APPMSG),
                "%<type>" + appMsgType + "</type>%"});
        if (cursor == null) {
            return 0L;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return longColumn(cursor, 0);
            }
            return 0L;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static long messageServerIdByLocalId(Object db, long msgId) throws Exception {
        if (db == null || msgId <= 0L) {
            return 0L;
        }
        Object cursor = rawQuery(db, ""
                + "SELECT COALESCE(msgSvrId,0) "
                + "FROM message "
                + "WHERE msgId = ? "
                + "LIMIT 1", new String[]{String.valueOf(msgId)});
        if (cursor == null) {
            return 0L;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return longColumn(cursor, 0);
            }
            return 0L;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static long waitForRevokeConfirmation(
            BridgeConfig config,
            String talker,
            long targetMsgId,
            long afterMsgId,
            long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (hasRevokeConfirmed(targetMsgId)) {
                return targetMsgId;
            }
            Object db = ensureMessageDatabase(config);
            if (db != null) {
                try {
                    long revokeMsgId = findRevokeSystemMessageId(db, talker, afterMsgId);
                    if (revokeMsgId > 0L) {
                        return revokeMsgId;
                    }
                } catch (Throwable t) {
                    log("revoke confirmation lookup failed: " + shortError(t));
                }
            }
            try {
                Thread.sleep(350L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0L;
            }
        }
        return 0L;
    }

    private static long findRevokeSystemMessageId(Object db, String talker, long afterMsgId) throws Exception {
        Object cursor = rawQuery(db, ""
                + "SELECT msgId "
                + "FROM message "
                + "WHERE msgId > ? AND talker = ? AND type = 10000 "
                + "AND (COALESCE(content,'') LIKE '%revokemsg%' OR COALESCE(content,'') LIKE '%撤回%') "
                + "ORDER BY msgId DESC "
                + "LIMIT 1", new String[]{String.valueOf(afterMsgId), talker});
        if (cursor == null) {
            return 0L;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return longColumn(cursor, 0);
            }
            return 0L;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static String normalizeRecordItemXML(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = stripCData(value);
        String lower = value.toLowerCase(Locale.US);
        if (lower.startsWith("<recorditem")) {
            int start = value.indexOf('>');
            int end = lower.lastIndexOf("</recorditem>");
            if (start >= 0 && end > start) {
                value = value.substring(start + 1, end).trim();
                value = stripCData(value);
            }
        }
        return value.trim();
    }

    private static List<Long> readSourceChatRecordIds(JSONArray first, JSONArray second) {
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        appendSourceChatRecordIds(ids, seen, first);
        appendSourceChatRecordIds(ids, seen, second);
        return ids;
    }

    private static void appendSourceChatRecordIds(List<Long> ids, Set<Long> seen, JSONArray values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.length() && ids.size() < MAX_CHAT_HISTORY_SOURCE_ITEMS; i++) {
            long id = values.optLong(i, 0L);
            if (id <= 0L || seen.contains(Long.valueOf(id))) {
                continue;
            }
            ids.add(Long.valueOf(id));
            seen.add(Long.valueOf(id));
        }
    }

    private static String buildChatHistoryRecordItemXML(Object db, List<Long> sourceMsgIds) throws Exception {
        if (sourceMsgIds == null || sourceMsgIds.isEmpty()) {
            return "";
        }
        List<ChatHistorySource> sources = new ArrayList<>();
        for (Long id : sourceMsgIds) {
            ChatHistorySource source = loadChatHistorySource(db, id == null ? 0L : id.longValue());
            if (source == null) {
                throw new IllegalStateException("source chat record was not found in local WeChat message database");
            }
            sources.add(source);
        }
        StringBuilder xml = new StringBuilder();
        xml.append("<recordinfo>");
        xml.append("<title>").append(xmlEscape("聊天记录")).append("</title>");
        xml.append("<desc>").append(xmlEscape("共" + sources.size() + "条")).append("</desc>");
        xml.append("<datalist count=\"").append(sources.size()).append("\">");
        int index = 0;
        for (ChatHistorySource source : sources) {
            index++;
            appendChatHistoryDataItem(xml, source, index);
        }
        xml.append("</datalist>");
        xml.append("</recordinfo>");
        String value = xml.toString();
        if (value.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw new IllegalStateException("generated recorditem_xml exceeds 1MB");
        }
        return value;
    }

    private static ChatHistorySource loadChatHistorySource(Object db, long msgId) throws Exception {
        if (msgId <= 0L) {
            return null;
        }
        ChatHistorySource source = queryChatHistorySource(db, msgId, true);
        if (source != null) {
            return source;
        }
        return queryChatHistorySource(db, msgId, false);
    }

    private static ChatHistorySource queryChatHistorySource(Object db, long msgId, boolean includeMsgSource) throws Exception {
        String msgSourceSelect = includeMsgSource ? ",COALESCE(msgSource,'')" : ",''";
        Object cursor;
        try {
            cursor = rawQuery(db, ""
                    + "SELECT msgId,COALESCE(msgSvrId,0),talker,COALESCE(content,''),isSend,createTime,type"
                    + msgSourceSelect + " "
                    + "FROM message "
                    + "WHERE msgId = ? "
                    + "LIMIT 1", new String[]{String.valueOf(msgId)});
        } catch (Throwable t) {
            if (includeMsgSource) {
                return null;
            }
            throw t;
        }
        if (cursor == null) {
            return null;
        }
        try {
            Method moveToFirst = findNoArgMethod(cursor.getClass(), "moveToFirst");
            if (!Boolean.TRUE.equals(moveToFirst.invoke(cursor))) {
                return null;
            }
            ChatHistorySource source = new ChatHistorySource();
            int column = 0;
            source.msgId = longColumn(cursor, column++);
            source.msgSvrId = longColumn(cursor, column++);
            source.talker = stringColumn(cursor, column++);
            source.content = stringColumn(cursor, column++);
            source.isSend = intColumn(cursor, column++);
            source.createTime = normalizeCreateTime(longColumn(cursor, column++));
            source.type = intColumn(cursor, column++);
            source.msgSource = stringColumn(cursor, column);
            return source.msgId > 0L ? source : null;
        } finally {
            closeQuietly(cursor);
        }
    }

    private static void appendChatHistoryDataItem(StringBuilder xml, ChatHistorySource source, int index) {
        int dataType = chatHistoryDataType(source);
        String title = chatHistoryDataTitle(source);
        String description = chatHistoryDataDescription(source, title);
        xml.append("<dataitem datatype=\"").append(dataType).append("\" dataid=\"")
                .append(xmlEscape("msg_" + source.msgId + "_" + index)).append("\">");
        if (!isBlank(title)) {
            xml.append("<datatitle>").append(xmlEscape(title)).append("</datatitle>");
        }
        if (!isBlank(description)) {
            xml.append("<datadesc>").append(xmlEscape(description)).append("</datadesc>");
        }
        xml.append("<sourcetime>").append(source.createTime).append("</sourcetime>");
        xml.append("</dataitem>");
    }

    private static int chatHistoryDataType(ChatHistorySource source) {
        if (source == null) {
            return 1;
        }
        switch (source.type) {
            case 3:
                return 2;
            case 34:
                return 3;
            case 43:
            case 62:
                return 4;
            case 48:
                return 6;
            case 49:
                int appMsgType = appMsgTypeFromContent(source.content);
                if (appMsgType == 6) {
                    return 8;
                }
                if (appMsgType == 5) {
                    return 5;
                }
                return 1;
            default:
                return 1;
        }
    }

    private static String chatHistoryDataTitle(ChatHistorySource source) {
        if (source == null) {
            return "";
        }
        String text = normalizeMessageText(source.talker, source.content);
        switch (source.type) {
            case 1:
                return compactRecordText(text, 80);
            case 3:
                return "[图片]";
            case 34:
                return "[语音]";
            case 43:
            case 62:
                return "[视频]";
            case 47:
                return "[表情]";
            case 48:
                return "[位置]";
            case 49:
                return firstNonBlank(
                        compactRecordText(extractXmlField(text, "title"), 80),
                        compactRecordText(extractXmlField(text, "des"), 80),
                        "[链接/文件]");
            default:
                return compactRecordText(nonTextMessageDetail(source.type, text), 80);
        }
    }

    private static String chatHistoryDataDescription(ChatHistorySource source, String title) {
        if (source == null) {
            return "";
        }
        String text = normalizeMessageText(source.talker, source.content);
        if (source.type == 1) {
            return title;
        }
        if (source.type == 49) {
            return firstNonBlank(
                    compactRecordText(extractXmlField(text, "des"), 120),
                    compactRecordText(extractXmlField(text, "appname"), 80),
                    title);
        }
        return title;
    }

    private static int appMsgTypeFromContent(String content) {
        String value = extractXmlField(content, "type");
        if (isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String compactRecordText(String value, int limit) {
        if (isBlank(value)) {
            return "";
        }
        String text = value.trim().replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        while (text.contains("  ")) {
            text = text.replace("  ", " ");
        }
        if (limit <= 0 || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }

    private static String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String stripCData(String value) {
        value = value == null ? "" : value.trim();
        if (value.startsWith("<![CDATA[") && value.endsWith("]]>")) {
            return value.substring("<![CDATA[".length(), value.length() - "]]>".length()).trim();
        }
        return value;
    }

    private static String cdataSafe(String value) {
        return (value == null ? "" : value).replace("]]>", "]]]]><![CDATA[>");
    }

    private static void verifyWebSocketHandshake(InputStream input, String key) throws Exception {
        String headers = readHttpHeaders(input);
        String[] lines = headers.split("\r\n");
        if (lines.length == 0 || !lines[0].contains("101")) {
            throw new IOException("websocket upgrade failed: " + (lines.length == 0 ? "" : lines[0]));
        }
        String accept = "";
        for (String line : lines) {
            int index = line.indexOf(':');
            if (index <= 0) {
                continue;
            }
            String name = line.substring(0, index).trim();
            if ("Sec-WebSocket-Accept".equalsIgnoreCase(name)) {
                accept = line.substring(index + 1).trim();
            }
        }
        if (!webSocketAccept(key).equals(accept)) {
            throw new IOException("websocket accept mismatch");
        }
    }

    private static String readHttpHeaders(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;
        int value;
        byte[] end = new byte[]{'\r', '\n', '\r', '\n'};
        while ((value = input.read()) != -1) {
            buffer.write(value);
            if ((byte) value == end[matched]) {
                matched++;
                if (matched == end.length) {
                    break;
                }
            } else {
                matched = (byte) value == end[0] ? 1 : 0;
            }
            if (buffer.size() > 32768) {
                throw new IOException("websocket headers too large");
            }
        }
        return new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static String webSocketAccept(String key) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.ISO_8859_1));
        return Base64.encodeToString(digest, Base64.NO_WRAP);
    }

    private static WebSocketFrame readWebSocketFrame(InputStream input) throws Exception {
        int first = input.read();
        int second = input.read();
        if (first < 0 || second < 0) {
            throw new IOException("websocket closed");
        }
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) readByte(input) << 8) | readByte(input);
        } else if (length == 127) {
            length = 0L;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte(input);
            }
        }
        if (length > 1024L * 1024L) {
            throw new IOException("websocket frame too large");
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[]{(byte) readByte(input), (byte) readByte(input), (byte) readByte(input), (byte) readByte(input)};
        }
        byte[] payload = new byte[(int) length];
        int offset = 0;
        while (offset < payload.length) {
            int read = input.read(payload, offset, payload.length - offset);
            if (read < 0) {
                throw new IOException("websocket payload truncated");
            }
            offset += read;
        }
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }
        return new WebSocketFrame(opcode, payload);
    }

    private static int readByte(InputStream input) throws Exception {
        int value = input.read();
        if (value < 0) {
            throw new IOException("websocket frame truncated");
        }
        return value & 0xFF;
    }

    private static void writeWebSocketText(OutputStream output, String text) throws Exception {
        writeWebSocketFrame(output, 0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeWebSocketFrame(OutputStream output, int opcode, byte[] payload) throws Exception {
        if (payload == null) {
            payload = new byte[0];
        }
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | (opcode & 0x0F));
        int length = payload.length;
        if (length < 126) {
            frame.write(0x80 | length);
        } else if (length <= 65535) {
            frame.write(0x80 | 126);
            frame.write((length >> 8) & 0xFF);
            frame.write(length & 0xFF);
        } else {
            frame.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) (((long) length >> (8 * i)) & 0xFF));
            }
        }
        frame.write(mask);
        for (int i = 0; i < payload.length; i++) {
            frame.write(payload[i] ^ mask[i % 4]);
        }
        output.write(frame.toByteArray());
        output.flush();
    }

    private static boolean supportsPlainHttp(BridgeConfig config) {
        return config.baseUrl != null && config.baseUrl.trim().toLowerCase(Locale.US).startsWith("http://");
    }

    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                out.append(ch);
            } else {
                byte[] bytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    out.append('%');
                    int v = b & 0xFF;
                    char high = Character.toUpperCase(Character.forDigit((v >> 4) & 0xF, 16));
                    char low = Character.toUpperCase(Character.forDigit(v & 0xF, 16));
                    out.append(high).append(low);
                }
            }
        }
        return out.toString();
    }

    private static void logWebSocketFailure(String message) {
        long now = System.currentTimeMillis();
        if (now - LAST_WEBSOCKET_FAIL_LOG_AT < 30000L) {
            return;
        }
        LAST_WEBSOCKET_FAIL_LOG_AT = now;
        log(message);
    }

    private static SendResult sendText(BridgeConfig config, ClassLoader classLoader, String wxid, String text) {
        classLoader = runtimeClassLoader(classLoader);
        if (classLoader == null) {
            return SendResult.failed("WeChat classLoader is not available");
        }
        if (isBlank(wxid) || isBlank(text)) {
            return SendResult.failed("wxid and text are required");
        }

        Object db = ensureMessageDatabase(config);
        if (db == null) {
            return SendResult.failed("WeChat message database is not available for text send verification");
        }

        synchronized (WECHAT_SEND_LOCK) {
            final ClassLoader targetClassLoader = classLoader;
            final String targetWxid = wxid;
            final String targetText = text;
            final int msgType = resolveMessageType(classLoader, wxid);
            final long beforeMsgId;
            try {
                beforeMsgId = readMaxMessageId(db);
            } catch (Throwable t) {
                return SendResult.failed("text send verification failed before send: " + shortError(t));
            }
            Throwable directUnavailable = null;
            Throwable builderUnavailable = null;
            Throwable eventUnavailable = null;
            try {
                Boolean sent = callOnMainThread(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return Boolean.valueOf(sendViaSendBuilder(targetClassLoader, targetWxid, targetText, msgType));
                    }
                });
                if (Boolean.TRUE.equals(sent)) {
                    return verifyOutgoingTextSend(config, targetWxid, targetText, beforeMsgId, "w11.r1 builder", msgType);
                }
                builderUnavailable = new IllegalStateException("WeChat send builder returned false");
                long builderFalseMsgId = waitForOutgoingTextMessage(config, targetWxid, targetText, beforeMsgId, 1500L);
                if (builderFalseMsgId > 0L) {
                    log("sendText verified outgoing text despite w11.r1 builder=false wxid="
                            + redactedId(wxid) + " msgType=" + msgType + " msgId=" + builderFalseMsgId);
                    return SendResult.sent(builderFalseMsgId);
                }
                log("w11.r1 builder returned false without outgoing row, trying SendMsgEvent");
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                builderUnavailable = e;
                log("w11.r1 builder path unavailable, trying SendMsgEvent: " + shortError(e));
            } catch (Throwable t) {
                return SendResult.failed("WeChat send failed via w11.r1 builder: " + shortError(t));
            }

            try {
                Boolean published = callOnMainThread(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return Boolean.valueOf(sendViaSendMsgEvent(targetClassLoader, targetWxid, targetText, msgType));
                    }
                });
                if (!Boolean.TRUE.equals(published)) {
                    throw new IllegalStateException("SendMsgEvent had no listener");
                }
                return verifyOutgoingTextSend(config, targetWxid, targetText, beforeMsgId, "SendMsgEvent", msgType);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                eventUnavailable = e;
                log("SendMsgEvent path unavailable, trying dk5.s5.fj: " + shortError(e));
            } catch (Throwable t) {
                return SendResult.failed("WeChat send failed via SendMsgEvent: "
                        + shortError(t)
                        + "; builder unavailable: " + shortError(builderUnavailable));
            }

            try {
                callOnMainThread(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        sendViaSendMsgMgr(targetClassLoader, targetWxid, targetText, msgType);
                        return null;
                    }
                });
                return verifyOutgoingTextSend(config, targetWxid, targetText, beforeMsgId, "dk5.s5.fj", msgType);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                directUnavailable = e;
                log("dk5.s5.fj path unavailable, trying legacy w11.r0 NetScene: " + shortError(e));
            } catch (Throwable t) {
                return SendResult.failed("WeChat send failed via dk5.s5.fj: "
                        + shortError(t)
                        + "; event unavailable: " + shortError(eventUnavailable)
                        + "; builder unavailable: " + shortError(builderUnavailable));
            }

            try {
                Long msgId = callOnMainThread(new Callable<Long>() {
                    @Override
                    public Long call() throws Exception {
                        return Long.valueOf(sendViaNetScene(targetClassLoader, targetWxid, targetText, msgType));
                    }
                });
                long fallbackMsgId = msgId == null ? 0L : msgId.longValue();
                return verifyOutgoingTextSend(config, targetWxid, targetText, beforeMsgId, "legacy w11.r0 NetScene", msgType, fallbackMsgId);
            } catch (Throwable t) {
                return SendResult.failed("WeChat send failed via legacy w11.r0 NetScene: "
                        + shortError(t)
                        + "; event unavailable: " + shortError(eventUnavailable)
                        + "; manager unavailable: " + shortError(directUnavailable)
                        + "; builder unavailable: " + shortError(builderUnavailable));
            }
        }
    }

    private static SendResult verifyOutgoingTextSend(BridgeConfig config, String wxid, String text, long beforeMsgId, String path, int msgType) {
        return verifyOutgoingTextSend(config, wxid, text, beforeMsgId, path, msgType, 0L);
    }

    private static SendResult verifyOutgoingTextSend(BridgeConfig config, String wxid, String text, long beforeMsgId, String path, int msgType, long fallbackMsgId) {
        long msgId = waitForOutgoingTextMessage(config, wxid, text, beforeMsgId, 8000L);
        if (msgId > 0L) {
            log("sendText verified outgoing text via " + path + " wxid=" + redactedId(wxid) + " msgType=" + msgType + " msgId=" + msgId);
            return SendResult.sent(msgId);
        }
        if (fallbackMsgId > 0L) {
            log("sendText legacy path returned msgId=" + fallbackMsgId + " but verification did not observe matching text row");
        }
        return SendResult.failed("WeChat text send via " + path + " invoked but no outgoing text message was recorded");
    }

    private static boolean sendViaSendMsgEvent(ClassLoader classLoader, String wxid, String text, int msgType) throws Exception {
        Object event = findClass(classLoader, "com.tencent.mm.autogen.events.SendMsgEvent")
                .getDeclaredConstructor()
                .newInstance();
        Field payloadField = findFieldAny(event.getClass(), "f71992g", "g");
        Object payload = payloadField.get(event);
        setObjectField(payload, wxid, "f7337a", "a");
        setObjectField(payload, text, "f7338b", "b");
        setIntFieldAny(payload, msgType, "f7339c", "c");
        setIntFieldAny(payload, 0, "f7340d", "d");
        Object result = findNoArgMethod(event.getClass(), "e").invoke(event);
        return result instanceof Boolean && (Boolean) result;
    }

    private static <T> T callOnMainThread(Callable<T> callable) throws Exception {
        synchronized (WECHAT_SEND_LOCK) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return callable.call();
            }
            FutureTask<T> task = new FutureTask<>(callable);
            new Handler(Looper.getMainLooper()).post(task);
            return task.get();
        }
    }

    private static long sendViaNetScene(ClassLoader classLoader, String wxid, String text, int msgType) throws Exception {
        Class<?> netSceneClass = findClass(classLoader, "w11.r0");
        Constructor<?> ctor = findConstructor(netSceneClass, String.class, String.class, int.class, int.class, long.class);
        Object scene = ctor.newInstance(wxid, text, msgType, 4, 0L);
        long msgId = getLongField(scene, "f459357f", "f");
        if (msgId == -1L) {
            throw new IllegalStateException("w11.r0 inserted local msg failed");
        }

        Class<?> runCgi = findClass(classLoader, "com.tencent.mm.modelbase.z2");
        Object ready = findNoArgMethod(runCgi, "c").invoke(null);
        if (ready instanceof Boolean && !((Boolean) ready)) {
            throw new IllegalStateException("NetSceneQueue is not ready");
        }

        Method enqueue = findMethod(runCgi, "b", findClass(classLoader, "com.tencent.mm.modelbase.m1"));
        enqueue.invoke(null, scene);
        return msgId;
    }

    private static void sendViaSendMsgMgr(ClassLoader classLoader, String wxid, String text, int msgType) throws Exception {
        Class<?> accessorClass = findClass(classLoader, "tg3.t1");
        Method accessor = findNoArgMethod(accessorClass, "a");
        Object service = accessor.invoke(null);
        if (service == null) {
            throw new IllegalStateException("tg3.t1.a returned null");
        }
        Method send = findMethod(service.getClass(), "fj", String.class, String.class, int.class, int.class);
        send.invoke(service, wxid, text, msgType, 0);
    }

    private static boolean sendViaSendBuilder(ClassLoader classLoader, String wxid, String text, int msgType) throws Exception {
        return sendViaSendBuilder(classLoader, wxid, text, msgType, null);
    }

    private static boolean sendViaSendBuilder(ClassLoader classLoader, String wxid, String text, int msgType, Object forwardInfo) throws Exception {
        ensureSendBuilderFactory(classLoader);
        Class<?> builderFactory = findClass(classLoader, "w11.s1");
        Method create = findMethod(builderFactory, "a", String.class);
        Object builder = create.invoke(null, wxid);
        if (builder == null) {
            throw new IllegalStateException("w11.s1.a returned null");
        }

        findMethod(builder.getClass(), "g", String.class).invoke(builder, wxid);
        findMethod(builder.getClass(), "e", String.class).invoke(builder, text);
        findMethod(builder.getClass(), "h", int.class).invoke(builder, msgType);
        if (forwardInfo == null) {
            setForwardInfo(classLoader, builder);
        } else {
            applyForwardInfo(builder, forwardInfo);
        }
        setOptionalIntField(builder, 0, "f459371f", "f");
        setOptionalIntField(builder, 4, "f459374i", "i");

        Object request = findNoArgMethod(builder.getClass(), "a").invoke(builder);
        if (request == null) {
            throw new IllegalStateException("w11.r1.a returned null");
        }
        Object result = findNoArgMethod(request.getClass(), "a").invoke(request);
        return !(result instanceof Boolean) || (Boolean) result;
    }

    private static void ensureSendBuilderFactory(ClassLoader classLoader) throws Exception {
        Class<?> builderFactory = findClass(classLoader, "w11.s1");
        Field factoryField = findFieldAny(builderFactory, "f459386a", "a");
        if (factoryField.get(null) != null) {
            return;
        }
        Object factory = newInstanceAny(findClass(classLoader, "aq1.l"));
        factoryField.set(null, factory);
        log("initialized w11.s1 send factory with aq1.l");
    }

    private static void setForwardInfo(ClassLoader classLoader, Object builder) {
        try {
            applyForwardInfo(builder, createForwardInfo(classLoader));
        } catch (Throwable t) {
            log("forward info setup skipped: " + shortError(t));
        }
    }

    private static Object createForwardInfo(ClassLoader classLoader) throws Exception {
        return findClass(classLoader, "c01.h7").getDeclaredConstructor().newInstance();
    }

    private static void applyForwardInfo(Object builder, Object forwardInfo) throws Exception {
        findMethod(builder.getClass(), "f", forwardInfo.getClass()).invoke(builder, forwardInfo);
    }

    private static int resolveMessageType(ClassLoader classLoader, String wxid) {
        try {
            Class<?> typeResolver = findClass(classLoader, "c01.e2");
            Object result = findMethod(typeResolver, "C", String.class).invoke(null, wxid);
            if (result instanceof Number) {
                int value = ((Number) result).intValue();
                return value > 0 ? value : 1;
            }
            if (result != null) {
                int value = Integer.parseInt(String.valueOf(result));
                return value > 0 ? value : 1;
            }
        } catch (Throwable t) {
            log("resolve message type failed, fallback to text type 1: " + shortError(t));
        }
        return 1;
    }

    private static Class<?> findClass(ClassLoader classLoader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, classLoader);
    }

    private static Object newInstanceAny(Class<?> cls) throws Exception {
        try {
            Constructor<?> ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException ignored) {
            Constructor<?>[] constructors = cls.getDeclaredConstructors();
            for (Constructor<?> ctor : constructors) {
                ctor.setAccessible(true);
                try {
                    Class<?>[] types = ctor.getParameterTypes();
                    Object[] args = new Object[types.length];
                    for (int i = 0; i < types.length; i++) {
                        args[i] = defaultValue(types[i]);
                    }
                    return ctor.newInstance(args);
                } catch (Throwable t) {
                    log("constructor " + cls.getName() + "(" + ctor.getParameterCount() + ") failed: " + shortError(t));
                }
            }
            throw new NoSuchMethodException(cls.getName() + " constructors=" + describeConstructors(constructors));
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> cls, String name) throws NoSuchMethodException {
        return findMethod(cls, name);
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = cls;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        Method method = cls.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Constructor<?> findConstructor(Class<?> cls, Class<?>... parameterTypes) throws NoSuchMethodException {
        Constructor<?> ctor = cls.getDeclaredConstructor(parameterTypes);
        ctor.setAccessible(true);
        return ctor;
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setInt(target, value);
    }

    private static void setOptionalIntField(Object target, int value, String... names) {
        try {
            Field field = findFieldAny(target.getClass(), names);
            field.setInt(target, value);
        } catch (NoSuchFieldException e) {
            log("optional fields " + joinNames(names) + " not found on " + target.getClass().getName());
        } catch (Throwable t) {
            log("optional fields " + joinNames(names) + " set failed: " + shortError(t));
        }
    }

    private static void setIntFieldAny(Object target, int value, String... names) throws Exception {
        Field field = findFieldAny(target.getClass(), names);
        field.setInt(target, value);
    }

    private static void setLongFieldAny(Object target, long value, String... names) throws Exception {
        Field field = findFieldAny(target.getClass(), names);
        field.setLong(target, value);
    }

    private static void setDoubleFieldAny(Object target, double value, String... names) throws Exception {
        Field field = findFieldAny(target.getClass(), names);
        field.setDouble(target, value);
    }

    private static void setBooleanFieldAny(Object target, boolean value, String... names) throws Exception {
        Field field = findFieldAny(target.getClass(), names);
        field.setBoolean(target, value);
    }

    private static void setObjectField(Object target, Object value, String... names) throws Exception {
        Field field = findFieldAny(target.getClass(), names);
        field.set(target, value);
    }

    private static Field findFieldAny(Class<?> cls, String... names) throws NoSuchFieldException {
        NoSuchFieldException last = null;
        for (String name : names) {
            try {
                return findField(cls, name);
            } catch (NoSuchFieldException e) {
                last = e;
            }
        }
        throw last == null ? new NoSuchFieldException("") : last;
    }

    private static long getLongField(Object target, String... names) throws Exception {
        Field field = findFieldAny(target.getClass(), names);
        Object value = field.get(target);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static long optionalLongField(Object target, String... names) {
        if (target == null) {
            return 0L;
        }
        try {
            return getLongField(target, names);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String optionalStringField(Object target, String... names) {
        Object value = optionalObjectField(target, names);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object optionalObjectField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        try {
            Field field = findFieldAny(target.getClass(), names);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long optionalLongMethod(Object target, String name) {
        if (target == null || isBlank(name)) {
            return 0L;
        }
        try {
            Object value = findNoArgMethod(target.getClass(), name).invoke(target);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String optionalStringMethod(Object target, String name) {
        if (target == null || isBlank(name)) {
            return "";
        }
        try {
            Object value = findNoArgMethod(target.getClass(), name).invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String joinNames(String... names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            if (out.length() > 0) {
                out.append("/");
            }
            out.append(name);
        }
        return out.toString();
    }

    private static String describeConstructors(Constructor<?>[] constructors) {
        if (constructors == null || constructors.length == 0) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < constructors.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            Class<?>[] types = constructors[i].getParameterTypes();
            out.append("(");
            for (int j = 0; j < types.length; j++) {
                if (j > 0) {
                    out.append(",");
                }
                out.append(types[j].getName());
            }
            out.append(")");
        }
        out.append("]");
        return out.toString();
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> current = cls;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String postJson(BridgeConfig config, String path, String bodyJson) throws Exception {
        if (config.baseUrl != null && config.baseUrl.trim().toLowerCase(Locale.US).startsWith("http://")) {
            return postJsonSocket(config, path, bodyJson);
        }
        try {
            return postJsonOnce(config, path, bodyJson);
        } catch (IOException first) {
            log("postJson retry after io error: " + shortError(first));
            System.setProperty("http.keepAlive", "false");
            return postJsonOnce(config, path, bodyJson);
        }
    }

    private static String postJsonSocket(BridgeConfig config, String path, String bodyJson) throws Exception {
        URL url = new URL(trimRight(config.baseUrl, "/") + path);
        String requestPath = url.getFile();
        if (isBlank(requestPath)) {
            requestPath = "/";
        }
        int port = url.getPort() > 0 ? url.getPort() : 80;
        String host = url.getHost();
        String hostHeader = url.getPort() > 0 ? host + ":" + port : host;
        byte[] body = bodyJson.getBytes(StandardCharsets.UTF_8);
        log("postJsonSocket path=" + path + " host=" + hostHeader + " bodyBytes=" + body.length);

        StringBuilder headers = new StringBuilder();
        headers.append("POST ").append(requestPath).append(" HTTP/1.1\r\n");
        headers.append("Host: ").append(hostHeader).append("\r\n");
        headers.append("User-Agent: WechatObservatoryModule/0.1\r\n");
        headers.append("Accept: application/json\r\n");
        headers.append("Content-Type: application/json; charset=utf-8\r\n");
        headers.append("Content-Length: ").append(body.length).append("\r\n");
        headers.append("Connection: close\r\n\r\n");

        ByteArrayOutputStream response = new ByteArrayOutputStream();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), BRIDGE_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(BRIDGE_READ_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
            out.flush();
            socket.shutdownOutput();

            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                response.write(buffer, 0, read);
            }
        }

        byte[] responseBytes = response.toByteArray();
        if (responseBytes.length == 0) {
            throw new IOException("bridge returned empty socket response");
        }
        String responseText = new String(responseBytes, StandardCharsets.ISO_8859_1);
        int headerEnd = responseText.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            throw new IOException("bridge returned malformed socket response");
        }
        int statusEnd = responseText.indexOf("\r\n");
        String statusLine = statusEnd < 0 ? responseText : responseText.substring(0, statusEnd);
        String[] statusParts = statusLine.split(" ");
        int status = statusParts.length >= 2 ? Integer.parseInt(statusParts[1]) : 0;
        byte[] bodyBytes = new byte[responseBytes.length - headerEnd - 4];
        System.arraycopy(responseBytes, headerEnd + 4, bodyBytes, 0, bodyBytes.length);

        String headersText = responseText.substring(0, headerEnd).toLowerCase(Locale.US);
        String responseBody = headersText.contains("transfer-encoding: chunked")
                ? decodeChunkedBody(bodyBytes)
                : new String(bodyBytes, StandardCharsets.UTF_8);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("bridge returned HTTP " + status + ": " + responseBody);
        }
        return responseBody;
    }

    private static String decodeChunkedBody(byte[] bodyBytes) throws Exception {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < bodyBytes.length) {
            int lineEnd = indexOfCrlf(bodyBytes, offset);
            if (lineEnd < 0) {
                break;
            }
            String sizeLine = new String(bodyBytes, offset, lineEnd - offset, StandardCharsets.ISO_8859_1).trim();
            int semicolon = sizeLine.indexOf(';');
            if (semicolon >= 0) {
                sizeLine = sizeLine.substring(0, semicolon);
            }
            int chunkSize = Integer.parseInt(sizeLine, 16);
            offset = lineEnd + 2;
            if (chunkSize == 0) {
                break;
            }
            if (offset + chunkSize > bodyBytes.length) {
                throw new IOException("truncated chunked response");
            }
            decoded.write(bodyBytes, offset, chunkSize);
            offset += chunkSize + 2;
        }
        return new String(decoded.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int indexOfCrlf(byte[] bytes, int start) {
        for (int i = start; i + 1 < bytes.length; i++) {
            if (bytes[i] == '\r' && bytes[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static String postJsonOnce(BridgeConfig config, String path, String bodyJson) throws Exception {
        URL url = new URL(trimRight(config.baseUrl, "/") + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(BRIDGE_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(BRIDGE_READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Connection", "close");

        byte[] body = bodyJson.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }

        int status = connection.getResponseCode();
        String response = readResponse(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("bridge returned HTTP " + status + ": " + response);
        }
        return response;
    }

    private static String readResponse(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
        }
        return out.toString();
    }

    private static String stringArg(Object[] args, int index) {
        if (args == null || args.length <= index || args[index] == null) {
            return "";
        }
        return String.valueOf(args[index]);
    }

    private static ContentValues contentValuesArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof ContentValues) {
                return (ContentValues) arg;
            }
        }
        return null;
    }

    private static int affectedRows(Object result) {
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        return 0;
    }

    private static long normalizeCreateTime(Long createTime) {
        return normalizeCreateTime(createTime == null ? 0L : createTime.longValue());
    }

    private static long normalizeCreateTime(long createTime) {
        if (createTime <= 0) {
            return System.currentTimeMillis() / 1000L;
        }
        return createTime > 10_000_000_000L ? createTime / 1000L : createTime;
    }

    private static void log(String message) {
        BridgeLogger.log(message);
    }

    private static String redactedId(String value) {
        return isBlank(value) ? "<empty>" : "<set>";
    }

}
