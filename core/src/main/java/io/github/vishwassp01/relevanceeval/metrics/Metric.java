package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.backend.SearchBackend;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchContext;
import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a quantitative relevance metric that evaluates search results against ground-truth judgments.
 * <p>
 * Separates the concerns of search result retrieval and metric scoring by defining {@link #computeFrom(JudgmentSet, Map)},
 * while the default {@link #evaluate(JudgmentSet, SearchBackend, SearchContext)} fetches search results from the
 * {@link SearchBackend} into a map and delegates to {@link #computeFrom(JudgmentSet, Map)}.
 */
public interface Metric {

    /**
     * Computes the relevance metric directly from pre-retrieved search results.
     *
     * @param judgments      the ground-truth judgments, must not be null
     * @param resultsByQuery mapping from query string to the list of retrieved search results, must not be null
     * @return a {@link MetricResult} containing the overall mean score and per-query scores
     */
    MetricResult computeFrom(JudgmentSet judgments, Map<String, List<SearchResult>> resultsByQuery);

    /**
     * Evaluates search performance across the queries in the provided judgment set by fetching
     * results from the search backend into a map and delegating to {@link #computeFrom(JudgmentSet, Map)}.
     *
     * @param judgments the ground-truth judgments for evaluation, must not be null
     * @param backend   the search backend to execute queries against, must not be null
     * @param context   the search context and parameters, must not be null
     * @return a {@link MetricResult} containing the overall mean score and per-query scores
     */
    default MetricResult evaluate(JudgmentSet judgments, SearchBackend backend, SearchContext context) {
        Objects.requireNonNull(judgments, "judgments must not be null");
        Objects.requireNonNull(backend, "backend must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Map<String, List<SearchResult>> resultsByQuery = new LinkedHashMap<>();
        for (String query : judgments.queries()) {
            List<SearchResult> results = backend.search(query, context);
            resultsByQuery.put(query, results != null ? results : List.of());
        }

        return computeFrom(judgments, resultsByQuery);
    }
}
