package io.github.vishwassp01.relevanceeval.stats;

/**
 * Result of a statistical significance test comparing two sets of evaluation scores.
 *
 * @param pValue     the estimated p-value with standard +1 correction to avoid being exactly zero
 * @param meanDelta  the observed mean difference across shared queries (candidate - baseline)
 * @param sampleSize the number of queries evaluated in both runs
 * @param trials     the number of randomization permutations performed
 */
public record SignificanceResult(
        double pValue,
        double meanDelta,
        int sampleSize,
        int trials
) {
    public SignificanceResult {
        if (sampleSize < 0) {
            throw new IllegalArgumentException("sampleSize must be non-negative: " + sampleSize);
        }
        if (trials < 0) {
            throw new IllegalArgumentException("trials must be non-negative: " + trials);
        }
        if (Double.isNaN(pValue) || pValue < 0.0 || pValue > 1.0) {
            throw new IllegalArgumentException("pValue must be between 0.0 and 1.0, got: " + pValue);
        }
    }
}
