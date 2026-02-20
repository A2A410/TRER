package xeq.trer.html;

public class ProxyMeta {
    public String name;
    public String urlPattern;
    public String health = "unknown";

    public ProxyMeta(String name, String urlPattern) {
        this.name = name;
        this.urlPattern = urlPattern;
    }
}
