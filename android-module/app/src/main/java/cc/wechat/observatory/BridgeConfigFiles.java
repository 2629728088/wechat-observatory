package cc.wechat.observatory;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class BridgeConfigFiles {
    private static final String TAG = "WechatGateway";
    private static final String MIRROR_PATH = "Android/media/cc.wechat.observatory/config.properties";
    private static final String LOCAL_TMP_DIR = "/data/local/tmp/wechat-observatory";
    private static final String LOCAL_TMP_CONFIG = LOCAL_TMP_DIR + "/config.properties";

    private BridgeConfigFiles() {
    }

    static void writeExternalMirror(Context context, SharedPreferences prefs) {
        if (context == null || prefs == null) {
            return;
        }
        makeSharedPreferencesReadable(context);

        File root = Environment.getExternalStorageDirectory();
        File file = new File(root, MIRROR_PATH);
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "create config mirror dir failed: " + dir.getAbsolutePath());
            return;
        }

        Map<String, ?> all = prefs.getAll();
        StringBuilder out = new StringBuilder();
        for (String key : BridgeConfigProvider.CONFIG_KEYS) {
            Object value = all.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            out.append(key).append('=').append(sanitize(text)).append('\n');
        }

        String text = out.toString();
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.w(TAG, "write config mirror failed: " + t);
        }
        writeLocalTmpMirror(context, text);
    }

    static void makeSharedPreferencesReadable(Context context) {
        if (context == null) {
            return;
        }
        File dataDir = new File(context.getApplicationInfo().dataDir);
        File sharedPrefsDir = new File(dataDir, "shared_prefs");
        File prefsFile = new File(sharedPrefsDir, BridgeConfigProvider.PREFS_NAME + ".xml");
        makeReadablePath(dataDir, true);
        makeReadablePath(sharedPrefsDir, true);
        makeReadablePath(prefsFile, false);
    }

    private static void makeReadablePath(File file, boolean directory) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            file.setReadable(true, false);
            if (directory) {
                file.setExecutable(true, false);
            }
        } catch (Throwable t) {
            Log.w(TAG, "mark readable failed: " + file.getAbsolutePath() + " " + t);
        }
        try {
            String mode = directory ? "755" : "644";
            Runtime.getRuntime().exec(new String[]{"chmod", mode, file.getAbsolutePath()}).waitFor();
        } catch (Throwable t) {
            Log.w(TAG, "chmod readable failed: " + file.getAbsolutePath() + " " + t);
        }
    }

    private static String sanitize(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static void writeLocalTmpMirror(Context context, String text) {
        if (context == null || text == null) {
            return;
        }
        File cacheFile = new File(context.getCacheDir(), "wechat-observatory-config.properties");
        try (FileOutputStream output = new FileOutputStream(cacheFile, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.w(TAG, "write temp config mirror failed: " + t);
            return;
        }

        String command = "mkdir -p " + shellQuote(LOCAL_TMP_DIR)
                + " && cp " + shellQuote(cacheFile.getAbsolutePath()) + " " + shellQuote(LOCAL_TMP_CONFIG)
                + " && chmod 755 " + shellQuote(LOCAL_TMP_DIR)
                + " && chmod 644 " + shellQuote(LOCAL_TMP_CONFIG);
        try {
            int code = Runtime.getRuntime().exec(new String[]{"su", "-c", command}).waitFor();
            if (code != 0) {
                Log.w(TAG, "write local tmp config mirror failed, su exit=" + code);
            }
        } catch (Throwable t) {
            Log.w(TAG, "write local tmp config mirror failed: " + t);
        }
    }

    private static String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
