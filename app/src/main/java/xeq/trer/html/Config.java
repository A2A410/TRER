package xeq.trer.html;

public class Config {
    public int maxKw = 15;
    public int resPerKw = 5;
    public int topN = 20;
    public int delay = 600;
    public boolean antiSeo = true;
    public boolean diversity = true;
    public boolean retry = true;
    public NguConfig ngu = new NguConfig();
    public int pinnedProxy = -1;
    public boolean torEnabled = false;
    public boolean torRoute = false;
    public boolean torAutoRebuild = false;
}
