package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.backend.SearchBackend;
import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchContext;
import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes Normalized Discounted Cumulative Gain at K (NDCG@K), measuring ranking quality
 * by rewarding highly relevant documents placed near the top of the search results list.
 * <p>
 * <b>Formulas:</b>
 * <ul>
 *   <li>
 *     <b>Discounted Cumulative Gain (DCG@K):</b>
 *     <pre>
 *     DCG@K = sum_{i=1}^{min(k, |results|)} (2^{grade_i} - 1) / log2(i + 1)
 *     </pre>
 *     where {@code i} is the 1-based position of the retrieved document.
 *   </li>
 *   <li>
 *     <b>Ideal Discounted Cumulative Gain (IDCG@K):</b>
 *     <pre>
 *     IDCG@K = sum_{i=1}^{min(k, |judgments|)} (2^{grade_i^*} - 1) / log2(i + 1)
 *     </pre>
 *     where {@code grade_i^*} represents the ground-truth judgments for that query, sorted
 *     in descending order of grade.
 *   </li>
 *   <li>
 *     <b>Normalized Discounted Cumulative Gain (NDCG@K):</b>
 *     <pre>
 *     NDCG@K = (IDCG@K == 0) ? 0.0 : (DCG@K / IDCG@K)
 *     </pre>
 *   </li>
 *   <li>
 *     <b>Overall Metric Score:</b>
 *     <pre>
 *     Mean NDCG@K = (1 / |Queries|) * sum(NDCG@K(q))
 *     </pre>
 *   </li>
 * </ul>
 * <p>
 * <b>Handling of Unjudged Documents:</b>
 * Any retrieved document without an explicit entry in the {@link JudgmentSet} for that query
 * is assigned an implicit grade of 0. Since {@code 2^0 - 1 = 0}, unjudged documents contribute
 * zero gain to DCG@K, while still consuming a rank position and discounting any relevant
 * documents ranked below them.
 */
public class NdcgAtK implements Metric {

    private static final double LOG2 = Math.log(2.0);

    private final int k;

    /**
     * Constructs an NdcgAtK metric for the specified cutoff rank.
     *
     * @param k the cutoff rank, must be strictly positive (&gt; 0)
     */
    public NdcgAtK(int k) {
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
            return new MetricResult("NDCG@" + k, 0.0, Map.of());
        }

        Map<String, Double> perQueryValues = new LinkedHashMap<>();
        double totalNdcg = 0.0;

        for (String query : queries) {
            Map<String, Integer> gradeByDocId = new HashMap<>();
            for (Judgment j : judgments.judgmentsFor(query)) {
                gradeByDocId.put(j.docId(), Math.max(gradeByDocId.getOrDefault(j.docId(), 0), j.grade()));
            }

            // Calculate DCG@K over top k returned results
            List<SearchResult> results = backend.search(query, context);
            int evalLimit = (results != null) ? Math.min(k, results.size()) : 0;

            double dcg = 0.0;
            for (int i = 0; i < evalLimit; i++) {
                int rank = i + 1;
                SearchResult result = results.get(i);
                int grade = gradeByDocId.getOrDefault(result.docId(), 0);
                if (grade > 0) {
                    double gain = Math.pow(2.0, grade) - 1.0;
                    double discount = Math.log(rank + 1) / LOG2;
                    dcg += gain / discount;
                }
            }

            // Calculate IDCG@K over top k judgments sorted descending by grade
            List<Integer> idealGrades = gradeByDocId.values().stream()
                    .filter(grade -> grade > 0)
                    .sorted(Comparator.reverseOrder())
                    .limit(k)
                    .toList();

            double idcg = 0.0;
            for (int i = 0; i < idealGrades.size(); i++) {
                int rank = i + 1;
                int grade = idealGrades.get(i);
                double gain = Math.pow(2.0, grade) - 1.0;
                double discount = Math.log(rank + 1) / LOG2;
                idcg += gain / discount;
            }

            double ndcg = (idcg == 0.0) ? 0.0 : (dcg / idcg);
            perQueryValues.put(query, ndcg);
            totalNdcg += ndcg;
        }

        double overallValue = totalNdcg / queries.size();
        return new MetricResult("NDCG@" + k, overallValue, perQueryValues);
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
