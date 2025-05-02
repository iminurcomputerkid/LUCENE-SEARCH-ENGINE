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

    public static void run(String qaFile, String indexDir, String truthFile) throws Exception {
        //load ground truth
       Map<Integer,Set<Integer>> ground = loadGroundTruth(truthFile);

        //open index
        var dir = FSDirectory.open(Paths.get(indexDir));
        var reader = DirectoryReader.open(dir);
        var searcher = new IndexSearcher(reader);
       //searcher.setSimilarity(new org.apache.lucene.search.similarities.ClassicSimilarity()); //using BM25 for potentially more precision
        searcher.setSimilarity(new org.apache.lucene.search.similarities.BM25Similarity());
        var analyzer = new StandardAnalyzer();

        //parse QA file for each query:
        try (BufferedReader in = new BufferedReader(new FileReader(qaFile))) {
    String line;
    Integer qID = null;
    StringBuilder qText = new StringBuilder();

    while ((line = in.readLine()) != null) {
        if (line.startsWith(".I")) {
            if (qID != null) {
                processAndPrint(qID, qText.toString(), searcher, analyzer);
            }
            //new query block
            qID = Integer.parseInt(line.substring(2).trim());
            qText.setLength(0);

        } else if (line.startsWith(".W")) {
            //read content of query 
            while ((line = in.readLine()) != null && !line.startsWith(".I")) {
                qText.append(line).append(" ");
            }
            //if inner loop found a new .I, process current
            if (line != null && line.startsWith(".I")) {
                processAndPrint(qID, qText.toString(), searcher, analyzer);
                qID = Integer.parseInt(line.substring(2).trim());
                qText.setLength(0);
            }
        }
    }
    //process last query (if present)
    if (qID != null && qText.length() > 0) {
        processAndPrint(qID, qText.toString(), searcher, analyzer);
    }
    }
   }

    private static Map<Integer,Set<Integer>> loadGroundTruth(String path) throws IOException {
        Map<Integer,Set<Integer>> gt = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new FileReader(path))) {
            String l;
            while ((l = in.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty()) continue;
                String[] parts = l.split("\\s+");
                int q = Integer.parseInt(parts[0]);
                int d = Integer.parseInt(parts[1]);
                gt.computeIfAbsent(q, k->new HashSet<>()).add(d);
            }
        }
        return gt;
    }

    private static void processAndPrint(int qID, String rawQuery, IndexSearcher searcher, Analyzer analyzer) throws Exception {
    QueryParser qp = new QueryParser("content", analyzer);
    // escape all special chars, and add wildcard
    String escaped = QueryParser.escape(rawQuery.trim());
    Query q = qp.parse(escaped + "*");

    TopDocs td = searcher.search(q, 1000);
    for (ScoreDoc sd : td.scoreDocs) {
        String fn = searcher.doc(sd.doc).get("filename");
        int docId = Integer.parseInt(fn.replaceAll("\\.txt$", ""));
        System.out.println(qID + " " + docId);
    }
}
}
