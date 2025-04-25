package edu.siena.csis225.projects25;

import java.io.File;
import javax.swing.SwingUtilities;

/** 
 * Depending on the presence of the “-text” flag in CLI argument, this class will either launch
 * the command‐line or GUI search interface. also prompts
 * user to choose between Gutenberg or Cranfield data for searching
 * 
 * @version 4/11/2025
 * @author Julien, Riley, Zi’Aire
 */
public class Main {
    /**
     * main method, parses cli arguments to detect -text flag for cli and gui by default, prompts for data to be searched (cranfield or gutenberg)
     * @param args 
     */
    public static void main(String[] args) {
        String folderPath       = "./data";         
        String idxFolder        = "./IndexedData";     
        String idxMode          = null;                 
        boolean launchGUI       = true;             
        boolean showExplanations= false;       
        int maxOutResults   = 5;                  
        boolean isGutenbergFormat = true;

        // check flags: -text for CLI, -cran for Cranfield
        for (String arg : args) {
            if ("-text".equalsIgnoreCase(arg)) {
                launchGUI = false;
            }
            else if ("-cran".equalsIgnoreCase(arg)) {
                isGutenbergFormat = false;
            }
        }

        // ensure the IndexedData folder exists
        File idxDirObj = new File(idxFolder);
        if (!idxDirObj.exists()) {
            idxDirObj.mkdirs();
        }

        // configuration
        System.out.println("Data Folder: " + folderPath);
        System.out.println("Index Folder: " + idxFolder);
        System.out.println("Index Mode: " + (idxMode == null ? "all" : idxMode));
        System.out.println(isGutenbergFormat 
            ? "Gutenberg data selected." 
            : "Cranfield data selected.");
        System.out.println("loading.... please wait");

        // if Cranfield requested, run cleaner first
        if (!isGutenbergFormat) {
            System.out.println("Running CranfieldMagic...");
            try {
                CranfieldMagic.magicClean();
                folderPath = "./cranfieldSeparated";
            } catch (Exception ex) {
                System.err.println("error separating cranfield: " + ex.getMessage());
                return;
            }
        }

        // index
        Indexer.run(folderPath, idxFolder, idxMode, isGutenbergFormat);

        // launch search UI
        if (launchGUI) {
            // launch GUI
            SwingUtilities.invokeLater(() -> {
                try {
                    new GUI(idxFolder, showExplanations, maxOutResults).setVisible(true);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        } else {
            try {
                searchHandler sh = new searchHandler(idxFolder, showExplanations, maxOutResults);
                sh.startCLI();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
