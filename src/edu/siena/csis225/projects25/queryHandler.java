package edu.siena.csis225.projects25;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;

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

    private static final String[] SEARCH_FIELDS = new String[]{"content", "stemcontent", "stopcontent", "author", "title", "filename", "filepath", "modified"};
    
    private Analyzer analyzer;
    private String inputQuery;
    private Query parsedQuery;
    
    /**
    * Constructs a new queryHandler using StandardAnalyzer.
    */
    public queryHandler() {
        this.analyzer = new StandardAnalyzer();
    }
    
    /**
     * Sets the user inputted raw query to the query to be processed.
     *
     * @param query, the user's raw query
     */
    public void setInputQuery(String query) {
        this.inputQuery = query;
    }
    
    /**
     * Transforms the previously set input query into a Lucene query:
     * Splits the input into tokens, appending trailing wildcards to each term
     * except Boolean operators and quoted phrases. parses the result using a
     * MultiFieldQueryParser over search fields.
     *
     * @throws ParseException, if the parsed query string is invalid
     */
    public void processQuery() throws ParseException {
        if (inputQuery == null || inputQuery.trim().isEmpty()) {
            parsedQuery = null;
            return;
        }
        String trimmedQuery = inputQuery.trim();
        String modified = appendWildcards(trimmedQuery);
        MultiFieldQueryParser parser = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer);
        parser.setAllowLeadingWildcard(true);
        parsedQuery = parser.parse(modified);
    }
    
     /**
     * Appends a '*' to each token in the query except
     * for Boolean operators and terms already have a wildcard. Also
     * preserves quoted phrases (no wildcard appended).
     *
     * @param queryStr, the raw query in need of wildcard
     * @return a new query with wildcards applied
     */
    private String appendWildcards(String queryStr) {
        //splits query into tokens
        String[] parts = queryStr.split("\\s+");
        //collection of operators that won't receive wildcards, essential for functional boolean queries
        Set<String> operators = new HashSet<>(Arrays.asList("AND", "OR", "NOT", "and", "or", "not"));
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            //if boolean operator detected in query, leave it alone. Do not append operator with "*"
            if (operators.contains(part)) {
                sb.append(part).append(" ");
                continue;
            }
            //if query is a quote, leave it alone.
            if (part.startsWith("\"") && part.endsWith("\"")) {
                sb.append(part).append(" ");
                continue;
            }
            //processes value of fielded queries, appends wildcard to value
            if (part.contains(":")) {
                String[] segments = part.split(":", 2);
                String field = segments[0];
                String val = segments[1];
                if (!val.contains("*")) {
                    val = val + "*";
                }
                sb.append(field).append(":").append(val).append(" ");
             //otherwise, should be a regular query. Append wildcard to token. 
            } else {
                if (!part.contains("*")) {
                    part = part + "*";
                }
                sb.append(part).append(" ");
            }
        }
        return sb.toString().trim();
    }
    
    /**
     * Returns the last parsed query
     *
     * @return the Lucene Query, or null if none found
     */
    public Query getQuery() {
        return parsedQuery;
    }
    
     /**
     * Returns the analyzer used for parsing and highlighting.
     *
     * @return the Analyzer, null if analyzer not found
     */
    public Analyzer getAnalyzer() {
        return analyzer;
    }
}
