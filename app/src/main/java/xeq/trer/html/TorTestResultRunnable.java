package xeq.trer.html;

import android.webkit.WebView;

public class TorTestResultRunnable implements Runnable {
    private final WebAppInterface mBridge;
    private final boolean mSuccess;
    private final String mMsg;

    public TorTestResultRunnable(WebAppInterface bridge, boolean success, String msg) {
        mBridge = bridge;
        mSuccess = success;
        mMsg = msg;
    }

    @Override
    public void run() {
        mBridge.callJs("TRER_UI.onTorTestResult", mSuccess, mMsg);
    }
}
