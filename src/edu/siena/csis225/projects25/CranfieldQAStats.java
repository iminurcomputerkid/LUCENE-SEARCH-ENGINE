package edu.siena.csis225.projects25;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
/**
 * This file takes the results from CranfieldQAEvaluator and returns the Macro-F1, Micro-F1 (used to evaluate accuracy by balancing 
 * precision and recall, P(Precision) and, R(Recall)
 * 
 * To run on myResults.txt (qId, docId), run: java -cp "bin;lib/*" edu.siena.csis225.projects25.CranfieldQAStats myResults.txt cranfield/cranfieldGroundTruth.txt

 * 
 * @author Julien Niles, Riley Pierson, Zi'Aire Tiarado
 * @version 5/2/2025
 */
public class CranfieldQAStats {

    public static void main(String[] args) throws Exception {
        //ensure correct format from QAEval
        if (args.length != 2) {
            System.err.println(
                "How to use: java ... CranfieldQAStats <yourResults.txt> <groundTruth.txt>");
            System.exit(1);
        }
        //location of results and ground truth 
        String resultsPath = args[0];
        String truthPath   = args[1];

        //load ground truth mapping queryID on set of relevant docIDs
        Map<Integer,Set<Integer>> ground = loadQA(truthPath);

        //load user-provided results mapping queryID on set of retrieved docIDs
        Map<Integer,Set<Integer>> results = loadQA(resultsPath);

        //combine all query IDs
        Set<Integer> allQ = new TreeSet<>();
        allQ.addAll(ground.keySet());
        allQ.addAll(results.keySet());

        double sumF1 = 0;            //accumulator for macro-F1 calculation
        int nq = allQ.size();        //total num of queries

        long microTP = 0, microFP = 0, microFN = 0;  //counters for micro-averaging

        //header for query stats
        System.out.printf("%-5s  TP   FP   FN    P      R     F1%n", "QID");
        for (int q : allQ) {
            //get ground truth and result sets for query
            Set<Integer> gt  = ground .getOrDefault(q, Collections.emptySet());
            Set<Integer> res = results.getOrDefault(q, Collections.emptySet());

            //count true positives
            int tp = 0;
            for (int d : res) {
                if (gt.contains(d)) tp++;
            }
            int fp = res.size() - tp;       //false positives, retreived but not relevatn
            int fn = gt .size() - tp;       // false negatives, relevant but not retrieved

            //precision: tp / (tp + fp)
            double P = res.isEmpty()
                     ? (gt.isEmpty() ? 1.0 : 0.0)
                     : (double) tp / res.size();
            // recall: tp / (tp + fn)
            double R = gt.isEmpty()
                     ? 1.0
                     : (double) tp / gt.size();
            // F1: harmonic mean of P and R
            double F1 = (P + R == 0)
                      ? 0.0
                      : 2 * P * R / (P + R);

            //print per-query metrics
            System.out.printf(
                "%3d   %3d  %3d  %3d  %1.3f  %1.3f  %1.3f%n",
                q, tp, fp, fn, P, R, F1
            );

            sumF1    += F1;       //accumulate macro-F1
            microTP  += tp;       // accumulate true positives
            microFP  += fp;       // accumulate false positives
            microFN  += fn;       // accumulate false negatives
        }

        //compute avgs
        double macroF1 = sumF1 / nq;
        double microP = (microTP + microFP == 0)
                      ? 1.0
                      : (double) microTP / (microTP + microFP);
        double microR = (microTP + microFN == 0)
                      ? 1.0
                      : (double) microTP / (microTP + microFN);
        double microF1 = (microP + microR == 0)
                       ? 0.0
                       : 2 * microP * microR / (microP + microR);

        //print macro and micro averaged metrics
        System.out.println("\n___AVERAGES____");
        System.out.printf("Macro-F1: %1.3f  (avg over %d queries)%n", macroF1, nq);
        System.out.printf(
            "Micro  P: %1.3f   R: %1.3f   F1: %1.3f%n",
            microP, microR, microF1
        );
    }

    /**
     * Loads a file of "qID docID" lines into a map of queryID -> set of docIDs.
     * @param path, loads results from QAEval (in the form of myResults.txt or something.txt)
     */
    private static Map<Integer,Set<Integer>> loadQA(String path) throws Exception {
        Map<Integer,Set<Integer>> m = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;           //skip blanks
                String[] parts = line.split("\\s+");  //split on whitespace
                int q = Integer.parseInt(parts[0]);     //parse qID
                int d = Integer.parseInt(parts[1]);     // parse doc ID
                // add this doc ID to the set for query
                m.computeIfAbsent(q, __ -> new HashSet<>()).add(d);
            }
        }
        return m;
    }
}

