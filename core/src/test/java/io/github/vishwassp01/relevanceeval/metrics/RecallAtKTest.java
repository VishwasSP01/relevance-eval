package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.backend.InMemorySearchBackend;
import io.github.vishwassp01.relevanceeval.backend.SearchBackend;
import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecallAtKTest {

    private final SearchContext defaultContext = new SearchContext("test-index", 10, Map.of());

    @Test
    void throwsExceptionWhenKIsZeroOrNegative() {
        assertThatThrownBy(() -> new RecallAtK(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("k must be positive");

        assertThatThrownBy(() -> new RecallAtK(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("k must be positive");
    }

    @Test
    void returnsOneWhenAllRelevantDocumentsAreFound() {
        // Hand calculation for k = 3:
        // Judgments:
        //   d1 -> grade 3 (relevant, >= 1)
        //   d2 -> grade 2 (relevant, >= 1)
        //   d3 -> grade 0 (not relevant, < 1)
        // Total relevant judgments for query: {d1, d2} -> count = 2
        // Returned top 3: [d1, d2, d4]
        // Relevant documents retrieved in top 3: {d1, d2} -> count = 2
        // Expected Recall@3 = 2 / 2 = 1.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 3),
                new Judgment("q1", "d2", 2),
                new Judgment("q1", "d3", 0)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2", "d4")
        ));

        Metric metric = new RecallAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(1.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 1.0);
    }

    @Test
    void returnsZeroWhenNoRelevantDocumentsAreFound() {
        // Hand calculation for k = 3:
        // Judgments:
        //   d1 -> grade 2 (relevant, >= 1)
        //   d2 -> grade 1 (relevant, >= 1)
        // Total relevant judgments for query: {d1, d2} -> count = 2
        // Returned top 3: [d3, d4, d5] (all unjudged -> implicit grade 0)
        // Relevant documents retrieved in top 3: count = 0
        // Expected Recall@3 = 0 / 2 = 0.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 2),
                new Judgment("q1", "d2", 1)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d3", "d4", "d5")
        ));

        Metric metric = new RecallAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 0.0);
    }

    @Test
    void returnsHalfWhenHalfOfRelevantDocumentsAreFound() {
        // Hand calculation for k = 2:
        // Judgments:
        //   d1 -> grade 3 (relevant, >= 1)
        //   d2 -> grade 2 (relevant, >= 1)
        // Total relevant judgments for query: {d1, d2} -> count = 2
        // Returned top 2: [d1, d3] (only d1 is relevant; d3 is unjudged)
        // Relevant documents retrieved in top 2: {d1} -> count = 1
        // Expected Recall@2 = 1 / 2 = 0.5
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 3),
                new Judgment("q1", "d2", 2)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d3")
        ));

        Metric metric = new RecallAtK(2);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.5);
        assertThat(result.perQueryValues()).containsEntry("q1", 0.5);
    }

    @Test
    void returnsZeroWhenQueryHasNoRelevantJudgments() {
        // Hand calculation for k = 3:
        // Judgments:
        //   d1 -> grade 0 (not relevant, < 1)
        //   d2 -> grade 0 (not relevant, < 1)
        // Total relevant judgments: 0
        // Rule: If a query has no relevant judgments, its value is 0.
        // Expected Recall@3 = 0.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 0),
                new Judgment("q1", "d2", 0)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2", "d3")
        ));

        Metric metric = new RecallAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 0.0);
    }

    @Test
    void computesMeanAcrossMultipleQueries() {
        // Hand calculation for k = 2:
        // Query q1:
        //   Judgments: d1 (grade 2), d2 (grade 1) -> 2 relevant
        //   Returned top 2: [d1, d2] -> 2 found
        //   Recall@2(q1) = 2 / 2 = 1.0
        // Query q2:
        //   Judgments: d3 (grade 3), d4 (grade 2) -> 2 relevant
        //   Returned top 2: [d3, unknown] -> 1 found
        //   Recall@2(q2) = 1 / 2 = 0.5
        // Expected overall mean = (1.0 + 0.5) / 2 = 0.75
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 2),
                new Judgment("q1", "d2", 1),
                new Judgment("q2", "d3", 3),
                new Judgment("q2", "d4", 2)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2"),
                "q2", List.of("d3", "unknown")
        ));

        Metric metric = new RecallAtK(2);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.75);
        assertThat(result.perQueryValues()).containsEntry("q1", 1.0);
        assertThat(result.perQueryValues()).containsEntry("q2", 0.5);
    }
}
