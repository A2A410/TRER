package xeq.trer.html;

import android.util.Log;
import com.google.gson.Gson;
import java.util.*;

public class TREREngine {
    private static final String TAG = "TREREngine";
    private final Models.Config cfg;
    private final EngineCallback callback;
    private boolean abortFlag = false;
    private final Gson gson = new Gson();

    public interface EngineCallback {
        void onStatus(String type, String msg);
        void onWaveUpdate(int n, String state, int pct, String stat);
        void onKeywordsFound(String query, List<Models.ExpansionTerm> terms);
        void onResultsFound(List<Models.SearchResult> results, String query);
        void onProgress(String msg);
        void onNguUpdate(boolean show, String text, int attempt, int pct);
        void onFinished(boolean success);
        void logDebug(String lvl, String msg);
    }

    public TREREngine(Models.Config cfg, EngineCallback callback) {
        this.cfg = cfg;
        this.callback = callback;
    }

    public void abort() {
        this.abortFlag = true;
    }

    public void run(final String query) {
        new Thread(new EngineRunnable(query)).start();
    }

    private class EngineRunnable implements Runnable {
        private final String query;
        EngineRunnable(String query) {
            this.query = query;
        }
        @Override
        public void run() {
            try {
                execute(query);
                callback.onFinished(true);
            } catch (Exception e) {
                callback.onStatus("err", "Error: " + e.getMessage());
                callback.logDebug("error", "Engine error: " + e.getMessage());
                callback.onFinished(false);
            }
        }
    }

    private void execute(String query) throws Exception {
        callback.logDebug("info", "Starting Wave 1...");
        callback.onWaveUpdate(1, "active", 0, "0%");

        // --- WAVE 1: Expansion ---
        callback.onStatus("info", "W1: Expanding keywords...");
        List<Models.ExpansionTerm> terms = wave1(query);
        if (abortFlag) return;
        callback.onKeywordsFound(query, terms);
        callback.onWaveUpdate(1, "done", 100, terms.size() + "kw");

        // --- WAVE 2: Harvest ---
        callback.logDebug("info", "Starting Wave 2...");
        callback.onWaveUpdate(2, "active", 0, "0%");
        List<Models.SearchResult> allResults = wave2(query, terms);
        if (abortFlag) return;
        callback.onWaveUpdate(2, "done", 100, allResults.size() + "u");

        // --- WAVE 3: Scoring ---
        callback.logDebug("info", "Starting Wave 3...");
        callback.onWaveUpdate(3, "active", 0, "0%");
        List<Models.SearchResult> finalResults = wave3(query, allResults);
        callback.onResultsFound(finalResults, query);
        callback.onWaveUpdate(3, "done", 100, "TOP " + finalResults.size());

        callback.onStatus("ok", "Search complete: " + finalResults.size() + " results");
    }

