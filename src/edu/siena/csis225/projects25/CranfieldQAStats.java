package edu.siena.csis225.projects25;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
/**
 * This file takes the results from CranfieldQAEvaluator and returns the Macro-F1, Micro-F1 (used to evaluate accuracy by balancing 
 * precision and recall, P(Precision) and, R(Recall)
 * 
 * @author Julien Niles, Riley Pierson, Zi'Aire Tiarado
 * @version 5/2/2025
 */
public class CranfieldQAStats {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("How to use: java ... CranfieldQAStats <yourResults.txt> <groundTruth.txt>");
            System.exit(1);
        }
        String resultsPath = args[0];
        String truthPath   = args[1];

        // load ground truth
        Map<Integer,Set<Integer>> ground = loadQA(truthPath);

        // load your results
        Map<Integer,Set<Integer>> results = loadQA(resultsPath);

        // union of all query IDs
        Set<Integer> allQ = new TreeSet<>();
        allQ.addAll(ground.keySet());
        allQ.addAll(results.keySet());

        double sumF1 = 0;
        int nq = allQ.size();

        long microTP = 0, microFP = 0, microFN = 0;

        System.out.printf("%-5s  TP   FP   FN    P      R     F1%n", "QID");
        for (int q : allQ) {
            Set<Integer> gt  = ground .getOrDefault(q, Collections.emptySet());
            Set<Integer> res = results.getOrDefault(q, Collections.emptySet());

            int tp=0;
            for(int d:res) if (gt.contains(d)) tp++;
            int fp = res.size() - tp;
            int fn = gt .size() - tp;

            double P = res.isEmpty() ? (gt.isEmpty() ? 1.0 : 0.0) : (double)tp / res.size();
            double R = gt .isEmpty() ? 1.0 : (double)tp / gt.size();
            double F1 = (P+R==0) ? 0.0 : 2*P*R/(P+R);

            System.out.printf("%3d   %3d  %3d  %3d  %1.3f  %1.3f  %1.3f%n",
                              q, tp, fp, fn, P, R, F1);

            sumF1 += F1;
            microTP += tp;
            microFP += fp;
            microFN += fn;
        }

        double macroF1 = sumF1 / nq;
        double microP = microTP + microFP == 0 ? 1.0 : (double)microTP / (microTP + microFP);
        double microR = microTP + microFN == 0 ? 1.0 : (double)microTP / (microTP + microFN);
        double microF1 = (microP+microR==0) ? 0.0 : 2*microP*microR/(microP+microR);

        System.out.println("\n___AVERAGES____");
        System.out.printf("Macro-F1: %1.3f  (avg over %d queries)%n", macroF1, nq);
        System.out.printf("Micro  P: %1.3f   R: %1.3f   F1: %1.3f%n",
                          microP, microR, microF1);
    }

    private static Map<Integer,Set<Integer>> loadQA(String path) throws Exception {
        Map<Integer,Set<Integer>> m = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                int q = Integer.parseInt(parts[0]);
                int d = Integer.parseInt(parts[1]);
                m.computeIfAbsent(q, __ -> new HashSet<>()).add(d);
            }
        }
        return m;
    }
}
