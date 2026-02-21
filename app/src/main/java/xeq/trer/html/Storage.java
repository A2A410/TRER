package xeq.trer.html;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class Storage {
    private static final String TAG = "TRERStorage";
    private static final String PREFS_NAME = "TRER_PREFS";
    private static final String KEY_CFG = "trer_cfg_v1";
    private static final String KEY_HIST = "trer_hist_v1";
    private static final Gson gson = new Gson();

    public static void saveConfig(Context context, Config cfg) {
        try {
            SharedPreferences.Editor editor = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            String json = gson.toJson(cfg);
            editor.putString(KEY_CFG, json);
            editor.apply();
            Log.d(TAG, "Config saved: " + json.length() + " chars");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save config", e);
        }
    }

    public static Config loadConfig(Context context) {
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_CFG, null);
            if (json == null) return new Config();
            return gson.fromJson(json, Config.class);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config", e);
            return new Config();
        }
    }

    public static void saveHistory(Context context, List<HistoryEntry> hist) {
        try {
            SharedPreferences.Editor editor = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            String json = gson.toJson(hist);
            editor.putString(KEY_HIST, json);
            editor.apply();
            Log.d(TAG, "History saved: " + hist.size() + " entries, " + json.length() + " chars");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save history", e);
        }
    }

    public static List<HistoryEntry> loadHistory(Context context) {
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_HIST, null);
            if (json == null) return new ArrayList<>();
            List<HistoryEntry> list = gson.fromJson(json, new TypeToken<List<HistoryEntry>>(){}.getType());
            return list != null ? list : new ArrayList<HistoryEntry>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load history", e);
            return new ArrayList<>();
        }
    }
}
