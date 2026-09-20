package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.backend.SearchBackend;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchContext;

/**
 * Represents a quantitative relevance metric that evaluates search results against ground-truth judgments.
 * <p>
 * Implementations execute searches via the provided {@link SearchBackend}, compare the retrieved
 * documents against the ground truth in {@link JudgmentSet}, and produce a {@link MetricResult}
 * containing overall aggregate scores as well as per-query breakdowns.
 */
public interface Metric {

    /**
     * Evaluates search performance across the queries in the provided judgment set.
     *
     * @param judgments the ground-truth judgments for evaluation, must not be null
     * @param backend   the search backend to execute queries against, must not be null
     * @param context   the search context and parameters, must not be null
     * @return a {@link MetricResult} containing the overall mean score and per-query scores
     */
    MetricResult evaluate(JudgmentSet judgments, SearchBackend backend, SearchContext context);
}
