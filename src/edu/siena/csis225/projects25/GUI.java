package edu.siena.csis225.projects25;

import edu.siena.csis225.projects25.Indexer.Stats;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import java.nio.file.Paths;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.DirectoryReader;

/**
 * Simple Swing‐based search GUI
 *
 * @version 4/12/2025
 * @author Julien, Riley, Zi’Aire
 */
public class GUI extends JFrame {

    //input data folder 
    private final String inFolder;
    //index folder path
    private final String outIndexFolder;
    //indexing mode key
    private final String modeKey;
    //flag for Gutenberg-specific indexing
    private final boolean isGutenberg;

    //Swing components
    private JTextField searchField;
    private JTextArea displayArea;
    private JButton goButton;
    private searchHandler guiSearchMgr;
    private JSlider maxResSlider;
    private JLabel sliderLabel;
    private int maxRes;
    private JButton statsButton;

    private JComboBox<String> fieldCombo;
    private JCheckBox phraseCheck;
    private JCheckBox explainCheck;

    /**
     * Constructs the GUI and initializes the search handler
     *
     * @param idxPath    path to Lucene index directory
     * @param showExplain initial state of the Explain checkbox
     * @param maxRes      maximum number of search hits to return
     * @throws IOException if the Lucene index cannot be opened
     */
    public GUI(String idxPath, boolean showExplain, int maxRes) throws IOException {
        super("Search GUI"); //window title
        this.maxRes = maxRes; // store slider default
        this.outIndexFolder = idxPath;
        this.inFolder = "./data"; // default data folder
        this.modeKey = "default";
        this.isGutenberg = false;

        try {
            // initialize search handler
            guiSearchMgr = new searchHandler(idxPath, showExplain, maxRes);
        } catch (IOException e) {
            //show error dialog and exit if initialization fails
            JOptionPane.showMessageDialog(
                this,
                "Error initializing search: " + e.getMessage(),
                "Init Error",
                JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }
        initialize();  //build and lay out UI components
    }

    /**
     * Lays out Swing components and links up event handlers
     */
    private void initialize() {
        //Build top row- field, search and stats buttons
        JPanel searchRow = new JPanel(new BorderLayout(5, 0));
        searchRow.add(new JLabel("Enter query:"), BorderLayout.WEST);
        searchField = new JTextField(30);
        searchRow.add(searchField, BorderLayout.CENTER);
        goButton = new JButton("Search");
        statsButton = new JButton("Stats");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttons.add(goButton);
        buttons.add(statsButton);
        searchRow.add(buttons, BorderLayout.EAST);

        // Build options row- field dropdown, Phrase only button, Explain button
        String[] fields = {
            "content", "stemcontent", "stopcontent",
            "author", "title", "filepath", "filename", "modified"
        };
        fieldCombo = new JComboBox<>(fields);
        phraseCheck = new JCheckBox("Phrase only");
        explainCheck = new JCheckBox("Explain", guiSearchMgr.isShowExplain());
        JPanel optionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        optionsRow.add(new JLabel("Field:"));
        optionsRow.add(fieldCombo);
        optionsRow.add(phraseCheck);
        optionsRow.add(explainCheck);

        //build slider for max results
        sliderLabel = new JLabel("Max Results: " + maxRes);
        maxResSlider = new JSlider(JSlider.HORIZONTAL, 1, 1000, maxRes);
        maxResSlider.setMajorTickSpacing(20);
        maxResSlider.setMinorTickSpacing(1);
        maxResSlider.setPaintTicks(true);
        maxResSlider.setPaintLabels(false);
        JPanel sliderRow = new JPanel(new BorderLayout(5, 0));
        sliderRow.add(sliderLabel, BorderLayout.WEST);
        sliderRow.add(maxResSlider, BorderLayout.CENTER);

        //Assemble northern panel (includes searchRow, optionsRow, sliderRow)
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.add(searchRow);
        northPanel.add(optionsRow);
        northPanel.add(sliderRow);
        add(northPanel, BorderLayout.NORTH);

        //display area for results
        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(
            displayArea,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        add(sp, BorderLayout.CENTER);

        //Event: Stats button pressed, run and show indexing stats dialog
        statsButton.addActionListener(e -> runAndShowIndexStats());

        //Event: Slider moved, update label and set max results in search handler
        maxResSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int val = maxResSlider.getValue();
                sliderLabel.setText("Max Results: " + val);
                GUI.this.maxRes = val;
                guiSearchMgr.setMaxResults(val);
            }
        });

        //ActionListener for executing search
        ActionListener doSearch = ae -> executeSearch();
        goButton.addActionListener(doSearch);
        searchField.addActionListener(doSearch);

        //Field combo selected, change search field and re-run automatically 
        fieldCombo.addActionListener(e -> {
            guiSearchMgr.setSearchField((String) fieldCombo.getSelectedItem());
            executeSearch();
        });
        //Phrase only checkbox selected, toggle phrase mode and re-run
        phraseCheck.addActionListener(e -> {
            guiSearchMgr.setPhraseOnly(phraseCheck.isSelected());
            executeSearch();
        });
        //Explain checkbox selected, toggle explain mode and re-run automatically
        explainCheck.addActionListener(e -> {
            guiSearchMgr.setShowExplain(explainCheck.isSelected());
            executeSearch();
        });

        //Window settings
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(750, 550);
        setLocationRelativeTo(null);
    }

    /**
     * Reads query from text field, executes search, and updates display area.
     * Shows a warning dialog if the query is empty.
     */
    private void executeSearch() {
        String qry = searchField.getText().trim();
        if (qry.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a query.",
                "No Query",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        try {
            String result = guiSearchMgr.search(qry);
            displayArea.setText(
                (result == null || result.isEmpty())
                ? "No results found." : result
            );
            displayArea.setCaretPosition(0);  //scroll to top
        } catch (IOException | ParseException | InvalidTokenOffsetsException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                this,
                "Error during search: " + ex.getMessage(),
                "Search Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Runs indexing (fron Indexer.java) and displays indexing statistics.
     */
    private void runAndShowIndexStats() {
        try {
            long start = System.currentTimeMillis();
            Stats stats = Indexer.indexFiles(inFolder, outIndexFolder, modeKey, isGutenberg);
            long end = System.currentTimeMillis();

            int totalDocs;
            try (DirectoryReader reader = DirectoryReader.open(
                    FSDirectory.open(Paths.get(outIndexFolder))
            )) {
                totalDocs = reader.numDocs();
            }

            //summary message
            String msg = String.format(
                "Indexing done in %,d ms%n" +
                "Added:   %d%n" +
                "Updated: %d%n" +
                "Removed: %d%n" +
                "Total Docs: %d",
                (end - start),
                stats.newDocs,
                stats.updatedDocs,
                stats.deletedDocs,
                totalDocs
            );

            JOptionPane.showMessageDialog(
                this,
                msg,
                "Indexing Statistics",
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error during indexing:\n" + ex.getMessage(),
                "Indexing Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Launches the GUI
     */
    public static void main(String[] args) {
        String idxPath = "./IndexedData";
        boolean showExplain = false;  //start with Explain unchecked
        int maxRes = 5;               //max results default
        SwingUtilities.invokeLater(() -> {
            try {
                new GUI(idxPath, showExplain, maxRes).setVisible(true);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                    null,
                    "Failed to launch GUI: " + e.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
