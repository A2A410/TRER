package xeq.trer.html;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Models {

    public static class Config {
        public int maxKw = 15;
        public int resPerKw = 5;
        public int topN = 20;
        public int delay = 600;
        public boolean antiSeo = true;
        public boolean diversity = true;
        public boolean retry = true;
        public NguConfig ngu = new NguConfig();
        public int pinnedProxy = -1;
    }

    public static class NguConfig {
        public boolean enabled = false;
        public int delay = 1000;
        public int proxyRetries = 3;
    }

    public static class SearchResult {
        public String title;
        public String url;
        public String domain;
        public String snippet;
        public int wave;
        public String keyword;
        public double termScore;
        public int waveScore;
        public int score;
        public int appearances;
        public Set<Integer> waves = new HashSet<>();
        public Set<String> keywords = new HashSet<>();
        public double totalTermScore;
        public String waveStr;

        public SearchResult(String title, String url, String domain, String snippet) {
            this.title = title;
            this.url = url;
            this.domain = domain;
            this.snippet = snippet;
        }
    }

    public static class ExpansionTerm {
        public String term;
        public double freq;
        public Set<String> sources = new HashSet<>();
        public double score;
        public String tag;

        public ExpansionTerm(String term) {
            this.term = term;
        }
    }

    public static class HistoryEntry {
        public String q;
        public int count;
        public long ts;

        public HistoryEntry(String q, int count, long ts) {
            this.q = q;
            this.count = count;
            this.ts = ts;
        }
    }

    public static class ProxyMeta {
        public String name;
        public String urlPattern;
        public String health = "unknown";

        public ProxyMeta(String name, String urlPattern) {
            this.name = name;
            this.urlPattern = urlPattern;
        }
    }
}
