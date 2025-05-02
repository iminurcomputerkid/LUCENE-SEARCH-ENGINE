package edu.siena.csis225.projects25;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;



/**
 * Simple Swing‐based search GUI
 *
 * @version 4/12/2025
 * @author Julien, Riley, Zi’Aire
 */
public class GUI extends JFrame {

    private JTextField searchField;
    private JTextArea  displayArea;
    private JButton    goButton;
    private searchHandler guiSearchMgr;
    private JSlider maxResSlider;
    private JLabel sliderLabel;
    private int maxRes;


    /**
     * Constructs the GUI and initializes searcher
     *
     * @param idxPath, path to Lucene index directory
     * @param showExplain, if true, include explanation details in results
     * @param maxRes, max number of search hits to return
     * @throws IOException if the Lucene index cannot be opened
     */
    public GUI(String idxPath, boolean showExplain, int maxRes) throws IOException {
        super("Search GUI");
        this.maxRes = maxRes;
        try {
            guiSearchMgr = new searchHandler(idxPath, showExplain, maxRes);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,"Error initializing search: " + e.getMessage(), "Init Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        initialize();
    }

    /**
     * Lays out swing components and executes search
     */
    private void initialize() {
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel label1 = new JLabel("Enter query:");
        searchField = new JTextField(30);
        goButton = new JButton("Search");
        topPanel.add(label1, BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(goButton, BorderLayout.EAST);
        
        sliderLabel = new JLabel("Max Results: " + maxRes);
        maxResSlider = new JSlider(JSlider.HORIZONTAL, 1, 1000, maxRes);
        maxResSlider.setMajorTickSpacing(20);
        maxResSlider.setMinorTickSpacing(1);
        maxResSlider.setPaintTicks(true);
        maxResSlider.setPaintLabels(false);
        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);
        
        maxResSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int val = maxResSlider.getValue();
                sliderLabel.setText("Max Results: " + val);
                GUI.this.maxRes = val;
                guiSearchMgr.setMaxResults(val);  
            }
        });
        JPanel sliderPanel = new JPanel(new BorderLayout(5,5));
        sliderPanel.add(sliderLabel, BorderLayout.WEST);
        sliderPanel.add(maxResSlider, BorderLayout.CENTER);

        JPanel northPanel = new JPanel(new BorderLayout(0,10));
        northPanel.add(topPanel,    BorderLayout.NORTH);
        northPanel.add(sliderPanel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        displayArea = new JTextArea();
        displayArea.setEditable(false);
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(displayArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        setLayout(new BorderLayout());
        add(northPanel, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
        
        ActionListener doSearch = ae -> executeSearch();
        goButton.addActionListener(doSearch);
        searchField.addActionListener(doSearch);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(750, 550);
        setLocationRelativeTo(null);

        goButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                executeSearch();
            }
        });

        searchField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                executeSearch();
            }
        });

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);
    }

    /**
     * reads query string from the text field, runs the search, and updates the display area
     * shows a warning message if field is empty.
     */
    private void executeSearch() {
        String qry = searchField.getText().trim();
        if (qry.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a query.",
                    "No Query",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String result = guiSearchMgr.search(qry);
            displayArea.setText(
                (result == null || result.isEmpty())
                    ? "No results found."
                    : result
            );
            displayArea.setCaretPosition(0);
        } catch (IOException | ParseException | InvalidTokenOffsetsException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error during search: " + ex.getMessage(),
                    "Search Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Launches GUI.
     */
    public static void main(String[] args) {
        String idxPath = "./IndexedData";
        boolean showExplain = true;
        int maxRes = 5;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    new GUI(idxPath, showExplain, maxRes).setVisible(true);
                } catch (IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            "Failed to launch GUI: " + e.getMessage(),
                            "Startup Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
}
