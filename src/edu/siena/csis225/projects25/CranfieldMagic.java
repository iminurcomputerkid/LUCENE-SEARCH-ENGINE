package edu.siena.csis225.projects25;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.Normalizer;

/**
 * Splits the combined Cranfield data into individual text files.
 * Each individual text file is ordered by a document number and contains
 * a title and author along with contents.
 * @version 4/3/2025
 * @author Julien, Riley, Zi’Aire
 */
public class CranfieldMagic {

    /**
     * Cleans the Cranfield data and reports status.
     */
    public static void main(String[] args) {
        try {
            //performs spliting and cleaning aka. Magic :D
            magicClean();
            System.out.println("Cranfield data has been cleaned.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads the original file and breaks it into separate documents, and writes to a new 
     * folder cranfieldCleaned. It recognizes markers for document no. [.I], title [.T], author [.A], and content [.W],
     * it reads contents for a specific file until another .I is detected, which marks the beginning of the next file
     * @throws IOException if error occurs during reading or writing process
     */
    public static void magicClean() throws IOException {
        //defines source file for cleaning
        File src = new File("./cranfield/cranfieldData.txt");
        //if source isn't found or doesn't exist, print error
        if (!src.exists()) {
            System.err.println("cranfieldData.txt not found.");
            return;
        }
        //defines output directory
        File outputDir = new File("./cranfieldSeparated");
        //if output file doesn't exist, make one
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        } else {
            //clears old files
            for (File f : outputDir.listFiles()) {
                f.delete();
            }
        }
        //reads and stores file as a string
        String whole = Files.readString(src.toPath());
        //convert to plain ascii characters
        whole = asciiConverter(whole);
        
        //split on lines starting with .I
        String[] chunks = whole.split("(?m)^\\.I\\s");
        
        //skip empty chunks
        for (String chunk : chunks) {
            if (chunk.isBlank()) continue;
            
            //split into lines
            String[] lines = chunk.split("\\R");
            
            //first line is doc number
            String docNum = lines[0].trim();
            
            //title line
            StringBuilder titleBuf   = new StringBuilder();
            //varaible to store author line
            String author            = "";
            //content lines
            StringBuilder contentBuf = new StringBuilder();
            
            //title and content flags for processing the rest
            boolean title            = false;
            boolean content          = false;
            
            //process reamining lines
            for (int i = 1; i < lines.length; i++) {
                String ln = lines[i];
                //title marker, set title to true and content to false (cuz its not content)
                if (ln.startsWith(".T")) {
                    title   = true;
                    content = false;
                    continue;
                }
                //Author marker
                if (ln.startsWith(".A")) {
                    title   = false;
                    content = false;
                    
                    //next line is author
                    if (i + 1 < lines.length) {
                        author = lines[++i].trim();
                    }
                    continue;
                }
                //ignore .B (bibliographic), continue if detected
                if (ln.startsWith(".B")) {
                    i++;
                    continue;
                }
                //content marker, set content to true
                if (ln.startsWith(".W")) {
                    title   = false;
                    content = true;
                    continue;
                }
                //add title text
                if (title) {
                    titleBuf.append(ln).append(" ");
                }
                //add content text
                if (content) {
                    contentBuf.append(ln).append("\n");
                }
            }
            //write document out with all contents
            outputDoc(outputDir, docNum, titleBuf.toString(), author, cleanContent(contentBuf.toString()));
        }
    }

    /**
     * Replaces extended-ASCII characters (128–255) with ASCII equivalents,
     * @param text, raw text
     * @return normalized ASCII-only text
     */
    private static String asciiConverter(String text) {
        //replacements for common fractions
        text = text.replace("\u00BC", "1/4")
                   .replace("\u00BD", "1/2")
                   .replace("\u00BE", "3/4");
        //strip accents
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Joins lines that do not end with a period with the next line so sentences remain intact.
     * @param raw, raw text with line breaks
     * @return a cleaned string with intact sentences
     */
    private static String cleanContent(String raw) {
        String[] lines = raw.split("\n");
        StringBuilder cleaned = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String cur = lines[i].trim();
            if (cur.isEmpty()) continue; //skip if empty
            
            //if line doesn't end with ., merge with next line
            while (!cur.endsWith(".") && i + 1 < lines.length) {
                cur += " " + lines[++i].trim();
            }
            //append with newline if sentence ended
            if (cur.endsWith(".")) {
                cleaned.append(cur).append("\n");
            } else {
                cleaned.append(cur).append(" ");
            }
        }

        return cleaned.toString().trim();
    }

    /**
     * Writes document files with a specific number into the given folder. 
     * Files start with a title, then author, and finally content.
     * @param folder, the directory to write to
     * @param docNum, document number used to identify that entry
     * @param title, the document's title
     * @param author, the document's author
     * @param content, the cleaned content
     * @throws, IOException if writing fails
     */
    private static void outputDoc(File folder, String docNum, String title, String author, String content) throws IOException {
        //combine multiple spaces in title to single ones
        String fixedTitle = title.replaceAll("\\s+", " ").trim();
        
        //file named by doc num
        File outFile = new File(folder, docNum + ".txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
           //write title line, author line and content
            pw.println("Title: " + fixedTitle);
            pw.println("Author: " + author);
            pw.println(content);
        }
    }
}
