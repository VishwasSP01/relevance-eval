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

class PrecisionAtKTest {

    private final SearchContext defaultContext = new SearchContext("test-index", 10, Map.of());

    @Test
    void returnsOneWhenAllTopKResultsAreRelevant() {
        // Hand calculation for k = 3:
        // Judgments: d1 (grade 1), d2 (grade 2), d3 (grade 3) -> all have grade >= 1
        // Returned top 3: [d1, d2, d3]
        // Relevant count in top 3 = 3
        // Expected Precision@3 = 3 / 3 = 1.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 1),
                new Judgment("q1", "d2", 2),
                new Judgment("q1", "d3", 3)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2", "d3")
        ));

        Metric metric = new PrecisionAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(1.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 1.0);
    }

    @Test
    void returnsZeroWhenNoTopKResultsAreRelevant() {
        // Hand calculation for k = 3:
        // Judgments: d1 (grade 2), d2 (grade 3)
        // Returned top 3: [unknown1, unknown2, unknown3]
        // None of the returned documents have a judgment -> grade defaults to 0 (< 1)
        // Relevant count in top 3 = 0
        // Expected Precision@3 = 0 / 3 = 0.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 2),
                new Judgment("q1", "d2", 3)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("unknown1", "unknown2", "unknown3")
        ));

        Metric metric = new PrecisionAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 0.0);
    }

    @Test
    void returnsExactFractionWhenHalfOfTopKResultsAreRelevant() {
        // Hand calculation for k = 4:
        // Judgments:
        //   d1 -> grade 2 (relevant, >= 1)
        //   d2 -> grade 1 (relevant, >= 1)
        // Returned top 4: [d1, unjudgedA, d2, unjudgedB]
        // Relevant documents in top 4: {d1, d2} -> count = 2
        // Expected Precision@4 = 2 / 4 = 0.5
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 2),
                new Judgment("q1", "d2", 1)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "unjudgedA", "d2", "unjudgedB")
        ));

        Metric metric = new PrecisionAtK(4);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.5);
        assertThat(result.perQueryValues()).containsEntry("q1", 0.5);
    }

    @Test
    void treatsGradeZeroDocumentsAsNotRelevant() {
        // Hand calculation for k = 2:
        // Judgments:
        //   d1 -> grade 0 (not relevant, grade < 1)
        //   d2 -> grade 2 (relevant, grade >= 1)
        // Returned top 2: [d1, d2]
        // Relevant documents in top 2: only d2 -> count = 1
        // Expected Precision@2 = 1 / 2 = 0.5
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 0),
                new Judgment("q1", "d2", 2)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2")
        ));

        Metric metric = new PrecisionAtK(2);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.5);
        assertThat(result.perQueryValues()).containsEntry("q1", 0.5);
    }
}
