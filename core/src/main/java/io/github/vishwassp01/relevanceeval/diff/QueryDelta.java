package io.github.vishwassp01.relevanceeval.diff;

/**
 * Represents the change in an evaluation metric score for a specific query between two evaluation runs.
 *
 * @param query          the search query string
 * @param baselineValue  the metric score in the baseline run, or {@code Double.NaN} if absent
 * @param candidateValue the metric score in the candidate run, or {@code Double.NaN} if absent
 */
public record QueryDelta(String query, double baselineValue, double candidateValue) {

    public QueryDelta {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
    }

    /**
     * Calculates the change in score from baseline to candidate (candidate - baseline).
     *
     * @return the score delta, or {@code Double.NaN} if the query is missing from either run
     */
    public double delta() {
        if (Double.isNaN(candidateValue) || Double.isNaN(baselineValue)) {
            return Double.NaN;
        }
        return candidateValue - baselineValue;
    }
}
