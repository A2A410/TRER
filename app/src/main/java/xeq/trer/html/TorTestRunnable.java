package xeq.trer.html;

public class TorTestRunnable implements Runnable {
    private final WebAppInterface mBridge;

    public TorTestRunnable(WebAppInterface bridge) {
        mBridge = bridge;
    }

    @Override
    public void run() {
        try {
            String result = Networking.testTor();
            mBridge.onTorTestResult(true, result);
        } catch (Exception e) {
            mBridge.onTorTestResult(false, e.getMessage());
        }
    }
}
