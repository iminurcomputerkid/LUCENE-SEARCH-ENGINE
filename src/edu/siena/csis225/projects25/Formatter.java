package edu.siena.csis225.projects25;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.analysis.Analyzer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Formats TopDocs results to readable format
 *
 * @version 4/10/2025
 * @author Julien, Zi’Aire, Riley
 */
public class Formatter {

    /**
     * Processes result from TopDocs and returns formatted string.
     *
     * @param searcher, IndexSearcher used
     * @param query, executed query
     * @param topDocs, TopDocs result
     * @param analyzer, the Analyzer used for highlighting
     * @param reqFields, the set of fields to consider
     * @param showExp, if true, list full explanation details
     * @return, formatted string with the search results.
     * @throws IOException
     * @throws InvalidTokenOffsetsException
     *
     */
    public static String fromTopDocs(IndexSearcher searcher, Query query, TopDocs topDocs, Analyzer analyzer, Set<String> reqFields, boolean showExp) throws IOException, InvalidTokenOffsetsException {
        StringBuilder outStr = new StringBuilder();
        long hits = topDocs.totalHits.value;
        outStr.append("Found ").append(hits).append(" document(s):\n\n");
        Set<String> required = new HashSet<>(Arrays.asList("filename", "title", "author"));
        int num = 1;
        //initializes bolding of word
        SimpleHTMLFormatter fmt = new SimpleHTMLFormatter("<b>", "</b>");
        QueryScorer qs = new QueryScorer(query);
        Highlighter hl = new Highlighter(fmt, qs);
        for (ScoreDoc sd : topDocs.scoreDocs) {
            Document doc = searcher.doc(sd.doc);
            outStr.append("Result ").append(num++).append(":\n");
            outStr.append("Filename: ").append(doc.get("filename")).append("\n");
            outStr.append("Title: ").append(doc.get("title")).append("\n");
            outStr.append("Author: ").append(doc.get("author")).append("\n");
            Set<String> extra = new HashSet<>(reqFields);
            extra.removeAll(required);
            String frag = "";
            for (String field : extra) {
                String fieldContent = doc.get(field);
                if (fieldContent != null && !fieldContent.isEmpty()) {
                    //bolded word
                    frag = hl.getBestFragment(analyzer, field, fieldContent);
                    if (frag != null && !frag.isEmpty()) {
                        break;
                    }
                }
            }
            if (frag != null && !frag.isEmpty()) {
                outStr.append("txt snippet: ").append(frag).append("\n");
            }
            
            //shows loat score 
            outStr.append("Score: ").append(sd.score).append("\n");

            //if explain button checked, provides full lucene scoring explanation
            if (showExp) {
                Explanation expl = searcher.explain(query, sd.doc);
                outStr.append(expl.toString()).append("\n");
            }

            outStr.append("-----------------------------\n\n");
        }
        return outStr.toString();
    }
}
