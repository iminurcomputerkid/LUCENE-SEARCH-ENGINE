package edu.siena.csis225.projects25;

/**
 * Parses command‑line arguments for configuring data, index paths, indexing mode,
 * Cranfield file location, and UI mode (GUI vs CLI).
 * 
 * @version 4/10/2025
 * @author Julien, Zi'Aire, Riley
 */
public class ArgParser {
    //default data directory
    private String dataDir = "./data";
    
    //default index directory
    private String idxDir = "./IndexedData";
    
    //indexing flag, new, changed or missing
    private String idxMode = null;   // "new", "changed", "missing"
    
    //default path to Cranfield
    private String cranFile = "./cranfield/cranfieldData.txt";
    
    //gui mode by default
    private boolean guiMode = true;   // GUI launched by default
    
    //cli mode by default
    private boolean cliMode = false;  // Enabled when -text is passed
    
    //whether to output lucene explanation or not
    private boolean explainFlag = false;


    /**
     * Constructs an ArgParser 
     *
     * @param inputs, command‑line arguments to interpret
     */
    public ArgParser(String[] inputs) {
        parse(inputs);
    }

    /**
     * sets the corresponding fields based on command line arguments
     *
     * @param inputs, array of command‑line tokens
     */
    private void parse(String[] inputs) {
        //iterate over each token 
        for (int i = 0; i < inputs.length; i++) {
            String token = inputs[i];
            if (token.equalsIgnoreCase("-data") && i + 1 < inputs.length) {
                //data dir is next element
                dataDir = inputs[++i];
            }
            else if (token.equalsIgnoreCase("-index") && i + 1 < inputs.length) {
                //index dir is next element
                idxDir = inputs[++i];
            }
            else if (token.equalsIgnoreCase("-mode") && i + 1 < inputs.length) {
                //index mode is next element
                idxMode = inputs[++i];
            }
            else if (token.equalsIgnoreCase("-cran") && i + 1 < inputs.length) {
               //next element is cran folder path  or gutenberg by default
                cranFile = inputs[++i];
            }
            else if (token.equalsIgnoreCase("-text")) {
               //sets gui to false and runs cli
                guiMode = false;
                cliMode = true;
            }
            else if (token.equalsIgnoreCase("-explain")) {
                //toggle score explanation
                explainFlag = true;
            }
        }
    }

    /**
     * @return the configured data directory path
     */
    public String getDataDir() {
        return dataDir;
    }

    /**
     * @return the configured index directory path
     */
    public String getIdxDir() {
        return idxDir;
    }

    /**
     * @return the chosen indexing mode ("new", "changed", "missing"), or null for all
     */
    public String getIdxMode() {
        return idxMode;
    }

    /**
     * @return the path to the Cranfield data file
     */
    public String getCranFile() {
        return cranFile;
    }

    /**
     * @return true if GUI mode is enabled
     */
    public boolean isGUI() {
        return guiMode;
    }

    /**
     * @return true if CLI mode is enabled
     */
    public boolean isCLI() {
        return cliMode;
    }

    /**
     * @return true if score explanation output has been requested
     */
    public boolean isExplainFlag() {
        return explainFlag;
    }

    /**
     *Demonstration of possible configurations for Data Dir, Index Dir, Mode, Cranfield File, GUiMode, CLI Mode and Explain
     * (Doesn't actually do anything)
     *
     * @param args, not used
     */
    public static void main(String[] args) {
        //all arguments and paths
        String[] params = {"-data","./data","-index","./IndexedData","-mode","new","-cran","./cranfield/cranfieldData.txt","-text","-explain"};
        ArgParser ap = new ArgParser(params);
        //echoes each setting back
        System.out.println("Data Dir: " + ap.getDataDir());
        System.out.println("Index Dir: " + ap.getIdxDir());
        System.out.println("Mode: " + ap.getIdxMode());
        System.out.println("Cranfield File: "+ ap.getCranFile());
        System.out.println("GUI Mode: " + ap.isGUI());
        System.out.println("CLI Mode: " + ap.isCLI());
        System.out.println("Explain: "  + ap.isExplainFlag());
    }
}
