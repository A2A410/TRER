package xeq.trer.html;

import android.content.Context;
import android.content.Intent;

public class TorStartRunnable implements Runnable {
    private final Context mContext;

    public TorStartRunnable(Context context) {
        mContext = context;
    }

    @Override
    public void run() {
        Intent start = new Intent("org.torproject.android.intent.action.START");
        start.setPackage("org.torproject.torservices");
        mContext.sendBroadcast(start);
    }
}
