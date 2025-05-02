package edu.siena.csis225.projects25;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import java.nio.file.Paths;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.*;

/**
 * Indexes a directory of text files into Lucene indices and ouptuts indices to a file named IndexedData.
 * @version 4/5/2025
 * @author Julien, Riley, Zi’Aire
 */
public class Indexer {

    //list of common stop-words
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the","a","an","is","of","and","or","in","to"
    ));

    /**
     * Tracks count of docs added, updated, and deleted.
     */
    static class Stats {
        int newDocs;      
        int updatedDocs; 
        int deletedDocs;  
        Stats() { 
            newDocs = 0; updatedDocs = 0; deletedDocs = 0; 
        }
    }

    /**
     * Runs the indexer and prints statistics
     * @param inFolder, input folder with .txt files
     * @param outIndexFolder, output directory for Lucene index
     * @param indexingMode, "new", "changed", "missing", or null for all
     * @param isGutenberg, if true, restrict content to Gutenberg block
     */
    public static void run(String inFolder, String outIndexFolder, String indexingMode, boolean isGutenberg) {
        // validate arguments
        if (inFolder == null || outIndexFolder == null) {
            System.out.println("to index, enter: java -jar indexer.jar <inputFolder> <outputFolder>");
            return;
        }
        //determine mode key (all by default)
        String modeKey = (indexingMode == null ? "all" : indexingMode.toLowerCase());
        if (!Arrays.asList("all","new","changed","missing").contains(modeKey)) {
            System.out.println("Invalid mode: " + indexingMode);
            return;
        }
        try {
            //record start time
            long indexTimeBegin = System.currentTimeMillis();
            //perform indexing
            Stats stats = indexFiles(inFolder, outIndexFolder, modeKey, isGutenberg);
            //end time
            long indexTimeEnd = System.currentTimeMillis();
            //output summary stats
            System.out.println("Indexing done.");
            System.out.println("Added:   " + stats.newDocs);
            System.out.println("Updated: " + stats.updatedDocs);
            System.out.println("Removed: " + stats.deletedDocs);
            try (DirectoryReader reader = DirectoryReader.open(
                    FSDirectory.open(Paths.get(outIndexFolder)))) {
                System.out.println("Total Docs:  " + reader.numDocs());
            }
            System.out.println("Duration: " + (indexTimeEnd - indexTimeBegin) + " ms");
        } catch (IOException e) {
            e.printStackTrace(); //print stack trace if error occurs
        }
    }

    /**
     * Indexes, updates, or deletes documents based on mode
     * @param inFolder, path to .txt files
     * @param outIndexFolder, path to Lucene index directory
     * @param mode, indexing mode: "all","new","changed","missing"
     * @param isGutenberg, if true, limit to Gutenberg block
     * @return Stats object with operation counts
     * @throws IOException I/O errors
     */
    public static Stats indexFiles(String inFolder, String outIndexFolder, String mode, boolean isGutenberg) throws IOException {
        //open or create index directory
        Directory dir = FSDirectory.open(Paths.get(outIndexFolder));
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        cfg.setSimilarity(new ClassicSimilarity()); //use TF-IDF similarity
        IndexWriter writer = new IndexWriter(dir, cfg);
        Stats stats = new Stats(); //initialize stats tracker

        //load existing index metadata: filepath'a last modified timestamp
        Map<String, Long> indexed = new HashMap<>();
        if (DirectoryReader.indexExists(dir)) {
            DirectoryReader rdr = DirectoryReader.open(dir);
            for (LeafReaderContext ctx : rdr.leaves()) {
                LeafReader lr = ctx.reader();
                Bits live = lr.getLiveDocs();
                for (int i = 0; i < lr.maxDoc(); i++) {
                    //skip deleted docs
                    if (live != null && !live.get(i)) continue;
                    Document d = lr.document(i);
                    String path = d.get("filepath");
                    String modStr = d.get("modified");
                    if (path != null && modStr != null) {
                        indexed.put(path, Long.valueOf(modStr));
                    }
                }
            }
            rdr.close();
        }

        //list .txt files in input folder
        File folder = new File(inFolder);
        File[] files = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".txt"));
        Set<String> visited = new HashSet<>();
        if (files != null) {
            for (File f : files) {
                String fp = f.getAbsolutePath();
                visited.add(fp); // mark as seen
                long oldMod = indexed.getOrDefault(fp, -1L);
                //switch based on index mode
                switch (mode) {
                    case "all" -> {
                        if (oldMod < 0) {
                            //new file: add to index
                            writer.addDocument(buildDoc(f, isGutenberg));
                            stats.newDocs++;
                        } else if (f.lastModified() > oldMod) {
                            //modified file: update existing doc
                            writer.updateDocument(new Term("filepath", fp), buildDoc(f, isGutenberg));
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
                            writer.updateDocument(new Term("filepath", fp), buildDoc(f, isGutenberg));
                            stats.updatedDocs++;
                        }
                    }
                    case "missing" -> {
                        //deletion handled below
                    }
                }
            }
        }

        //if mode is "missing", delete docs for files no longer present
        if ("missing".equals(mode)) {
            for (String path : indexed.keySet()) {
                if (!visited.contains(path)) {
                    writer.deleteDocuments(new Term("filepath", path));
                    stats.deletedDocs++;
                }
            }
        }

        writer.commit(); //commit all changes
        writer.close();  //close writer
        return stats;
    }

    /**
     * Builds a Lucene Document from a text file, extracting metadata and content fields.
     * @param file, the text file to index
     * @param isGutenberg, restrict content if Gutenberg mode
     * @return constructed Lucene Document
     * @throws IOException during read errors
     */
    private static Document buildDoc(File file, boolean isGutenberg) throws IOException {
        Document doc = new Document();
        StringBuilder text = new StringBuilder(); // collect file content
        String auth = ""; //author 
        String ttl = "";  //title 
        //flag to indicate when to capture lines
        boolean inBlock = !isGutenberg;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String ln;
            while ((ln = br.readLine()) != null) {
                if (isGutenberg) {
                    if (ln.contains("START OF THE PROJECT GUTENBERG EBOOK")) {
                        inBlock = true; //start capture
                        continue;
                    }
                    if (ln.contains("END OF THE PROJECT GUTENBERG EBOOK")) {
                        break; //stop capture
                    }
                }
                //extract author if not already set
                if (auth.isEmpty() && ln.contains("Author:")) {
                    auth = ln.substring(ln.indexOf("Author:") + 7).trim();
                }
                //extract title if not already set
                if (ttl.isEmpty() && ln.contains("Title:")) {
                    ttl = ln.substring(ln.indexOf("Title:") + 6).trim();
                }
                //append to text if in capture block
                if (inBlock) text.append(ln).append("\n");
            }
        }
        //prepare field values
        String full = text.toString().trim();
        String stemmed = stem(full);      //apply stemming
        String filtered = removeStops(full); //apply stop-word removal
        //add fields to document
        doc.add(new TextField("content",full,TextField.Store.YES));
        doc.add(new TextField("stemcontent",stemmed,TextField.Store.YES));
        doc.add(new TextField("stopcontent",filtered,TextField.Store.YES));
        doc.add(new TextField("author",auth.isEmpty()?"Unknown":auth,StringField.Store.YES));
        doc.add(new TextField("title",ttl.isEmpty()?file.getName():ttl,StringField.Store.YES));
        doc.add(new TextField("filename",file.getName(),StringField.Store.YES));
        doc.add(new TextField("filepath",file.getAbsolutePath(),StringField.Store.YES));
        doc.add(new TextField("modified",Long.toString(file.lastModified()),StringField.Store.YES));
        //return document
        return doc;
    }

    /**
     * Applies Porter stemming to input text
     * @param input, raw text to stem
     * @return space-separated stemmed tokens
     * @throws IOException on token stream errors
     */
    public static String stem(String input) throws IOException {
        StandardTokenizer tokenizer = new StandardTokenizer();
        tokenizer.setReader(new StringReader(input));
        TokenStream ts = new LowerCaseFilter(tokenizer);  //convert to lowercase
        ts = new PorterStemFilter(ts);                    //apply stemming
        ts.reset();
        StringBuilder out = new StringBuilder();
        CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);
        while (ts.incrementToken()) {
            out.append(attr.toString()).append(" ");
        }
        ts.end(); ts.close();
        return out.toString().trim();
    }

    /**
     * Removes stop words from input text.
     * @param input, raw text to filter
     * @return filtered text without stop words
     */
    public static String removeStops(String input) {
        StringBuilder sb = new StringBuilder();
        for (String t : input.split("\\W+")) {
            if (!t.isEmpty() && !STOP_WORDS.contains(t.toLowerCase())) {
                sb.append(t).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
