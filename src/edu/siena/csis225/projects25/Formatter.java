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
        long hits = topDocs.totalHits.value;  //total num matches
        outStr.append("Found ").append(hits).append(" document(s):\n\n"); //header with count

        // fields to print before snippet
        Set<String> required = new HashSet<>(Arrays.asList(
                "filename", "title", "author"));
        int num = 1; //rank counter

        //HTML formatter to highlight matched terms
        SimpleHTMLFormatter fmt = new SimpleHTMLFormatter("<b>", "</b>");
        QueryScorer qs = new QueryScorer(query);
        Highlighter hl = new Highlighter(fmt, qs);

        //iterate over score docs
        for (ScoreDoc sd : topDocs.scoreDocs) {
            Document doc = searcher.doc(sd.doc); //fetch document by docID
            outStr.append("Result ").append(num++).append(":\n");  //result header

            //print metadata
            outStr.append("Filename: ").append(doc.get("filename"))
                  .append("\n");
            outStr.append("Title: ").append(doc.get("title"))
                  .append("\n");
            outStr.append("Author: ").append(doc.get("author"))
                  .append("\n");

            // compute snippet
            Set<String> extra = new HashSet<>(reqFields);
            extra.removeAll(required);
            String frag = "";
            for (String field : extra) {
                String fieldContent = doc.get(field);
                if (fieldContent != null && !fieldContent.isEmpty()) {
                    //get best fragment for highlighting
                    frag = hl.getBestFragment(analyzer, field, fieldContent);
                    if (frag != null && !frag.isEmpty()) {
                        break; // stop at first non-empty snippet
                    }
                }
            }
            if (frag != null && !frag.isEmpty()) {
                outStr.append("txt snippet: ").append(frag).append("\n"); // append snippet if possible
            }
            
            //always show raw score
            outStr.append("Score: ").append(sd.score)
                  .append("\n");

            // if explain flag is set, include full Lucene score breakdown
            if (showExp) {
                Explanation expl = searcher.explain(query, sd.doc);
                outStr.append(expl.toString())
                      .append("\n");
            }

            outStr.append("-----------------------------\n\n"); //separator between results
        }
        return outStr.toString(); //final output
    }
}
