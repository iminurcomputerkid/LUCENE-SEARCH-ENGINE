package edu.siena.csis225.projects25;

import java.util.Map;
import java.util.HashMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.queryparser.classic.QueryParser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
/**
 * Converts raw user input to a Lucene query that searches across multiple fields
 * with trailing wildcards.  Boolean operators and quoted phrases
 * are preserved without modification.
 *
 * @version 4/10/2025
 * @author Julien, Zi'Aire, Riley
 */
public class queryHandler {

    // fields to search when using multi-field parser
    private static final String[] SEARCH_FIELDS = new String[]{
        "content", "stemcontent", "stopcontent",
        "author", "title", "filepath", "modified", "filename"
    };
    
    private Analyzer analyzer;    //analyzer for parsing, highlighting an dtokenizing
    private String inputQuery;    //raw user input string
    private Query parsedQuery;    //resulting Lucene Query object
    
    private String searchField = SEARCH_FIELDS[0];  //default single-field search
    private boolean phraseOnly = false;             //flag for phrase-only queries

    /**
     * Constructs a new queryHandler using StandardAnalyzer.
     */
    public queryHandler() {
        //initialize analyzer for tokenization and filtering
        this.analyzer = new StandardAnalyzer();
    }
    
    /**
     * Sets the raw user query string to be processed
     *
     * @param query the user's raw query input
     */
    public void setInputQuery(String query) {
        this.inputQuery = query;
    }
    
    /**
     * Processes the previously set input query into a Lucene Query:
     * splits tokens, appends wildcards (unless operators or phrases),
     * and parses using MultiFieldQueryParser or single-field parser
     *
     * @throws ParseException if parsing fails
     */
    public void processQuery() throws ParseException {
        // if no input or blank, skip processing
        if (inputQuery == null || inputQuery.trim().isEmpty()) {
            parsedQuery = null;
            return;
        }
        //trim whitespace
        String trimmed = inputQuery.trim();
        //if phrase-only mode, wrap in quotes. else append wildcards
        String mod;
        if (phraseOnly) {
            //remove existing quotes before wrapping
            mod = "\"" + trimmed.replaceAll("^\"|\"$", "") + "\"";
        } else {
            mod = appendWildcards(trimmed);
        }

        //parse on the single selected field with leading wildcards allowed
        QueryParser parser = new QueryParser(searchField, analyzer);
        parser.setAllowLeadingWildcard(true);
        parsedQuery = parser.parse(mod);
    }
    
    /**
     * Appends '*' wildcard to each token except boolean operators, quoted phrases,
     * or tokens that already contain a wildcard. Maintains fielded queries.
     *
     * @param queryStr, raw query needing wildcards
     * @return modified, query with trailing wildcards
     */
    private String appendWildcards(String queryStr) {
        //split on whitespace
        String[] parts = queryStr.split("\\s+");
        //boolean operators to leave untouched
        Set<String> operators = new HashSet<>(
            Arrays.asList("AND","OR","NOT","and","or","not")
        );
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (operators.contains(part)) {
                //boolean operator, append as is
                sb.append(part).append(" ");
                continue;
            }
            if (part.startsWith("\"") && part.endsWith("\"")) {
                //quoted phrase, leave alone
                sb.append(part).append(" ");
                continue;
            }
            if (part.contains(":")) {
                //fielded query: split into field and value
                String[] seg = part.split(":", 2);
                String field = seg[0];
                String val = seg[1];
                if (!val.contains("*")) {
                    val = val + "*"; //append wildcard if missing
                }
                sb.append(field).append(":").append(val).append(" ");
            } else {
                //regular token: append wildcard if missing
                if (!part.contains("*")) {
                    part = part + "*";
                }
                sb.append(part).append(" ");
            }
        }
        //return trimmed result
        return sb.toString().trim();
    }
    
    /**
     * Returns the last parsed Lucene Query.
     *
     * @return parsed Query, or null if none
     */
    public Query getQuery() {
        return parsedQuery;
    }
    
    /**
     * Returns the Analyzer used for parsing/highlighting.
     *
     * @return Analyzer instance
     */
    public Analyzer getAnalyzer() {
        return analyzer;
    }
}