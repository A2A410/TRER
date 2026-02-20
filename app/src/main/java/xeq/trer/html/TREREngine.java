package xeq.trer.html;

import android.util.Log;
import com.google.gson.Gson;
import java.util.*;

public class TREREngine {
    private static final String TAG = "TREREngine";
    final Config cfg;
    final EngineCallback callback;
    private boolean abortFlag = false;
    private final Gson gson = new Gson();

    public interface EngineCallback {
        void onStatus(String type, String msg);
        void onWaveUpdate(int n, String state, int pct, String stat);
        void onKeywordsFound(String query, List<ExpansionTerm> terms);
        void onResultsFound(List<SearchResult> results, String query);
        void onProgress(String msg);
        void onNguUpdate(boolean show, String text, int attempt, int pct);
        void onFinished(boolean success);
        void logDebug(String lvl, String msg);
    }

    public TREREngine(Config cfg, EngineCallback callback) {
        this.cfg = cfg;
        this.callback = callback;
    }

    public void abort() {
        this.abortFlag = true;
    }

    public void run(final String query) {
        new Thread(new EngineRunnable(this, query)).start();
    }

    void execute(String query) throws Exception {
        callback.logDebug("info", "Starting Wave 1...");
        callback.onWaveUpdate(1, "active", 0, "0%");

        // --- WAVE 1: Expansion ---
        callback.onStatus("info", "W1: Expanding keywords...");
        List<ExpansionTerm> terms = wave1(query);
        if (abortFlag) return;
        callback.onKeywordsFound(query, terms);
        callback.onWaveUpdate(1, "done", 100, terms.size() + "kw");

        // --- WAVE 2: Harvest ---
        callback.logDebug("info", "Starting Wave 2...");
        callback.onWaveUpdate(2, "active", 0, "0%");
        List<SearchResult> allResults = wave2(query, terms);
        if (abortFlag) return;
        callback.onWaveUpdate(2, "done", 100, allResults.size() + "u");

        // --- WAVE 3: Scoring ---
        callback.logDebug("info", "Starting Wave 3...");
        callback.onWaveUpdate(3, "active", 0, "0%");
        List<SearchResult> finalResults = wave3(query, allResults);
        callback.onResultsFound(finalResults, query);
        callback.onWaveUpdate(3, "done", 100, "TOP " + finalResults.size());

        callback.onStatus("ok", "Search complete: " + finalResults.size() + " results");
    }

    private List<ExpansionTerm> wave1(String query) throws Exception {
        Map<String, ExpansionTerm> termMap = new HashMap<>();

        // A: Autocomplete (Direct if possible, but let's just use fetchProxy)
        callback.onWaveUpdate(1, "active", 10, "10%");
        String acJson = fetchProxy("https://ac.duckduckgo.com/ac/?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&type=list", 0);
        List<Object> acList = gson.fromJson(acJson, List.class);
        if (acList.size() > 1 && acList.get(1) instanceof List) {
            List<String> suggestions = (List<String>) acList.get(1);
            for (String sug : suggestions) {
                addTerm(termMap, sug, "autocomplete", 3.0);
            }
        }

        // B: HTML Related
        callback.onWaveUpdate(1, "active", 50, "50%");
        String html = fetchProxy("https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8"), 1);
        List<String> related = Networking.parseRelatedSearches(html);
        for (String r : related) {
            addTerm(termMap, r, "html_related", 2.5);
        }

        // Score and select
        List<ExpansionTerm> scored = new ArrayList<>(termMap.values());
        for (ExpansionTerm t : scored) {
            t.score = t.freq * Math.log(1 + t.sources.size());
            StringBuilder sb = new StringBuilder();
            for (String s : t.sources) {
                if (sb.length() > 0) sb.append("+");
                if (s.equals("autocomplete")) sb.append("AC");
                else if (s.equals("html_related")) sb.append("HR");
                else sb.append(s.substring(0, 2).toUpperCase());
            }
            t.tag = sb.toString();
        }

