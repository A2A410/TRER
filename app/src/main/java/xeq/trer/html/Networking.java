package xeq.trer.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Networking {

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
    };

    public static String fetch(String urlString, String proxyPattern, int attempt) throws Exception {
        String targetUrl = urlString;
        if (proxyPattern != null) {
            // Handle specific proxy patterns from the original HTML
            if (proxyPattern.contains("api.allorigins.win")) {
                targetUrl = "https://api.allorigins.win/raw?url=" + java.net.URLEncoder.encode(urlString, "UTF-8");
            } else if (proxyPattern.contains("corsproxy.io")) {
                targetUrl = "https://corsproxy.io/?" + java.net.URLEncoder.encode(urlString, "UTF-8");
            } else if (proxyPattern.contains("thingproxy.freeboard.io")) {
                targetUrl = "https://thingproxy.freeboard.io/fetch/" + urlString;
            } else if (proxyPattern.contains("cors-anywhere.herokuapp.com")) {
                targetUrl = "https://cors-anywhere.herokuapp.com/" + urlString;
            } else {
                targetUrl = proxyPattern.replace("${u}", java.net.URLEncoder.encode(urlString, "UTF-8"))
                                        .replace("${encU}", java.net.URLEncoder.encode(urlString, "UTF-8"))
                                        .replace("${rawU}", urlString);
            }
        }

        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENTS[attempt % USER_AGENTS.length]);
        conn.setRequestProperty("Accept", "text/html,application/json,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setRequestProperty("Referer", "https://duckduckgo.com/");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);

        int responseCode = conn.getResponseCode();
        boolean isSuccess = responseCode == HttpURLConnection.HTTP_OK;

        InputStream stream = isSuccess ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null && !isSuccess) {
            throw new Exception("HTTP " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        if (stream != null) {
            BufferedReader in = new BufferedReader(new InputStreamReader(stream));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        }

        if (isSuccess) {
            return response.toString();
        } else {
            String errorBody = response.toString();
            if (errorBody.length() > 200) errorBody = errorBody.substring(0, 200) + "...";
            throw new Exception("HTTP " + responseCode + (errorBody.isEmpty() ? "" : ": " + errorBody));
        }
    }

    public static List<Models.SearchResult> parseDDGHtml(String html, int maxResults) {
        List<Models.SearchResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        // Try multiple selectors
        Elements cards = doc.select("#links .result, .result__body");
        for (Element card : cards) {
            if (card.hasClass("result--more")) continue;

            Element titleEl = card.select(".result__a").first();
            if (titleEl == null) continue;

            String title = titleEl.text().trim();
            if (title.length() < 3) continue;

            String url = titleEl.attr("href");
            if (url.startsWith("//")) url = "https:" + url;

            // Decode DDG redirect
            if (url.contains("/l/?") || url.contains("/l?")) {
                try {
                    int start = url.indexOf("uddg=");
                    if (start != -1) {
                        int end = url.indexOf("&", start);
                        String encoded = (end == -1) ? url.substring(start + 5) : url.substring(start + 5, end);
                        url = URLDecoder.decode(encoded, "UTF-8");
                    }
                } catch (Exception e) {}
            }

            if (!url.startsWith("http") || url.contains("duckduckgo.com/html")) continue;

            Element snippetEl = card.select(".result__snippet").first();
            String snippet = (snippetEl != null) ? snippetEl.text().trim() : "";

            String domain = getDomain(url);
            if (domain != null && !title.isEmpty()) {
                results.add(new Models.SearchResult(title, url, domain, snippet));
            }
            if (results.size() >= maxResults) break;
        }

        if (results.isEmpty()) {
            for (Element a : doc.select("a[href]")) {
                String url = a.attr("href");
                if (url.startsWith("//")) url = "https:" + url;
                if (url.contains("/l/?") || url.contains("/l?")) {
                    try {
                        int start = url.indexOf("uddg=");
                        if (start != -1) {
                            int end = url.indexOf("&", start);
                            String encoded = (end == -1) ? url.substring(start + 5) : url.substring(start + 5, end);
                            url = URLDecoder.decode(encoded, "UTF-8");
                        }
                    } catch (Exception e) {}
                }
                if (!url.startsWith("http") || url.contains("duckduckgo.com")) continue;
                String title = a.text().trim();
                if (title.length() < 5) continue;
                String domain = getDomain(url);
                results.add(new Models.SearchResult(title, url, domain, ""));
                if (results.size() >= maxResults) break;
            }
        }

        return results;
    }

    public static List<String> parseRelatedSearches(String html) {
        List<String> related = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements links = doc.select(".result--more .result__a, .related-searches__link, .results_links_more a, .zci__related-searches a");
        for (Element a : links) {
            String t = a.text().trim();
            if (!t.isEmpty() && t.length() > 2 && t.length() < 80) {
                if (!related.contains(t)) related.add(t);
            }
        }
        return related;
    }

    public static String getDomain(String url) {
        try {
            String host = new URL(url).getHost();
            if (host.startsWith("www.")) return host.substring(4);
            return host;
        } catch (Exception e) {
            return url;
        }
    }

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the","and","for","that","this","with","from","are","was","were","has","have","had","not","but","what","when","where","who","how","its","will","can","may","about","also","than","then","there","their","they","been","being","your","our","any","all","each","both","few","more","most","other","some","such","into","onto","after","before","during","without","between","through","these","those","only","just","now","new","first","last","long","great","since","per","via","vs","etc","get","use","make","like","time","need","very","well","over","even","still","back","way","take","give"
    ));

    public static List<String> tokenize(String s) {
        String[] parts = s.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (p.length() > 2 && !STOPWORDS.contains(p)) {
                tokens.add(p);
            }
        }
        return tokens;
    }
}
