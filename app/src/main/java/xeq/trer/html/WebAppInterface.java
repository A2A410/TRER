package xeq.trer.html;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebStorage;
import android.webkit.WebView;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

public class WebAppInterface implements TREREngine.EngineCallback {
    private final Context mContext;
    private final WebView mWebView;
    private TREREngine mEngine;
    private Config mCfg;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Gson mGson = new Gson();
    private String mTorStatus = "UNKNOWN";
    private final TorStatusReceiver mTorStatusReceiver = new TorStatusReceiver(this);

    public WebAppInterface(Context c, WebView webView) {
        mContext = c;
        mWebView = webView;
        mCfg = Storage.loadConfig(c);

        IntentFilter filter = new IntentFilter();
        filter.addAction("org.torproject.android.intent.action.STATUS");
        filter.addAction("info.pluggabletransports.status");
        mContext.registerReceiver(mTorStatusReceiver, filter);
    }

    @JavascriptInterface
    public void init() {
        boolean torInstalled = isTorServicesInstalled();
        callJs("TRER_UI.updateConfig", mGson.toJson(mCfg), torInstalled);
        callJs("TRER_UI.updateTorStatus", mTorStatus);
        if (mCfg.torEnabled) {
            handleTorToggled(true);
        }
    }

    @JavascriptInterface
    public void runSearch(String query) {
        if (mEngine != null) mEngine.abort();
        mEngine = new TREREngine(mCfg, this);
        callJs("TRER_UI.setRunning", true);
        mEngine.run(query);
    }

    @JavascriptInterface
    public void stopSearch() {
        if (mEngine != null) mEngine.abort();
    }

    @JavascriptInterface
    public void saveConfig(String json) {
        Config newCfg = mGson.fromJson(json, Config.class);
        boolean torJustEnabled = newCfg.torEnabled && !mCfg.torEnabled;
        mCfg = newCfg;
        Storage.saveConfig(mContext, mCfg);
        if (torJustEnabled) {
            handleTorToggled(true);
        }
    }

    @JavascriptInterface
    public void loadHistory() {
        final List<HistoryEntry> hist = Storage.loadHistory(mContext);
        callJs("TRER_UI.updateHistory", mGson.toJson(hist));
    }

    @JavascriptInterface
    public void deleteHistory(int index) {
        List<HistoryEntry> hist = Storage.loadHistory(mContext);
        if (index >= 0 && index < hist.size()) {
            hist.remove(index);
            Storage.saveHistory(mContext, hist);
            loadHistory();
        }
    }

    @JavascriptInterface
    public void clearHistory() {
        Storage.saveHistory(mContext, new ArrayList<HistoryEntry>());
        loadHistory();
    }

    @JavascriptInterface
    public void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            mContext.startActivity(i);
        } catch (Exception e) {
            logDebug("error", "Failed to open URL: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public void retestProxies() {
        onStatus("info", "Native mode: direct connection active");
    }

    @JavascriptInterface
    public void forceRebuildTor() {
        rebuildTor();
    }

    @JavascriptInterface
    public void clearCache() {
        mWebView.post(new ClearCacheRunnable(mWebView, this));
    }

    @JavascriptInterface
    public void exportDebug(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("TRER Debug Log", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                onStatus("ok", "Debug logs copied to clipboard");
            }
        } catch (Exception e) {
            logDebug("error", "Failed to copy: " + e.getMessage());
        }
    }

    private boolean isTorServicesInstalled() {
        PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo("org.torproject.torservices", PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void handleTorToggled(boolean enabled) {
        if (enabled) {
            logDebug("info", "Requesting TorServices to start...");
            Intent intent = new Intent("org.torproject.android.intent.action.START");
            intent.setPackage("org.torproject.torservices");
            mContext.sendBroadcast(intent);
        } else {
            logDebug("info", "Requesting TorServices to stop...");
            Intent intent = new Intent("org.torproject.android.intent.action.STOP");
            intent.setPackage("org.torproject.torservices");
            mContext.sendBroadcast(intent);
        }
    }

    public void rebuildTor() {
        logDebug("info", "Rebuilding Tor circuit...");
        onStatus("warn", "Tor rebuild triggered, search pending...");

        Intent stop = new Intent("org.torproject.android.intent.action.STOP");
        stop.setPackage("org.torproject.torservices");
        mContext.sendBroadcast(stop);

        mTorStatus = "REBUILDING";
        callJs("TRER_UI.updateTorStatus", mTorStatus);

        mHandler.postDelayed(new TorStartRunnable(mContext), 1500);
    }

    public void updateTorStatusLocally(String status) {
        mTorStatus = status;
        callJs("TRER_UI.updateTorStatus", mTorStatus);
    }

    private void runOnMainThread(Runnable r) {
        mHandler.post(r);
    }

    // --- Engine Callbacks ---

    @Override
    public void onStatus(final String type, final String msg) {
        callJs("TRER_UI.addStatusEntry", type, msg);
    }

    @Override
    public void onWaveUpdate(final int n, final String state, final int pct, final String stat) {
        callJs("TRER_UI.updateWave", n, state, pct, stat);
    }

    @Override
    public void onKeywordsFound(final String query, final List<ExpansionTerm> terms) {
        final String json = mGson.toJson(terms);
        callJs("TRER_UI.setKeywords", query, json);
    }

    @Override
    public void onResultsFound(final List<SearchResult> results, final String query) {
        final String json = mGson.toJson(results);
        callJs("TRER_UI.setResults", json, query);

        List<HistoryEntry> hist = Storage.loadHistory(mContext);
        hist.add(0, new HistoryEntry(query, results.size(), System.currentTimeMillis()));
        if (hist.size() > 20) {
            hist = new ArrayList<>(hist.subList(0, 20));
        }
        Storage.saveHistory(mContext, hist);
    }

    @Override
    public void onProgress(final String msg) {
        callJs("TRER_UI.updateEta", msg);
    }

    @Override
    public void onNguUpdate(final boolean show, final String text, final int attempt, final int pct) {
        callJs("TRER_UI.updateNgu", show, text, attempt, pct);
    }

    @Override
    public void onFinished(boolean success) {
        callJs("TRER_UI.setRunning", false);
    }

    @Override
    public void logDebug(final String lvl, final String msg) {
        final String ts = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        callJs("TRER_UI.logDebug", lvl, msg, ts);
    }

    @Override
    public void onTorRebuildRequested() {
        rebuildTor();
    }

    @Override
    public String getTorStatus() {
        return mTorStatus;
    }

    private void callJs(String method, Object... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(",");
            Object arg = args[i];
            if (arg instanceof String) {
                String s = (String) arg;
                String escaped = s.replace("\\", "\\\\")
                                  .replace("'", "\\'")
                                  .replace("\n", "\\n")
                                  .replace("\r", "\\r");
                sb.append("'").append(escaped).append("'");
            } else {
                sb.append(String.valueOf(arg));
            }
        }
        sb.append(")");
        final String script = sb.toString();
        mWebView.post(new CallJsRunnable(mWebView, script));
    }
}
