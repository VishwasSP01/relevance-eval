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
 * Computes Precision at K (P@K), measuring the fraction of retrieved documents in the top {@code k}
 * positions that are deemed relevant.
 * <p>
 * <b>Formula:</b>
 * <pre>
 * Precision@K = |{ d in TopK(results) : grade(d) &gt;= 1 }| / K
 * </pre>
 * The overall metric value is the arithmetic mean across all evaluated queries:
 * <pre>
 * Mean Precision@K = (1 / |Queries|) * sum(Precision@K(q))
 * </pre>
 * <p>
 * <b>Handling of Unjudged Documents:</b>
 * Any retrieved document that does not have an explicit judgment in the {@link JudgmentSet} for that query
 * is treated as non-relevant (i.e. having an implicit grade of 0). It contributes 0 to the count of
 * relevant documents in the numerator while still counting toward the cutoff {@code k} in the denominator.
 */
public class PrecisionAtK implements Metric {

    private final int k;

    /**
     * Constructs a PrecisionAtK metric for the specified cutoff rank.
     *
     * @param k the cutoff rank, must be strictly positive (&gt; 0)
     */
    public PrecisionAtK(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive (> 0), but was: " + k);
        }
        this.k = k;
    }

    @Override
    public MetricResult evaluate(JudgmentSet judgments, SearchBackend backend, SearchContext context) {
        Objects.requireNonNull(judgments, "judgments must not be null");
        Objects.requireNonNull(backend, "backend must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Set<String> queries = judgments.queries();
        if (queries.isEmpty()) {
            return new MetricResult("Precision@" + k, 0.0, Map.of());
        }

        Map<String, Double> perQueryValues = new LinkedHashMap<>();
        double totalPrecision = 0.0;

        for (String query : queries) {
            Map<String, Integer> gradeByDocId = new HashMap<>();
            for (Judgment j : judgments.judgmentsFor(query)) {
                gradeByDocId.put(j.docId(), Math.max(gradeByDocId.getOrDefault(j.docId(), 0), j.grade()));
            }

            List<SearchResult> results = backend.search(query, context);
            int evalLimit = (results != null) ? Math.min(k, results.size()) : 0;

            int relevantCount = 0;
            for (int i = 0; i < evalLimit; i++) {
                SearchResult result = results.get(i);
                int grade = gradeByDocId.getOrDefault(result.docId(), 0);
                if (grade >= 1) {
                    relevantCount++;
                }
            }

            double precision = (double) relevantCount / (double) k;
            perQueryValues.put(query, precision);
            totalPrecision += precision;
        }

        double overallValue = totalPrecision / queries.size();
        return new MetricResult("Precision@" + k, overallValue, perQueryValues);
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
