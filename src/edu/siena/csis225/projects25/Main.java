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
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        //default data directory
        String folderPath = "./data";
        //default index directory
        String idxFolder = "./IndexedData";
        //indexing mode key, new, changed, missing or all (by def)
        String idxMode = null;
        //flag to launch GUI, if false launch CLI
        boolean launchGUI = true;
        //flag to show explanation in results
        boolean showExplanations = false;
        //max number of output results
        int maxOutResults = 5;
        //format flag to specify Gutenberg, false means Cranfield
        boolean isGutenbergFormat = true;
        //flag for parallel indexing
        boolean isParallel = false;
        //path to Cranfield QA file (optional)
        String cranQAfile = null;

        //parse CLI flags
        for (int i = 0; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "-text":
                    //switch to CLI mode if -text passed through
                    launchGUI = false;
                    break;
                case "-parallel":
                    //enable parallel indexing if -parallel passed through
                    isParallel = true;
                    break;
                case "-cran":
                    //switch to Cranfield data format if -cran passed through
                    isGutenbergFormat = false;
                    //next arg would be QA file path
                    if (i + 1 < args.length) {
                        cranQAfile = args[++i];
                    }
                    break;
                default:
                    // ignore other arguments passed
            }
        }

        //ensure index directory exists
        File idxDirObj = new File(idxFolder);
        if (!idxDirObj.exists()) {
            idxDirObj.mkdirs();
        }

        //print config summary
        System.out.println("Data Folder: " + folderPath);
        System.out.println("Index Folder: " + idxFolder);
        System.out.println("Index Mode: " + (idxMode == null ? "all" : idxMode));
        System.out.println(isGutenbergFormat 
            ? "Gutenberg data selected." 
            : "Cranfield data selected.");
        System.out.println("loading.... please wait");

        //if Cranfield format selected, run cleaner
        if (!isGutenbergFormat) {
            System.out.println("Running CranfieldMagic...");
            try {
                CranfieldMagic.magicClean();
                //switch data folder to CranfieldSeperated
                folderPath = "./cranfieldSeparated";
                //clear existing index files
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

        //choose indexing option (normal vs parallel)
        if (isParallel) {
            //parallel indexing with thread pool
            int numThreads = Runtime.getRuntime().availableProcessors() + 1;
            int maxQueueSize = numThreads * 4;
            PIndexer.run(folderPath, idxFolder, idxMode, isGutenbergFormat, numThreads, maxQueueSize);
        } else {
            //normal single-threaded indexing
            Indexer.run(folderPath, idxFolder, idxMode, isGutenbergFormat);
        }

        //if CLI and QA file provided, run CranfieldQAevaluation
        if (!launchGUI && cranQAfile != null) {
            //derive ground truth file path from QA file location
            java.nio.file.Path qaPath = java.nio.file.Paths.get(cranQAfile);
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
            return; //exit after eval
        }

        //launch search interface, gui if launchGui is true, otherwise, run CLI
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
