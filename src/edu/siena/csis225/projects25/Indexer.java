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

    //stop‑word list
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList("the","a","an","is","of","and","or","in","to"));

    /**
     * class used to track statistics relevant to indexing.
     */
    static class Stats {
        int newDocs;
        int updatedDocs;
        int deletedDocs;
        Stats() { newDocs = 0; updatedDocs = 0; deletedDocs = 0; }
    }

    /**
     * runs indexer and reports statistics.
     *
     * @param inFolder, input folder storing .txt files
     * @param outIndexFolder, folder to write Lucene indices
     * @param indexingMode, one of "new", "changed", "missing", or null for all
     * @param isGutenberg, controls whether to extract only Gutenberg block
     */
    public static void run(String inFolder, String outIndexFolder, String indexingMode, boolean isGutenberg) {
        if (inFolder==null||outIndexFolder==null) {
            System.out.println("to index, enter: java -jar indexer.jar <inputFolder> <outputFolder>");
            return;
        }
        String modeKey = (indexingMode==null?"all":indexingMode.toLowerCase());
        if (!Arrays.asList("all","new","changed","missing").contains(modeKey)) {
            System.out.println("Invalid mode: "+indexingMode);
            return;
        }
        try {
            long indexTimeBegin = System.currentTimeMillis();
            Stats stats = indexFiles(inFolder,outIndexFolder,modeKey,isGutenberg);
            long indexTimeEnd = System.currentTimeMillis();
            System.out.println("Indexing done.");
            System.out.println("Added:   "+stats.newDocs);
            System.out.println("Updated: "+stats.updatedDocs);
            System.out.println("Removed: "+stats.deletedDocs);
            System.out.println("Duration: "+(indexTimeEnd-indexTimeBegin)+" ms");
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses through the files inside input folder and adds, updates, or deletes
     * documents in Lucene index depending on specified mode.
     *
     * @param inFolder, path to the directory of .txt files
     * @param outIndexFolder, path to folder that stores output of Lucene indices
     * @param mode, "all","new","changed",or "missing"
     * @param isGutenberg, true if indexing should be limited to Gutenberg block
     * @return, a Stats object reporting the total count of added, updated, and deleted docs
     * @throws, IOException if index or file input or output fails
     */
    private static Stats indexFiles(String inFolder, String outIndexFolder, String mode, boolean isGutenberg) throws IOException {
        Directory dir = FSDirectory.open(Paths.get(outIndexFolder));
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        cfg.setSimilarity(new ClassicSimilarity());
        IndexWriter writer = new IndexWriter(dir,cfg);
        Stats stats = new Stats();

        Map<String,Long> indexed = new HashMap<>();
        if (DirectoryReader.indexExists(dir)) {
            DirectoryReader rdr = DirectoryReader.open(dir);
            for (LeafReaderContext ctx : rdr.leaves()) {
                LeafReader lr = ctx.reader();
                Bits live = lr.getLiveDocs();
                for (int i=0;i<lr.maxDoc();i++) {
                    if (live!=null&&!live.get(i)) continue;
                    Document d = lr.document(i);
                    String path = d.get("filepath"), modStr = d.get("modified");
                    if (path!=null&&modStr!=null) indexed.put(path,Long.valueOf(modStr));
                }
            }
            rdr.close();
        }

        File folder = new File(inFolder);
        File[] files = folder.listFiles((d,n)->n.toLowerCase().endsWith(".txt"));
        Set<String> visited = new HashSet<>();
        if (files!=null) {
            for (File f:files) {
                String fp = f.getAbsolutePath();
                visited.add(fp);
                long oldMod = indexed.getOrDefault(fp,-1L);
                switch(mode) {
                    case "all" -> {
                        if (oldMod<0) {
                            writer.addDocument(buildDoc(f,isGutenberg));
                            stats.newDocs++;
                        } else if (f.lastModified()>oldMod) {
                            writer.updateDocument(new Term("filepath",fp),buildDoc(f,isGutenberg));
                            stats.updatedDocs++;
                        }
                    }
                    case "new" -> {
                        if (oldMod<0) {
                            writer.addDocument(buildDoc(f,isGutenberg));
                            stats.newDocs++;
                        }
                    }
                    case "changed" -> {
                        if (oldMod>=0&&f.lastModified()>oldMod) {
                            writer.updateDocument(new Term("filepath",fp),buildDoc(f,isGutenberg));
                            stats.updatedDocs++;
                        }
                    }
                    case "missing" -> {
                        //deletion handled later
                    }
                }
            }
        }

        if ("missing".equals(mode)) {
            for (String path:indexed.keySet()) {
                if (!visited.contains(path)) {
                    writer.deleteDocuments(new Term("filepath",path));
                    stats.deletedDocs++;
                }
            }
        }

        writer.commit();
        writer.close();
        return stats;
    }

    /**
     * Reads a file, and extracts text and builds Lucene Document with required fields.
     * @param file, the text file to index
     * @param isGutenberg, true to restrict content extraction to Gutenberg block
     * @return, a Document, the constructed Lucene Document
     * @throws, IOException if reading the file fails
     */
    private static Document buildDoc(File file, boolean isGutenberg) throws IOException {
        Document doc = new Document();
        StringBuilder text = new StringBuilder();
        String auth = "", ttl = "";
        boolean inBlock = !isGutenberg;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String ln;
            while ((ln=br.readLine())!=null) {
                if (isGutenberg) {
                    if (ln.contains("START OF THE PROJECT GUTENBERG EBOOK")) {
                        inBlock=true;
                        continue;
                    }
                    if (ln.contains("END OF THE PROJECT GUTENBERG EBOOK")) {
                        inBlock= false;
                        break;
                    }
                }
                if (auth.isEmpty()&&ln.contains("Author:")) {
                    auth = ln.substring(ln.indexOf("Author:")+7).trim();
                }
                if (ttl.isEmpty()&&ln.contains("Title:")) {
                    ttl = ln.substring(ln.indexOf("Title:")+6).trim();
                }
                if (inBlock) text.append(ln).append("\n");
            }
        }
        String full = text.toString().trim();
        String stemmed = stem(full);
        String filtered = removeStops(full);
        doc.add(new TextField("content",full,TextField.Store.YES));
        doc.add(new TextField("stemcontent",stemmed,TextField.Store.YES));
        doc.add(new TextField("stopcontent",filtered,TextField.Store.YES));
        doc.add(new StringField("author",auth.isEmpty()?"Unknown":auth,StringField.Store.YES));
        doc.add(new StringField("title",ttl.isEmpty()?file.getName():ttl,StringField.Store.YES));
        doc.add(new StringField("filename",file.getName(),StringField.Store.YES));
        doc.add(new StringField("filepath",file.getAbsolutePath(),StringField.Store.YES));
        doc.add(new StringField("modified",Long.toString(file.lastModified()),StringField.Store.YES));
        return doc;
    }

    /**
     * Applies Porter stemming to inputted text.
     *
     * @param, input raw text for stemming
     * @return, string containing stemmed tokens seperated by space
     * @throws, IOException if stemming fails during processing
     */
    private static String stem(String input) throws IOException {
        StandardTokenizer tokenizer = new StandardTokenizer();
        tokenizer.setReader(new StringReader(input));
        TokenStream ts = new LowerCaseFilter(tokenizer);
        ts = new PorterStemFilter(ts);
        ts.reset();
        StringBuilder out = new StringBuilder();
        CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);
        while (ts.incrementToken()) out.append(attr.toString()).append(" ");
        ts.end(); ts.close();
        return out.toString().trim();
    }

    /**
     * Filters out stop words from text
     *
     * @param input, the raw text to be filtered
     * @return, a string containing text with stop words removed, separated by a space
     */
    private static String removeStops(String input) {
        StringBuilder sb = new StringBuilder();
        for (String t: input.split("\\W+"))
            if (!t.isEmpty() && !STOP_WORDS.contains(t.toLowerCase()))
                sb.append(t).append(" ");
        return sb.toString().trim();
    }
}
