package xeq.trer.html;

public class EngineRunnable implements Runnable {
    private final String query;
    private final TREREngine engine;

    public EngineRunnable(TREREngine engine, String query) {
        this.engine = engine;
        this.query = query;
    }

    @Override
    public void run() {
        try {
            engine.execute(query);
            engine.callback.onFinished(true);
        } catch (Exception e) {
            engine.callback.onStatus("err", "Error: " + e.getMessage());
            engine.callback.logDebug("error", "Engine error: " + e.getMessage());
            engine.callback.onFinished(false);
        }
    }
}
