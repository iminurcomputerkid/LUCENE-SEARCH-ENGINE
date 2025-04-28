package edu.siena.csis225.projects25;

import java.io.File;
import javax.swing.SwingUtilities;
import java.nio.file.Paths;


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
     * main method, parses cli arguments to detect -text flag for cli and gui by default, detects -cran flag to run cranfield or guteberg without it, and 
     * -parallel flag to run parallel indexer and normal indexer without it
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
        boolean isParallel = false;
        String cranQAfile = null;

        // check flags: -text for CLI, -cran for Cranfield, -parallel for parallel indexing (also under -cran case sets
        //cranQAfile to QA file passed in as argument)
            for (int i = 0; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "-text":
                    launchGUI = false;
                    break;
                case "-parallel":
                    isParallel = true;
                    break;
                case "-cran":
                    isGutenbergFormat = false;
                    break;
                default:
                    // ignore unknown flags or handle other options here
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
                File indexDir = new File(idxFolder);
                if (indexDir.exists()) {
                    for (File f : indexDir.listFiles()) {
                        f.delete();
                    }
              }
            } catch (Exception ex) {
                System.err.println("error separating cranfield: " + ex.getMessage());
                return;
            }
        }
        //if parallel selected, run parallel indexer, if not, run normal indexer
         if (isParallel) {
                int numThreads  = Runtime.getRuntime().availableProcessors() + 1;
                int maxQueueSize  = numThreads * 4;
            PIndexer.run(folderPath,idxFolder,idxMode,isGutenbergFormat,numThreads,maxQueueSize);
        } else {
             //index normally
            Indexer.run(folderPath, idxFolder, idxMode, isGutenbergFormat);
        }
         //for QA bonus, runs cranfieldQAEvaluator if cranQAfile not null and not gui 
         if (!launchGUI && cranQAfile != null) {
            // look for ground truth right next to the QA file
            java.nio.file.Path qaPath    = java.nio.file.Paths.get(cranQAfile);
            java.nio.file.Path parentDir = qaPath.getParent();
            String truthPath = (parentDir != null
                                 ? parentDir.resolve("cranfieldGroundTruth.txt").toString()
                                 : "cranfieldGroundTruth.txt");

            try {
                CranfieldQAEvaluator.run(cranQAfile, idxFolder, truthPath);
            } catch (Exception e) {
                System.err.println("Error during Cranfield QA evaluation: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
            return;
        }
        // launch search UI
        if (launchGUI) {
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
