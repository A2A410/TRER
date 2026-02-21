package xeq.trer.html;

import android.webkit.WebView;

public class CallJsRunnable implements Runnable {
    private final WebView webView;
    private final String script;

    public CallJsRunnable(WebView webView, String script) {
        this.webView = webView;
        this.script = script;
    }

    @Override
    public void run() {
        webView.loadUrl("javascript:" + script);
    }
}
