package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.backend.SearchBackend;
import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchContext;
import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes Recall at K (R@K), measuring the fraction of all relevant documents in the judgment set
 * that appear within the top {@code k} positions of the search results.
 * <p>
 * <b>Grade Threshold:</b>
 * A document is deemed relevant if and only if its relevance grade is greater than or equal to 1
 * ({@code grade >= 1}). Documents with a grade of 0 or unjudged documents are treated as non-relevant.
 * <p>
 * <b>Formula:</b>
 * For a single query \(q\):
 * <pre>
 * Recall@K(q) = |{ d in TopK(results) : grade(d) &gt;= 1 }| / |{ d in Judgments(q) : grade(d) &gt;= 1 }|
 * </pre>
 * If a query has no relevant judgments (i.e. {@code |{ d in Judgments(q) : grade(d) >= 1 }| == 0}),
 * its Recall@K value is defined to be {@code 0.0}.
 * <p>
 * The overall metric value across all evaluated queries is the arithmetic mean:
 * <pre>
 * Mean Recall@K = (1 / |Queries|) * sum_{q} Recall@K(q)
 * </pre>
 */
public class RecallAtK implements Metric {

    private final int k;

    /**
     * Constructs a RecallAtK metric for the specified cutoff rank.
     *
     * @param k the cutoff rank, must be strictly positive (&gt; 0)
     */
    public RecallAtK(int k) {
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
            return new MetricResult("Recall@" + k, 0.0, Map.of());
        }

        Map<String, Double> perQueryValues = new LinkedHashMap<>();
        double totalRecall = 0.0;

        for (String query : queries) {
            Map<String, Integer> gradeByDocId = new HashMap<>();
            for (Judgment j : judgments.judgmentsFor(query)) {
                gradeByDocId.put(j.docId(), Math.max(gradeByDocId.getOrDefault(j.docId(), 0), j.grade()));
            }

            Set<String> relevantDocIds = new HashSet<>();
            for (Map.Entry<String, Integer> entry : gradeByDocId.entrySet()) {
                if (entry.getValue() >= 1) {
                    relevantDocIds.add(entry.getKey());
                }
            }

            double recall;
            if (relevantDocIds.isEmpty()) {
                recall = 0.0;
            } else {
                List<SearchResult> results = backend.search(query, context);
                int evalLimit = (results != null) ? Math.min(k, results.size()) : 0;

                Set<String> retrievedRelevant = new HashSet<>();
                for (int i = 0; i < evalLimit; i++) {
                    SearchResult result = results.get(i);
                    if (relevantDocIds.contains(result.docId())) {
                        retrievedRelevant.add(result.docId());
                    }
                }
                recall = (double) retrievedRelevant.size() / (double) relevantDocIds.size();
            }

            perQueryValues.put(query, recall);
            totalRecall += recall;
        }

        double overallValue = totalRecall / queries.size();
        return new MetricResult("Recall@" + k, overallValue, perQueryValues);
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
