package xeq.trer.html;

import java.util.HashSet;
import java.util.Set;

public class SearchResult {
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