    private List<Models.ExpansionTerm> wave1(String query) throws Exception {
        Map<String, Models.ExpansionTerm> termMap = new HashMap<>();

        // A: Autocomplete
        callback.onWaveUpdate(1, "active", 10, "10%");
        try {
            String acUrl = "https://ac.duckduckgo.com/ac/?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&type=list";
            if ("google_lite".equals(cfg.engine)) {
                acUrl = "https://suggestqueries.google.com/complete/search?client=chrome&q=" + java.net.URLEncoder.encode(query, "UTF-8");
            }
            String acJson = fetchWithEngine(acUrl, 0);
            List<Object> acList = gson.fromJson(acJson, List.class);
            if (acList.size() > 1 && acList.get(1) instanceof List) {
                List<String> suggestions = (List<String>) acList.get(1);
                for (String sug : suggestions) {
                    addTerm(termMap, sug, "autocomplete", 3.0);
                }
            }
        } catch (Exception e) {
            callback.logDebug("warn", "Autocomplete failed: " + e.getMessage());
        }

        // B: HTML Related
        callback.onWaveUpdate(1, "active", 50, "50%");
        try {
            String url = getEngineUrl(query, cfg.engine, 0);
            String html = fetchWithEngine(url, 1);
            List<String> related = Networking.parseRelatedSearches(html);
            for (String r : related) {
                addTerm(termMap, r, "html_related", 2.5);
            }
        } catch (Exception e) {
            callback.logDebug("warn", "HTML Related failed: " + e.getMessage());
        }

        // Score and select
        List<Models.ExpansionTerm> scored = new ArrayList<>(termMap.values());
        for (Models.ExpansionTerm t : scored) {
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

        Collections.sort(scored, new ScoredComparator());

        List<Models.ExpansionTerm> finalTerms = new ArrayList<>();
        for (Models.ExpansionTerm t : scored) {
            if (finalTerms.size() >= cfg.maxKw) break;
            finalTerms.add(t);
        }
        return finalTerms;
    }

    private void addTerm(Map<String, Models.ExpansionTerm> map, String term, String source, double weight) {
        term = term.toLowerCase().trim();
        if (term.length() < 3) return;
        Models.ExpansionTerm et = map.get(term);
        if (et == null) {
            et = new Models.ExpansionTerm(term);
            map.put(term, et);
        }
        et.freq += weight;
        et.sources.add(source);
    }

    private List<Models.SearchResult> wave2(String query, List<Models.ExpansionTerm> terms) throws Exception {
        List<Models.SearchResult> all = new ArrayList<>();

        // Initial results from query
        String currentEngine = cfg.engine;
        String initialUrl = getEngineUrl(query, currentEngine, 0);
        String initialHtml = fetchWithEngine(initialUrl, 0);
        List<Models.SearchResult> initialResults = parseResults(initialHtml, currentEngine);
        for (Models.SearchResult r : initialResults) {
            r.wave = 1;
            r.keyword = query;
            all.add(r);
        }

        for (int i = 0; i < terms.size(); i++) {
            if (abortFlag) break;
            Models.ExpansionTerm t = terms.get(i);
            String q = query + " " + t.term;
            callback.onStatus("info", "W2 [" + (i+1) + "/" + terms.size() + "]: " + t.term);
            callback.onWaveUpdate(2, "active", (int)(((double)i/terms.size())*100), (i+1)+"/"+terms.size());

            if ("rotate".equals(cfg.engine)) {
                currentEngine = (i % 2 == 0) ? "google_lite" : "ddg_lite";
            }

            Thread.sleep(cfg.delay);
            try {
                String url = getEngineUrl(q, currentEngine, i + 1);
                String html = fetchWithEngine(url, i + 1);
                List<Models.SearchResult> results = parseResults(html, currentEngine);
                for (Models.SearchResult r : results) {
                    r.wave = 2;
                    r.keyword = t.term;
                    r.termScore = t.score;
                    all.add(r);
                }
            } catch (Exception e) {
                callback.logDebug("warn", "W2 query failed: " + t.term + " (" + e.getMessage() + ")");
            }
        }
        return all;
    }

    private String getEngineUrl(String q, String engine, int attempt) throws Exception {
        String query = java.net.URLEncoder.encode(q, "UTF-8");
        if ("google_lite".equals(engine)) {
            return "https://www.google.com/search?q=" + query + "&gbv=1";
        } else if ("rotate".equals(engine)) {
            return getEngineUrl(q, (attempt % 2 == 0) ? "ddg_lite" : "google_lite", attempt);
        } else {
            return "https://lite.duckduckgo.com/lite/?q=" + query;
        }
    }

    private List<Models.SearchResult> parseResults(String html, String engine) {
        if ("google_lite".equals(engine)) {
            return Networking.parseGoogleLiteHtml(html, cfg.resPerKw);
        } else if ("ddg_lite".equals(engine)) {
            return Networking.parseDDGLiteHtml(html, cfg.resPerKw);
        } else {
            // Fallback to auto-detect or default
            if (html.contains("google.com")) return Networking.parseGoogleLiteHtml(html, cfg.resPerKw);
            return Networking.parseDDGLiteHtml(html, cfg.resPerKw);
        }
    }

    private List<Models.SearchResult> wave3(String query, List<Models.SearchResult> allResults) {
        Map<String, Models.SearchResult> urlMap = new HashMap<>();
        for (Models.SearchResult r : allResults) {
            Models.SearchResult m = urlMap.get(r.url);
            if (m == null) {
                m = r;
                urlMap.put(r.url, m);
            }
            m.appearances++;
            m.waves.add(r.wave);
            m.keywords.add(r.keyword);
            m.totalTermScore += r.termScore;
        }

        List<Models.SearchResult> scored = new ArrayList<>(urlMap.values());
        for (Models.SearchResult r : scored) {
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

        Collections.sort(scored, new SearchResultComparator());

        if (cfg.diversity) {
            List<Models.SearchResult> diverse = new ArrayList<>();
            Map<String, Integer> domainCount = new HashMap<>();
            for (Models.SearchResult r : scored) {
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

    private static class ScoredComparator implements Comparator<Models.ExpansionTerm> {
        @Override
        public int compare(Models.ExpansionTerm a, Models.ExpansionTerm b) {
            return Double.compare(b.score, a.score);
        }
    }

    private static class SearchResultComparator implements Comparator<Models.SearchResult> {
        @Override
        public int compare(Models.SearchResult a, Models.SearchResult b) {
            return b.score - a.score;
        }
    }

    private int calculateSeoPenalty(Models.SearchResult r) {
        int penalty = 0;
        String t = r.title.toLowerCase();
        if (t.contains("best") || t.contains("top 10") || t.contains("review") || t.contains("buy")) penalty -= 12;
        if (r.domain.contains("amazon") || r.domain.contains("pinterest") || r.domain.contains("yelp")) penalty -= 25;
        return penalty;
    }

    private String fetchWithEngine(String url, int attempt) throws Exception {
        if (cfg.ngu.enabled) {
            return fetchNgu(url);
        }

        try {
            return Networking.fetch(url, attempt);
        } catch (Exception e) {
            if (!cfg.retry) throw e;
            callback.logDebug("warn", "Fetch failed, retrying: " + e.getMessage());
            // Retry once more with a different User-Agent (handled by Networking.fetch's attempt param)
            return Networking.fetch(url, attempt + 1);
        }
    }

    private String fetchNgu(String url) throws Exception {
        int attempt = 0;
        while (!abortFlag) {
            attempt++;
            try {
                String result = Networking.fetch(url, attempt);
                callback.logDebug("info", "Fetch successful on attempt " + attempt);
                return result;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                callback.logDebug("warn", "Fetch attempt " + attempt + " failed: " + errorMsg);

                int delay = Math.min(1000 * (int)Math.pow(1.5, attempt), 30000);
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
