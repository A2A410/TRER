package xeq.trer.html;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TorStatusReceiver extends BroadcastReceiver {
    private final WebAppInterface mBridge;

    public TorStatusReceiver(WebAppInterface bridge) {
        mBridge = bridge;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String status = intent.getStringExtra("org.torproject.android.intent.extra.STATUS");
        if (status == null) status = intent.getStringExtra("status");
        if (status != null) {
            mBridge.updateTorStatusLocally(status.toUpperCase());
        }
    }
}
