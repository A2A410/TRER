package xeq.trer.html;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class TorHardRestartRunnable implements Runnable {
    private final Context mContext;
    private final Handler mHandler;

    public TorHardRestartRunnable(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    @Override
    public void run() {
        Intent stop = new Intent("org.torproject.android.intent.action.STOP");
        stop.setPackage("org.torproject.torservices");
        mContext.sendBroadcast(stop);

        mHandler.postDelayed(new TorStartRunnable(mContext), 1000);
    }
}
