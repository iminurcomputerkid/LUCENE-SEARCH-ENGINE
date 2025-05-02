package edu.siena.csis225.projects25;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.QueryParser;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

/**
 * Reads CranfieldQA file, runs each query against the index, and
 * ouptuts data in "qID docID" format for each retrieved hit
 * 
 * @author Julien Niles, Riley Pierson, Zi'Aire Tiarado
 * @version 5/2/2025
 */
public class CranfieldQAEvaluator {

    /**
     * Entry point: loads ground truth, opens index, and processes QA file.
     * @param, qaFile, cranfieldQAfile
     * @param  indexDir, directory of lucene indices
     * @param   truthFile directory of groundTruth file
     * @throws Exception
     */
    public static void run(String qaFile, String indexDir, String truthFile) throws Exception {
        //mapping of query IDs to their document IDs
        Map<Integer,Set<Integer>> ground = loadGroundTruth(truthFile);

        //open Lucene index directory
        var dir = FSDirectory.open(Paths.get(indexDir));
        var reader = DirectoryReader.open(dir);
        var searcher = new IndexSearcher(reader);
        
        //use Classic Similarity for socring and ranking rauther than ClassicSimilarity
        searcher.setSimilarity(new org.apache.lucene.search.similarities.ClassicSimilarity());
        var analyzer = new StandardAnalyzer();

        //read and parse QA file line by line
        try (BufferedReader in = new BufferedReader(new FileReader(qaFile))) {
            String line;
            //current 
            Integer qID = null;    
            
            //query text 
            StringBuilder qText = new StringBuilder(); 

            while ((line = in.readLine()) != null) {
                if (line.startsWith(".I")) {
                    //encountered new query block marker
                    if (qID != null) {
                        // process previous query before starting new one
                        processAndPrint(qID, qText.toString(), searcher, analyzer);
                    }
                    // parse new query ID (integer after ".I")
                    qID = Integer.parseInt(line.substring(2).trim());
                    //resest text buffer
                    qText.setLength(0); 

                } else if (line.startsWith(".W")) {
                    // start reading query content
                    while ((line = in.readLine()) != null && !line.startsWith(".I")) {
                        //append each line to query text
                        qText.append(line).append(" "); 
                    }
                    //if stopped because of a new ".I" marker, process current and prepare next
                    if (line != null && line.startsWith(".I")) {
                        processAndPrint(qID, qText.toString(), searcher, analyzer);
                        qID = Integer.parseInt(line.substring(2).trim());
                        qText.setLength(0);
                    }
                }
                // lines not starting with .I or .W are ignored
            }
            //process the last query if present
            if (qID != null && qText.length() > 0) {
                processAndPrint(qID, qText.toString(), searcher, analyzer);
            }
        }
    }

    /**
     * Loads the ground truth mapping from file where each line is "qID docID"
     * @param path, path to groundTruth file
     * @throws IOException
     */
    private static Map<Integer,Set<Integer>> loadGroundTruth(String path) throws IOException {
        Map<Integer,Set<Integer>> gt = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new FileReader(path))) {
            String l;
            while ((l = in.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty()) continue;            // skip empty lines
                String[] parts = l.split("\\s+");  // split on whitespace
                int q = Integer.parseInt(parts[0]);  //qID
                int d = Integer.parseInt(parts[1]);  // docID
                // add doc ID to the set for this query
                gt.computeIfAbsent(q, k -> new HashSet<>()).add(d);
            }
        }
        return gt;
    }

    /**
     * Parses a raw query string, runs it against the index, and prints "qID docID" for each
     * @param qID, queryID
     * @param rawQuery, query from QA file
     * @param searcher, search executer
     * @param analyzer the tokenizer
     * @throws Exception
     */
    private static void processAndPrint(int qID, String rawQuery, IndexSearcher searcher, Analyzer analyzer) throws Exception {
        //parse query on content field
        QueryParser qp = new QueryParser("content", analyzer);
        //escape special chars then add a trailing wildcard
        String escaped = QueryParser.escape(rawQuery.trim());
        Query q = qp.parse(escaped + "*");

        //execute the search, retrieving up to 1000 hits
        TopDocs td = searcher.search(q, 1000);
        for (ScoreDoc sd : td.scoreDocs) {
            //extract the filename field
            String fn = searcher.doc(sd.doc).get("filename");
            //strip ".txt" and parse ID num
            int docId = Integer.parseInt(fn.replaceAll("\\.txt$", ""));
            //print qID, docId format
            System.out.println(qID + " " + docId);
        }
    }
}