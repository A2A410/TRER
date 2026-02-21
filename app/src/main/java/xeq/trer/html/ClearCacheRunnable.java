package xeq.trer.html;

import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

public class ClearCacheRunnable implements Runnable {
    private final WebView webView;
    private final TREREngine.EngineCallback callback;

    public ClearCacheRunnable(WebView webView, TREREngine.EngineCallback callback) {
        this.webView = webView;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            webView.clearCache(true);
            CookieManager.getInstance().removeAllCookie();
            WebStorage.getInstance().deleteAllData();
            callback.onStatus("ok", "Cache and Web Data cleared");
        } catch (Exception e) {
            callback.logDebug("error", "Failed to clear cache: " + e.getMessage());
        }
    }
}
