package io.github.vishwassp01.relevanceeval.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Non-parametric paired randomization test (permutation test) for assessing
 * statistical significance of metric score differences between baseline and candidate runs.
 */
public class PairedRandomizationTest {

    public static final int DEFAULT_TRIALS = 10_000;
    public static final long DEFAULT_SEED = 42L;

    /**
     * Runs a paired randomization test using default trials (10,000) and default seed (42).
     *
     * @param baseline  query-to-score map from the baseline run
     * @param candidate query-to-score map from the candidate run
     * @return the significance test result
     */
    public SignificanceResult test(Map<String, Double> baseline, Map<String, Double> candidate) {
        return test(baseline, candidate, DEFAULT_TRIALS, DEFAULT_SEED);
    }

    /**
     * Runs a paired randomization test using the specified number of trials and default seed (42).
     *
     * @param baseline  query-to-score map from the baseline run
     * @param candidate query-to-score map from the candidate run
     * @param trials    number of randomization trials
     * @return the significance test result
     */
    public SignificanceResult test(Map<String, Double> baseline, Map<String, Double> candidate, int trials) {
        return test(baseline, candidate, trials, DEFAULT_SEED);
    }

    /**
     * Runs a paired randomization test with explicit trials and seed.
     *
     * @param baseline  query-to-score map from the baseline run
     * @param candidate query-to-score map from the candidate run
     * @param trials    number of randomization trials
     * @param seed      seed for the pseudo-random number generator
     * @return the significance test result
     */
    public SignificanceResult test(Map<String, Double> baseline, Map<String, Double> candidate, int trials, long seed) {
        if (trials < 0) {
            throw new IllegalArgumentException("trials must be non-negative: " + trials);
        }

        if (baseline == null || candidate == null || baseline.isEmpty() || candidate.isEmpty()) {
            return new SignificanceResult(1.0, 0.0, 0, trials);
        }

        // Take only queries present in both maps. That count is sampleSize.
        List<String> commonQueries = new ArrayList<>();
        for (String query : baseline.keySet()) {
            if (query != null && candidate.containsKey(query)) {
                Double bVal = baseline.get(query);
                Double cVal = candidate.get(query);
                if (bVal != null && cVal != null) {
                    commonQueries.add(query);
                }
            }
        }

        int sampleSize = commonQueries.size();
        if (sampleSize == 0) {
            return new SignificanceResult(1.0, 0.0, 0, trials);
        }

        // Sort queries for deterministic iteration order across map implementations
        Collections.sort(commonQueries);

        // For each, compute delta = candidate - baseline
        double[] deltas = new double[sampleSize];
        double sumDelta = 0.0;
        for (int i = 0; i < sampleSize; i++) {
            String query = commonQueries.get(i);
            double delta = candidate.get(query) - baseline.get(query);
            deltas[i] = delta;
            sumDelta += delta;
        }

        // meanDelta = the mean of those deltas. This is the observed statistic.
        double meanDelta = sumDelta / sampleSize;
        double absObservedMean = Math.abs(meanDelta);

        // Use java.util.Random seeded with seed so results are reproducible
        Random random = new Random(seed);
        int count = 0;

        // Repeat trials times: for each delta independently, multiply by +1 or -1 with equal probability;
        // compute the mean of the flipped deltas.
        for (int t = 0; t < trials; t++) {
            double trialSum = 0.0;
            for (int i = 0; i < sampleSize; i++) {
                trialSum += random.nextBoolean() ? deltas[i] : -deltas[i];
            }
            double trialMean = trialSum / sampleSize;

            // Count how many trials produced an absolute mean greater than or equal to the absolute observed mean
            if (Math.abs(trialMean) >= absObservedMean) {
                count++;
            }
        }

        // pValue = (count + 1) / (trials + 1) — the +1 on both sides is the standard correction so p is never exactly zero
        double pValue = (double) (count + 1) / (trials + 1);

        return new SignificanceResult(pValue, meanDelta, sampleSize, trials);
    }
}
