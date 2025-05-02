package edu.siena.csis225.projects25;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.io.File;
import java.io.IOException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.MultiTermQuery;

/**
 * Handles the command line search of user query .
 *
 * @version 4/10/2025
 * @author Julien, Zi’Aire, Riley
 */
public class searchHandler {

    private String idxPath;
    private boolean showExplain;
    private queryHandler qryHandler;
    private Analyzer analyzr;
    private LuceneSearcher luceneSrc;
    private int maxResults;

    private String searchField = "content";
    private boolean phraseOnly = false;

    /**
     * Constructor for searchHandler.
     *
     * @param idxPath, path of lucene index.
     * @param showExplain, If lucene index, include full explanations in
     * results.
     * @param maxResults, Maximum results to return.
     * @throws IOException, if index opening fails.
     */
    public searchHandler(String idxPath, boolean showExplain, int maxResults) throws IOException {
        this.idxPath = idxPath;
        this.showExplain = showExplain;
        this.maxResults = maxResults;
        this.qryHandler = new queryHandler();
        this.luceneSrc = new LuceneSearcher(idxPath, maxResults);
        this.luceneSrc.open();
        this.analyzr = qryHandler.getAnalyzer();
    }

    /**
     * Executes a search for the query string and returns formatted results from
     * formatter class.
     *
     * @param rawQry, The raw search query.
     * @return Formatted results as a String.
     * @throws IOException
     * @throws ParseException
     * @throws InvalidTokenOffsetsException
     */
// at the top of searchHandler.java, add:
    public String search(String rawQry)
            throws IOException, ParseException, InvalidTokenOffsetsException {

        String text = rawQry == null ? "" : rawQry.trim();
        if (text.isEmpty()) {
            return "";
        }

        final String qstr;
        if (phraseOnly) {
            qstr = "\"" + text.replaceAll("^\"|\"$", "") + "\"";
        } else {
            qstr = text.endsWith("*") ? text : text + "*";
        }

        QueryParser parser = new QueryParser(searchField, analyzr);
        parser.setAllowLeadingWildcard(true);
        parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
        Query q = parser.parse(qstr);

        TopDocs docs = luceneSrc.search(q);

        Set<String> desiredFields = new HashSet<>(Arrays.asList(
                "content", "stemcontent", "stopcontent",
                "author", "title", "filepath", "filename", "modified"
        ));
        
        return Formatter.fromTopDocs(
                luceneSrc.getIndexSearcher(),
                q,
                docs,
                analyzr,
                desiredFields,
                showExplain
        );
    }

    /**
     * Initiates and continues CLI searching prcoess with user.
     */
    public void startCLI() {
        Scanner scnr = new Scanner(System.in);
        System.out.println("query instructions:");
        System.out.println("1. Simple: Type a word (e.g., shakespeare) → auto becomes 'shakespeare*'");
        System.out.println("2. Fielded: Use 'field:term' (e.g., author:shakespeare) → auto becomes 'author:shakespeare*'");
        System.out.println("3. Boolean: Combine with AND/OR (e.g., author:shakespeare AND title:hamlet)");
        System.out.println("4. Exact phrase: Enclose in quotes (e.g., \"To be or not to be\")");
        System.out.println("5. Hit Enter on a blank line to exit.");
        System.out.println("-------------------------------------------------");

        while (true) {
            System.out.print("query> ");
            String userInput = scnr.nextLine();
            if (userInput == null || userInput.trim().isEmpty()) {
                break;
            }
            try {
                String outcome = search(userInput);
                if (outcome != null && !outcome.isEmpty()) {
                    System.out.println(outcome);
                } else {
                    System.out.println("No results found.");
                }
            } catch (IOException | ParseException | InvalidTokenOffsetsException ex) {
                System.err.println("Error searching query: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        scnr.close();
        try {
            luceneSrc.close();
        } catch (IOException e) {
            System.err.println("Error....closing index: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("search done.");
    }

    //sets max Results for GUI slider
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
        luceneSrc.setLimit(maxResults);
    }

    public void setSearchField(String field) {
        this.searchField = field;
    }

    public void setPhraseOnly(boolean p) {
        this.phraseOnly = p;
    }

    public void setShowExplain(boolean e) {
        this.showExplain = e;
    }

    public boolean isShowExplain() {
        return this.showExplain;
    }

    /**
     * Inner class LuceneSearcher facilitates index access and query execution.
     */
    public static class LuceneSearcher {

        private org.apache.lucene.store.Directory dir;
        private org.apache.lucene.index.DirectoryReader dirReader;
        private org.apache.lucene.search.IndexSearcher idxSearcher;
        private String idxPath;
        private int limit;

        public LuceneSearcher(String idxPath, int limit) {
            this.idxPath = idxPath;
            this.limit = limit;
        }

        public void open() throws IOException {
            dir = org.apache.lucene.store.FSDirectory.open(new File(idxPath).toPath());
            var reader = org.apache.lucene.index.DirectoryReader.open(dir);
            idxSearcher = new org.apache.lucene.search.IndexSearcher(reader);
            idxSearcher.setSimilarity(new org.apache.lucene.search.similarities.BM25Similarity());
        }

        public TopDocs search(Query query) throws IOException {
            return idxSearcher.search(query, limit);
        }

        //helper for result limit
        public void setLimit(int limit) {
            this.limit = limit;
        }

        public org.apache.lucene.search.IndexSearcher getIndexSearcher() {
            return idxSearcher;
        }

        public org.apache.lucene.index.DirectoryReader getReader() {
            return dirReader;
        }

        public void close() throws IOException {
            if (dirReader != null) {
                dirReader.close();
            }
            if (dir != null) {
                dir.close();
            }
        }
    }
}