        sortExpansionTerms(scored);

        List<ExpansionTerm> finalTerms = new ArrayList<>();
        for (ExpansionTerm t : scored) {
            if (finalTerms.size() >= cfg.maxKw) break;
            finalTerms.add(t);
        }
        return finalTerms;
    }

    private void addTerm(Map<String, ExpansionTerm> map, String term, String source, double weight) {
        term = term.toLowerCase().trim();
        if (term.length() < 3) return;
        ExpansionTerm et = map.get(term);
        if (et == null) {
            et = new ExpansionTerm(term);
            map.put(term, et);
        }
        et.freq += weight;
        et.sources.add(source);
    }

    private List<SearchResult> wave2(String query, List<ExpansionTerm> terms) throws Exception {
        List<SearchResult> all = new ArrayList<>();

        // Initial results from query
        String initialHtml = fetchProxy("https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8"), 0);
        List<SearchResult> initialResults = Networking.parseDDGHtml(initialHtml, cfg.resPerKw);
        for (SearchResult r : initialResults) {
            r.wave = 1;
            r.keyword = query;
            all.add(r);
        }

        for (int i = 0; i < terms.size(); i++) {
            if (abortFlag) break;
            ExpansionTerm t = terms.get(i);
            String q = query + " " + t.term;
            callback.onStatus("info", "W2 [" + (i+1) + "/" + terms.size() + "]: " + t.term);
            callback.onWaveUpdate(2, "active", (int)(((double)i/terms.size())*100), (i+1)+"/"+terms.size());

            Thread.sleep(cfg.delay);
            try {
                String html = fetchProxy("https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(q, "UTF-8"), i + 1);
                List<SearchResult> results = Networking.parseDDGHtml(html, cfg.resPerKw);
                for (SearchResult r : results) {
                    r.wave = 2;
                    r.keyword = t.term;
                    r.termScore = t.score;
                    all.add(r);
                }
            } catch (Exception e) {
                callback.logDebug("warn", "W2 query failed: " + t.term);
            }
        }
        return all;
    }

    private List<SearchResult> wave3(String query, List<SearchResult> allResults) {
        Map<String, SearchResult> urlMap = new HashMap<>();
        for (SearchResult r : allResults) {
            SearchResult m = urlMap.get(r.url);
            if (m == null) {
                m = r;
                urlMap.put(r.url, m);
            }
            m.appearances++;
            m.waves.add(r.wave);
            m.keywords.add(r.keyword);
            m.totalTermScore += r.termScore;
        }

        List<SearchResult> scored = new ArrayList<>(urlMap.values());
        for (SearchResult r : scored) {
            double score = 0;
            score += Math.log(1 + r.appearances) * 25;
            score += r.waves.size() * 20;
            score += r.keywords.size() * 15;

            // Basic relevance (very simple in Java)
            if (r.title.toLowerCase().contains(query.toLowerCase())) score += 30;

            score += Math.min(r.totalTermScore / 10, 20);

            if (cfg.antiSeo) score += calculateSeoPenalty(r);

            r.score = (int)score;

            StringBuilder ws = new StringBuilder();
            List<Integer> sortedWaves = new ArrayList<>(r.waves);
            Collections.sort(sortedWaves);
            for (int w : sortedWaves) {
                if (ws.length() > 0) ws.append("+");
                ws.append("W").append(w);
            }
            r.waveStr = ws.toString();
        }

        sortSearchResults(scored);

        if (cfg.diversity) {
            List<SearchResult> diverse = new ArrayList<>();
            Map<String, Integer> domainCount = new HashMap<>();
            for (SearchResult r : scored) {
                Integer count = domainCount.get(r.domain);
                if (count == null) count = 0;
                if (count < 2) {
                    diverse.add(r);
                    domainCount.put(r.domain, count + 1);
                }
                if (diverse.size() >= cfg.topN) break;
            }
            return diverse;
        }

        if (scored.size() > cfg.topN) return scored.subList(0, cfg.topN);
        return scored;
    }

    private int calculateSeoPenalty(SearchResult r) {
        int penalty = 0;
        String t = r.title.toLowerCase();
        if (t.contains("best") || t.contains("top 10") || t.contains("review") || t.contains("buy")) penalty -= 12;
        if (r.domain.contains("amazon") || r.domain.contains("pinterest") || r.domain.contains("yelp")) penalty -= 25;
        return penalty;
    }

    private static final String[] PROXIES = {
            "https://api.allorigins.win/raw?url=${u}",
            "https://corsproxy.io/?${u}",
            "https://thingproxy.freeboard.io/fetch/${rawU}",
            "https://cors-anywhere.herokuapp.com/${rawU}"
    };

    private String fetchProxy(String url, int attempt) throws Exception {
        if (cfg.ngu.enabled) {
            return fetchNgu(url);
        }

        // Try pinned proxy if set
        if (cfg.pinnedProxy >= 0 && cfg.pinnedProxy < PROXIES.length) {
            return Networking.fetch(url, PROXIES[cfg.pinnedProxy], attempt, cfg);
        }

        // Try direct first
        try {
            return Networking.fetch(url, null, attempt, cfg);
        } catch (Exception e) {
            if (!cfg.retry) throw e;
            callback.logDebug("warn", "Direct fetch failed, trying proxies: " + e.getMessage());

            // Try each proxy
            for (int i = 0; i < PROXIES.length; i++) {
                if (abortFlag) throw new Exception("Aborted");
                try {
                    return Networking.fetch(url, PROXIES[i], attempt + i + 1, cfg);
                } catch (Exception ex) {
                    callback.logDebug("warn", "Proxy " + i + " failed: " + ex.getMessage());
                }
            }
            throw e;
        }
    }

    private void sortExpansionTerms(List<ExpansionTerm> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(j).score > list.get(i).score) {
                    ExpansionTerm temp = list.get(i);
                    list.set(i, list.get(j));
                    list.set(j, temp);
                }
            }
        }
    }

    private void sortSearchResults(List<SearchResult> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(j).score > list.get(i).score) {
                    SearchResult temp = list.get(i);
                    list.set(i, list.get(j));
                    list.set(j, temp);
                }
            }
        }
    }

    private String fetchNgu(String url) throws Exception {
        int attempt = 0;
        while (!abortFlag) {
            attempt++;
            try {
                String result;
                if (cfg.pinnedProxy >= 0 && cfg.pinnedProxy < PROXIES.length) {
                    result = Networking.fetch(url, PROXIES[cfg.pinnedProxy], attempt, cfg);
                } else if (attempt == 1) {
                    result = Networking.fetch(url, null, attempt, cfg);
                } else {
                    int pIdx = (attempt - 2) % PROXIES.length;
                    result = Networking.fetch(url, PROXIES[pIdx], attempt, cfg);
                }
                callback.logDebug("info", "Fetch successful on attempt " + attempt);
                return result;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                callback.logDebug("warn", "Fetch attempt " + attempt + " failed: " + errorMsg);

                int delay = Math.min(500 * (int)Math.pow(1.6, attempt), 10000);
                callback.onNguUpdate(true, "Hunting (" + errorMsg + ")... wait " + (delay/1000) + "s", attempt, 0);

                for (int i = 0; i < 20; i++) {
                    if (abortFlag) {
                        callback.logDebug("info", "Fetch aborted during NGU wait");
                        throw new Exception("Aborted");
                    }
                    callback.onNguUpdate(true, "Hunting (" + errorMsg + ")... wait " + (delay/1000) + "s", attempt, (i+1)*5);
                    Thread.sleep(delay / 20);
                }
            }
        }
        throw new Exception("Aborted");
    }
}
