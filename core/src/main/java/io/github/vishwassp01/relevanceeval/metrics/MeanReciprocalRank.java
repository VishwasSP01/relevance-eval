package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.backend.SearchBackend;
import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchContext;
import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes Mean Reciprocal Rank (MRR), evaluating how high the first relevant document is ranked.
 * <p>
 * <b>Grade Threshold:</b>
 * A document is deemed relevant if and only if its relevance grade is greater than or equal to 1
 * ({@code grade >= 1}). Documents with grade 0 and unjudged documents are treated as non-relevant.
 * <p>
 * <b>Formula:</b>
 * For each query \(q\), the Reciprocal Rank (RR) is defined as:
 * <pre>
 * RR(q) = 1 / rank_first
 * </pre>
 * where {@code rank_first} is the 1-based rank position of the first returned result with {@code grade >= 1}.
 * If no relevant result appears among the results returned by the search backend, the reciprocal rank is {@code 0.0}.
 * Only results returned by the backend (within the configured {@link SearchContext#size()}) are considered.
 * <p>
 * The overall metric value is the arithmetic mean of reciprocal ranks across all evaluated queries:
 * <pre>
 * MRR = (1 / |Queries|) * sum_{q} RR(q)
 * </pre>
 */
public class MeanReciprocalRank implements Metric {

    /**
     * Constructs a default MeanReciprocalRank metric.
     */
    public MeanReciprocalRank() {
    }

    @Override
    public MetricResult evaluate(JudgmentSet judgments, SearchBackend backend, SearchContext context) {
        Objects.requireNonNull(judgments, "judgments must not be null");
        Objects.requireNonNull(backend, "backend must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Set<String> queries = judgments.queries();
        if (queries.isEmpty()) {
            return new MetricResult("MRR", 0.0, Map.of());
        }

        Map<String, Double> perQueryValues = new LinkedHashMap<>();
        double totalReciprocalRank = 0.0;

        for (String query : queries) {
            Map<String, Integer> gradeByDocId = new HashMap<>();
            for (Judgment j : judgments.judgmentsFor(query)) {
                gradeByDocId.put(j.docId(), Math.max(gradeByDocId.getOrDefault(j.docId(), 0), j.grade()));
            }

            List<SearchResult> results = backend.search(query, context);
            double rr = 0.0;

            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    SearchResult result = results.get(i);
                    int grade = gradeByDocId.getOrDefault(result.docId(), 0);
                    if (grade >= 1) {
                        int rank = (result.rank() > 0) ? result.rank() : (i + 1);
                        rr = 1.0 / (double) rank;
                        break;
                    }
                }
            }

            perQueryValues.put(query, rr);
            totalReciprocalRank += rr;
        }

        double overallValue = totalReciprocalRank / queries.size();
        return new MetricResult("MRR", overallValue, perQueryValues);
    }
}
