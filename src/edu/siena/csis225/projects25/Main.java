package edu.siena.csis225.projects25;

import java.io.File;
import java.util.Scanner;
import javax.swing.SwingUtilities;

/** 
 * Depending on the presence of the “-text” flag in CLI argument, this class will either launch
 * the command‐line or GUI search interface. also prompts
 * user to choose between Gutenberg or Cranfield data for searching
 
 * @version 4/11/2025
 * @author Julien, Riley, Zi’Aire
 */
public class Main {
    /**
     * main method, parses cli arguments to detect -text flag for cli and gui by default, prompts for data to be searched (cranfield or gutenberg)
     * @param args 
     */
    public static void main(String[] args) {
        String folderPath = "./data";         
        String idxFolder = "./IndexedData";     
        String idxMode = null;                 
        boolean launchGUI = true;             
        boolean showExplanations = false;       
        int maxOutResults = 5;                  

        //if -text passed through, set gui to false to launch cli
        for (String arg : args) {
            if ("-text".equalsIgnoreCase(arg)) {
                launchGUI = false;
            }
        }
        
        // ensure the indexedData folder exists
        File idxDirObj = new File(idxFolder);
        if (!idxDirObj.exists()) {
            idxDirObj.mkdirs();
        }

        //config
        System.out.println("Data Folder: " + folderPath);
        System.out.println("Index Folder: " + idxFolder);
        System.out.println("Index Mode: " + (idxMode == null ? "all" : idxMode));

        // prompts user to enter in data format
        Scanner kbReader = new Scanner(System.in);
        System.out.println("Select data format: Enter 'G' for gutenberg or 'C' for cranfield:");
        String opt = kbReader.nextLine().trim();

        boolean isGutenbergFormat;
        if (opt.equalsIgnoreCase("C")) {
            System.out.println("Cranfield format selected. Running CranfieldMagic..");
            try {
                CranfieldMagic.magicClean();
                //after cleaning, set folderpath to cleaned cranfield data
                folderPath = "./cranfieldCleaned";
            } catch (Exception ex) {
                System.err.println("error seperating cranfield: " + ex.getMessage());
                kbReader.close();
                return;
            }
            isGutenbergFormat = false;
        } else {
            isGutenbergFormat = true;
        }

        System.out.println("____Final settings____");
        System.out.println("Data Folder: " + folderPath);
        System.out.println("Index Folder: " + idxFolder);
        System.out.println("Index Mode: " + (idxMode == null ? "all" : idxMode));
        System.out.println(isGutenbergFormat ? "Gutenberg format active." : "Cranfield format active.");

        // index
        Indexer.run(folderPath, idxFolder, idxMode, isGutenbergFormat);

        if (launchGUI) {
            // launch Swing-based GUI
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
        kbReader.close();
    }
}
