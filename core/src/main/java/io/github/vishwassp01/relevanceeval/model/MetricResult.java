package io.github.vishwassp01.relevanceeval.model;

import java.util.Map;

/**
 * Encapsulates the output of a search relevance metric evaluation.
 * <p>
 * This record stores the overall macro-aggregated metric score across an entire evaluation run,
 * alongside individual per-query evaluation scores. It exists to provide structured, auditable
 * evaluation results for reporting, comparison, and regression testing.
 *
 * @param metricName      the name of the evaluated metric (e.g., "NDCG@10", "Precision@5"), must not be null or blank
 * @param overallValue    the aggregated macro score across all evaluated queries
 * @param perQueryValues  a mapping of each query to its calculated metric score, defensively copied
 */
public record MetricResult(String metricName, double overallValue, Map<String, Double> perQueryValues) {

    public MetricResult {
        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("metricName must not be null or blank");
        }
        perQueryValues = Map.copyOf(perQueryValues);
    }
}
