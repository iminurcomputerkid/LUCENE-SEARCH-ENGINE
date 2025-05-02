package edu.siena.csis225.projects25;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * Parallel processing version of Indexer. Uses a ThreadedIndexWriter to index with multiple threads.
 *
 * @version 4/24/2025
 * @author Julien, Riley, Zi’Aire
 */
public class PIndexer {

    static class Stats {
        int newDocs = 0;      //count of added documents
        int updatedDocs = 0;  //count of updated documents
        int deletedDocs = 0;  //count of deleted documents
    }

    /**
     * Runs parallel indexing if -parallel flag is set, then prints stats.
     *
     * @param inFolder, path to directory of .txt files to index
     * @param outIndexFolder, path to directory where Lucene index is stored
     * @param indexingMode, all,new, changed, or missing mode
     * @param isGutenberg, true if input files are Gutenberg, false if Cranfield
     * @param numThreads, number of threads for ThreadedIndexWriter
     * @param maxQueueSize, max number of jobs queued for threading
     */
    public static void run(String inFolder, String outIndexFolder, String indexingMode, boolean isGutenberg, int numThreads, int maxQueueSize) {
        long start = System.currentTimeMillis(); //start timer
        Stats stats;
        try {
            //delegate to parallel indexing
            stats = indexFilesParallel(
                inFolder,
                outIndexFolder,
                (indexingMode == null ? "all" : indexingMode.toLowerCase()),
                isGutenberg
            );
        } catch (IOException e) {
            e.printStackTrace();
            //abort on error
            return;                                              
        }
        //compute duration
        long duration = System.currentTimeMillis() - start;    

        //print summary stats of parallel indexing
        System.out.println("Parallel indexing finished.");
        System.out.println("Added:   " + stats.newDocs);
        System.out.println("Updated: " + stats.updatedDocs);
        System.out.println("Removed: " + stats.deletedDocs);
        
        //report total documents in index
        try (DirectoryReader r = DirectoryReader.open(
                FSDirectory.open(Paths.get(outIndexFolder)))) {
            System.out.println("Total Docs:  " + r.numDocs());
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Duration: " + duration + " ms"); // print timing
    }

    /**
     * Issues indexing commands to ThreadedIndexWriter in parallel.
     *
     * @param dataDirPath      input folder of .txt files
     * @param indexDirPath     output index folder path
     * @param mode             "all", "new", "changed", or "missing"
     * @param isGutenberg      limit content extraction if true
     * @return                 Stats object with counts of operations
     * @throws IOException     on I/O errors
     */
    private static Stats indexFilesParallel(String dataDirPath, String indexDirPath, String mode, boolean isGutenberg) throws IOException {
        //prepare index directory and writer config
        Directory dir = FSDirectory.open(Paths.get(indexDirPath));
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        cfg.setSimilarity(new ClassicSimilarity()); // use TF-IDF similarity

        //initialize threaded writer with thread and queue sizes
        int numThreads  = Runtime.getRuntime().availableProcessors() + 1;
        int maxQueueSize  = numThreads * 4;
        ThreadedIndexWriter writer = new ThreadedIndexWriter(dir, cfg, numThreads, maxQueueSize);  // maxQueueSize
        Stats stats = new Stats();

        //read existing index metadata
        Map<String, Long> indexed = new HashMap<>();
        if (DirectoryReader.indexExists(dir)) {
            try (DirectoryReader rdr = DirectoryReader.open(dir)) {
                for (LeafReaderContext ctx : rdr.leaves()) {
                    LeafReader lr = ctx.reader();
                    Bits live = lr.getLiveDocs();
                    for (int i = 0; i < lr.maxDoc(); i++) {
                        if (live != null && !live.get(i)) continue; // skip deleted docs
                        var d = lr.document(i);
                        indexed.put(
                            d.get("filepath"),
                            Long.parseLong(d.get("modified"))
                        );
                    }
                }
            }
        }

        //list .txt files in data dir
        File dataDir = new File(dataDirPath);
        File[] files = dataDir.listFiles((d, n) -> n.toLowerCase().endsWith(".txt"));
        Set<String> seen = new HashSet<>();
        if (files != null) {
            for (File f : files) {
                String path = f.getAbsolutePath();
                seen.add(path); //mark file as processed
                long oldMod = indexed.getOrDefault(path, -1L);
                //perform actions based on mode
                switch (mode) {
                    case "all" -> {
                        if (oldMod < 0) {
                            writer.addDocument(buildDoc(f, isGutenberg));
                            stats.newDocs++;
                        } else if (f.lastModified() > oldMod) {
                            writer.updateDocument(
                                new Term("filepath", path),
                                buildDoc(f, isGutenberg)
                            );
                            stats.updatedDocs++;
                        }
                    }
                    case "new" -> {
                        if (oldMod < 0) {
                            writer.addDocument(buildDoc(f, isGutenberg));
                            stats.newDocs++;
                        }
                    }
                    case "changed" -> {
                        if (oldMod >= 0 && f.lastModified() > oldMod) {
                            writer.updateDocument(
                                new Term("filepath", path),
                                buildDoc(f, isGutenberg)
                            );
                            stats.updatedDocs++;
                        }
                    }
                    case "missing" -> {
                        //handle deletions below
                    }
                }
            }
        }

        //delete docs for files no longer present
        if ("missing".equals(mode)) {
            for (String oldPath : indexed.keySet()) {
                if (!seen.contains(oldPath)) {
                    writer.deleteDocuments(new Term("filepath", oldPath));
                    stats.deletedDocs++;
                }
            }
        }

        writer.commit();  //commit all thread operations
        writer.close();   //close writer
        return stats;     //return summary
    }

    /**
     * Reads text file and constructs a Lucene Document with content and metadata
     *
     * @param file, input text File to index
     * @param isGutenberg, if true, only content between Gutenberg markers
     * @return, a Lucene Document
     * @throws IOException on file read errors
     */
    private static Document buildDoc(File file, boolean isGutenberg) throws IOException {
        Document doc = new Document();
        StringBuilder content = new StringBuilder(); //builder for file text
        String author = "", title = "";            //metadata placeholders
        boolean inBlock = !isGutenberg;                //control capturing
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (isGutenberg) {
                    if (ln.contains("START OF THE PROJECT GUTENBERG EBOOK")) {
                        inBlock = true; //start capture
                        continue;
                    }
                    if (ln.contains("END OF THE PROJECT GUTENBERG EBOOK")) {
                        break;         //end capture
                    }
                }
                //extract author and title if present
                if (author.isEmpty() && ln.contains("Author:")) {
                    author = ln.substring(ln.indexOf("Author:") + 7).trim();
                }
                if (title.isEmpty() && ln.contains("Title:")) {
                    title = ln.substring(ln.indexOf("Title:") + 6).trim();
                }
                if (inBlock) content.append(ln).append('\n'); //capture lines
            }
        }
        String full = content.toString();
        //add to document
        doc.add(new TextField("content", full, TextField.Store.YES));
        doc.add(new TextField("stemcontent", Indexer.stem(full), TextField.Store.YES));
        doc.add(new TextField("stopcontent", Indexer.removeStops(full), TextField.Store.YES));
        doc.add(new TextField("author", author.isEmpty() ? "Unknown" : author, StringField.Store.YES));
        doc.add(new TextField("title", title.isEmpty() ? file.getName() : title, StringField.Store.YES));
        doc.add(new TextField("filename", file.getName(), StringField.Store.YES));
        doc.add(new TextField("filepath", file.getAbsolutePath(), StringField.Store.YES));
        doc.add(new TextField("modified", Long.toString(file.lastModified()), StringField.Store.YES));
        return doc;
    }
}