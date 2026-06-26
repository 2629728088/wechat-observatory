package cc.wechat.observatory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class BridgeConfigReceiver extends BroadcastReceiver {
    public static final String ACTION_SET_CONFIG = "cc.wechat.observatory.SET_CONFIG";
    private static final String TAG = "WechatGateway";
    private static final Set<String> ALLOWED_KEYS = new HashSet<>(Arrays.asList(BridgeConfigProvider.CONFIG_KEYS));

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION_SET_CONFIG.equals(intent.getAction())) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(BridgeConfigProvider.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        int count = 0;
        for (String key : ALLOWED_KEYS) {
            if (!intent.hasExtra(key)) {
                continue;
            }
            String value = intent.getStringExtra(key);
            if (value == null || value.trim().isEmpty()) {
                editor.remove(key);
            } else {
                editor.putString(key, value.trim());
            }
            count++;
        }
        editor.commit();
        BridgeConfigFiles.writeExternalMirror(context, prefs);
        Log.i(TAG, "stored bridge config keys=" + count);
    }
}
