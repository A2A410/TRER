package xeq.trer.html;

public class HistoryEntry {
    public String q;
    public int count;
    public long ts;

    public HistoryEntry() {}

    public HistoryEntry(String q, int count, long ts) {
        this.q = q;
        this.count = count;
        this.ts = ts;
    }
}
