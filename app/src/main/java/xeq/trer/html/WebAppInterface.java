package xeq.trer.html;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
    private Models.Config mCfg;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Gson mGson = new Gson();

    public WebAppInterface(Context c, WebView webView) {
        mContext = c;
        mWebView = webView;
        mCfg = Storage.loadConfig(c);
    }

    @JavascriptInterface
    public void init() {
        callJs("TRER_UI.updateConfig", mGson.toJson(mCfg));
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
        mCfg = mGson.fromJson(json, Models.Config.class);
        Storage.saveConfig(mContext, mCfg);
    }

    @JavascriptInterface
    public void loadHistory() {
        final List<Models.HistoryEntry> hist = Storage.loadHistory(mContext);
        callJs("TRER_UI.updateHistory", mGson.toJson(hist));
    }

    @JavascriptInterface
    public void deleteHistory(int index) {
        List<Models.HistoryEntry> hist = Storage.loadHistory(mContext);
        if (index >= 0 && index < hist.size()) {
            hist.remove(index);
            Storage.saveHistory(mContext, hist);
            loadHistory();
        }
    }

    @JavascriptInterface
    public void clearHistory() {
        Storage.saveHistory(mContext, new ArrayList<Models.HistoryEntry>());
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
    public void clearCache() {
        runOnMainThread(new ClearCacheRunnable(mWebView, this));
    }

    private static class ClearCacheRunnable implements Runnable {
        private final WebView webView;
        private final WebAppInterface bridge;

        ClearCacheRunnable(WebView webView, WebAppInterface bridge) {
            this.webView = webView;
            this.bridge = bridge;
        }

        @Override
        public void run() {
            try {
                webView.clearCache(true);
                CookieManager.getInstance().removeAllCookie();
                WebStorage.getInstance().deleteAllData();
                bridge.onStatus("ok", "Cache and Web Data cleared");
            } catch (Exception e) {
                bridge.logDebug("error", "Failed to clear cache: " + e.getMessage());
            }
        }
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
    public void onKeywordsFound(final String query, final List<Models.ExpansionTerm> terms) {
        final String json = mGson.toJson(terms);
        callJs("TRER_UI.setKeywords", query, json);
    }

    @Override
    public void onResultsFound(final List<Models.SearchResult> results, final String query) {
        final String json = mGson.toJson(results);
        callJs("TRER_UI.setResults", json, query);

        List<Models.HistoryEntry> hist = Storage.loadHistory(mContext);
        hist.add(0, new Models.HistoryEntry(query, results.size(), System.currentTimeMillis()));
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

    private void callJs(String method, Object... args) {
        runOnMainThread(new CallJsRunnable(mWebView, method, args));
    }

    private static class CallJsRunnable implements Runnable {
        private final WebView webView;
        private final String method;
        private final Object[] args;

        CallJsRunnable(WebView webView, String method, Object[] args) {
            this.webView = webView;
            this.method = method;
            this.args = args;
        }

        @Override
        public void run() {
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
            webView.loadUrl("javascript:" + sb.toString());
        }
    }
}
