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

    //path to the Lucene index directory
    private String idxPath;
    //flag to include explanation details in output
    private boolean showExplain;
    //converts raw input into Lucene Query
    private queryHandler qryHandler;
    //analyzer used for parsing and highlighting and tokenization
    private Analyzer analyzr;
    //encapsulates IndexSearcher and search logic
    private LuceneSearcher luceneSrc;
    //maximum number of results to return
    private int maxResults;

    //field to search in single-field mode
    private String searchField = "content";
    //flag to enforce phrase-only queries
    private boolean phraseOnly = false;

    /**
     * Constructor for searchHandler
     *
     * @param idxPath, path of Lucene index
     * @param showExplain, include full explanations in results if true
     * @param maxResults, maximum results to return
     * @throws IOException, if index opening fails
     */
    public searchHandler(String idxPath, boolean showExplain, int maxResults) throws IOException {
        this.idxPath = idxPath;
        this.showExplain = showExplain;
        this.maxResults = maxResults;
        //initialize query handler and lucene searcher
        this.qryHandler = new queryHandler();
        this.luceneSrc = new LuceneSearcher(idxPath, maxResults);
        this.luceneSrc.open(); //open index reader and searcher
        this.analyzr = qryHandler.getAnalyzer(); //reuse analyzer from queryHandler
    }

    /**
     * Executes a search for the query string and returns formatted results.
     *
     * @param rawQry, The raw search query
     * @return Formatted results as a String
     * @throws IOException
     * @throws ParseException
     * @throws InvalidTokenOffsetsException
     */
    public String search(String rawQry)
            throws IOException, ParseException, InvalidTokenOffsetsException {

        //trim input and handle empty case
        String text = rawQry == null ? "" : rawQry.trim();
        if (text.isEmpty()) {
            return "";
        }

        final String qstr;
        //apply phrase-only or wildcard suffix
        if (phraseOnly) {
            //wrap in quotes, stripping existing ones
            qstr = "\"" + text.replaceAll("^\"|\"$", "") + "\"";
        } else {
            //append wildcard if there isn't one already
            qstr = text.endsWith("*") ? text : text + "*";
        }

        //set up parser on single field with leading wildcard support
        QueryParser parser = new QueryParser(searchField, analyzr);
        parser.setAllowLeadingWildcard(true);
        //use scoring boolean rewrite for multi-term queries
        parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
        //parse into Lucene Query
        Query q = parser.parse(qstr);

        //execute search to obtain TopDocs
        TopDocs docs = luceneSrc.search(q);

        //fields to include in highlighting and snippet extraction
        Set<String> desiredFields = new HashSet<>(Arrays.asList( "content", "stemcontent", "stopcontent","author", "title", "filepath", "filename", "modified"));
        
        //format and return results
        return Formatter.fromTopDocs(
                luceneSrc.getIndexSearcher(), //IndexSearcher instance
                q,                             //parsed query
                docs,                          //search hits
                analyzr,                       //nalyzer for highlighting
                desiredFields,                 //fields to consider for snippets
                showExplain                    //whether to include explanation
        );
    }

    /**
     * Initiates and continues CLI search process with user.
     */
    public void startCLI() {
        Scanner scnr = new Scanner(System.in);
        //instructions
        System.out.println("query instructions:");
        System.out.println("1. Simple: Type a word (e.g., shakespeare) → auto becomes 'shakespeare*'");
        System.out.println("2. Fielded: Use 'field:term' (e.g., author:shakespeare) -> becomes 'author:shakespeare*'");
        System.out.println("3. Boolean: Combine with AND/OR (e.g., author:shakespeare AND title:hamlet)");
        System.out.println("4. Exact phrase: Enclose in quotes (e.g., \"To be or not to be\")");
        System.out.println("5. Hit Enter on a blank line to exit.");
        System.out.println("-------------------------------------------------");

        //search loop for constant querying until blank line is entered
        while (true) {
            System.out.print("query> ");
            String userInput = scnr.nextLine();
            if (userInput == null || userInput.trim().isEmpty()) {
                break; //exit on blank query
            }
            try {
                String outcome = search(userInput);
                //print results or no results message
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
        //close index reader/searcher
        try {
            luceneSrc.close();
        } catch (IOException e) {
            System.err.println("Error....closing index: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("search done.");
    }

    //setter for GUI slider to adjust maxResults limit
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
        luceneSrc.setLimit(maxResults);
    }

    //setter for single-field search
    public void setSearchField(String field) {
        this.searchField = field;
    }

    //toggle phrase-only mode
    public void setPhraseOnly(boolean p) {
        this.phraseOnly = p;
    }

    //toggle explanation output
    public void setShowExplain(boolean e) {
        this.showExplain = e;
    }

    //return current explanation flag
    public boolean isShowExplain() {
        return this.showExplain;
    }

    /**
     * Inner class to incorporate index opening and search
     */
    public static class LuceneSearcher {

        private org.apache.lucene.store.Directory dir;
        private org.apache.lucene.index.DirectoryReader dirReader;
        private org.apache.lucene.search.IndexSearcher idxSearcher;
        private String idxPath;
        private int limit;

        /**
         * Constructor storing index path and result limit
         */
        public LuceneSearcher(String idxPath, int limit) {
            this.idxPath = idxPath;
            this.limit = limit;
        }

        /**
         * Opens the index directory and initializes IndexSearcher with BM25 similarity
         * @throws IOException on open failure
         */
        public void open() throws IOException {
            dir = org.apache.lucene.store.FSDirectory.open(new File(idxPath).toPath());
            var reader = org.apache.lucene.index.DirectoryReader.open(dir);
            idxSearcher = new org.apache.lucene.search.IndexSearcher(reader);
            idxSearcher.setSimilarity(new org.apache.lucene.search.similarities.BM25Similarity());
        }

        /**
         * Executes the search query and returns TopDocs.
         * @param query, the Lucene Query to execute
         * @return TopDocs containing search hits
         * @throws IOException on search failure
         */
        public TopDocs search(Query query) throws IOException {
            return idxSearcher.search(query, limit);
        }

        /**
         * Helper to adjust the maximum hit limit at runtime
         */
        public void setLimit(int limit) {
            this.limit = limit;
        }

        /**
         * Provides access to IndexSearcher
         * @return IndexSearcher instance
         */
        public org.apache.lucene.search.IndexSearcher getIndexSearcher() {
            return idxSearcher;
        }

        /**
         * Closes index reader and directory resources
         * @throws IOException on close failure
         */
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
