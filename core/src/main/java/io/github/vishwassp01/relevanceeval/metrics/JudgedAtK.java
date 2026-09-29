package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes Judged at K (Judged@K), measuring the fraction of retrieved documents in the top
 * {@code k} positions that appear anywhere in the query's ground-truth judgments.
 * <p>
 * <b>What it computes:</b>
 * For each query, of the top {@code k} results returned, Judged@K calculates what fraction have an explicit
 * judgment in the query's judgment set — regardless of grade. A grade 0 judgment counts as judged. A result
 * with no judgment at all does not.
 * <p>
 * <b>Denominator — min(k, results.size()) vs k:</b>
 * Unlike {@link PrecisionAtK}, which divides by {@code k} to evaluate quality across all available ranking slots,
 * Judged@K divides by the number of results actually returned in the top-{@code k}, i.e., {@code min(k, results.size())}.
 * This distinction is intentional:
 * <ul>
 *   <li>{@code Precision@K} measures retrieval quality across the fixed rank slots {@code k}.</li>
 *   <li>{@code Judged@K} measures judgment coverage of what was actually returned. If an index only returns 3
 *       documents for a query and all 3 are judged, dividing by {@code k=10} would yield 0.30, falsely implying
 *       that 70% of returned results are unjudged. Dividing by {@code min(k, results.size()) = 3} correctly yields 1.0.</li>
 * </ul>
 * <p>
 * <b>Edge Cases:</b>
 * If a query returns no results from the search backend (i.e. {@code results.isEmpty()}), its value is defined as {@code 0.0}.
 * <p>
 * <b>Overall Value:</b>
 * The overall metric value is the arithmetic mean across all evaluated queries, retaining the per-query values.
 */
public class JudgedAtK implements Metric {

    private final int k;

    /**
     * Constructs a JudgedAtK metric for the specified cutoff rank.
     *
     * @param k the cutoff rank, must be strictly positive (&gt; 0)
     */
    public JudgedAtK(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive (> 0), but was: " + k);
        }
        this.k = k;
    }

    @Override
    public MetricResult computeFrom(JudgmentSet judgments, Map<String, List<SearchResult>> resultsByQuery) {
        Objects.requireNonNull(judgments, "judgments must not be null");
        Objects.requireNonNull(resultsByQuery, "resultsByQuery must not be null");

        Set<String> queries = judgments.queries();
        if (queries.isEmpty()) {
            return new MetricResult("Judged@" + k, 0.0, Map.of());
        }

        Map<String, Double> perQueryValues = new LinkedHashMap<>();
        double totalJudged = 0.0;

        for (String query : queries) {
            Set<String> judgedDocIds = new HashSet<>();
            for (Judgment j : judgments.judgmentsFor(query)) {
                judgedDocIds.add(j.docId());
            }

            List<SearchResult> results = resultsByQuery.get(query);
            if (results == null || results.isEmpty()) {
                perQueryValues.put(query, 0.0);
                continue;
            }

            int returnedInTopK = Math.min(k, results.size());
            if (returnedInTopK == 0) {
                perQueryValues.put(query, 0.0);
                continue;
            }

            int judgedCount = 0;
            for (int i = 0; i < returnedInTopK; i++) {
                if (judgedDocIds.contains(results.get(i).docId())) {
                    judgedCount++;
                }
            }

            double queryScore = (double) judgedCount / (double) returnedInTopK;
            perQueryValues.put(query, queryScore);
            totalJudged += queryScore;
        }

        double overallValue = totalJudged / queries.size();
        return new MetricResult("Judged@" + k, overallValue, perQueryValues);
    }

    /**
     * Returns the cutoff threshold {@code k}.
     *
     * @return the rank cutoff {@code k}
     */
    public int k() {
        return k;
    }
}
