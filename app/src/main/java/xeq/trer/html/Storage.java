package xeq.trer.html;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class Storage {
    private static final String PREFS_NAME = "TRER_PREFS";
    private static final String KEY_CFG = "trer_cfg_v1";
    private static final String KEY_HIST = "trer_hist_v1";
    private static final Gson gson = new Gson();

    public static void saveConfig(Context context, Models.Config cfg) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_CFG, gson.toJson(cfg));
        editor.apply();
    }

    public static Models.Config loadConfig(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CFG, null);
        if (json == null) return new Models.Config();
        return gson.fromJson(json, Models.Config.class);
    }

    public static void saveHistory(Context context, List<Models.HistoryEntry> hist) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_HIST, gson.toJson(hist));
        editor.apply();
    }

    public static List<Models.HistoryEntry> loadHistory(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_HIST, null);
        if (json == null) return new ArrayList<>();
        return gson.fromJson(json, new TypeToken<List<Models.HistoryEntry>>(){}.getType());
    }
}
