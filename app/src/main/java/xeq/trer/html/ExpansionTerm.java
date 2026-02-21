package xeq.trer.html;

import java.util.HashSet;
import java.util.Set;

public class ExpansionTerm {
    public String term;
    public double freq;
    public Set<String> sources = new HashSet<>();
    public double score;
    public String tag;

    public ExpansionTerm(String term) {
        this.term = term;
    }
}
