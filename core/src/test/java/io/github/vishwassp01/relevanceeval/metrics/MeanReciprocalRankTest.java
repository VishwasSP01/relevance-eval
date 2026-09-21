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

class MeanReciprocalRankTest {

    private final SearchContext defaultContext = new SearchContext("test-index", 10, Map.of());

    @Test
    void returnsOneWhenFirstResultIsRelevant() {
        // Hand calculation:
        // Judgments:
        //   d1 -> grade 2 (relevant, >= 1)
        //   d2 -> grade 3 (relevant, >= 1)
        // Returned results: [d1, d2, d3]
        // First result with grade >= 1 is d1 at rank 1.
        // Reciprocal Rank = 1 / 1 = 1.0
        // Expected MRR = 1.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 2),
                new Judgment("q1", "d2", 3)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2", "d3")
        ));

        Metric metric = new MeanReciprocalRank();
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(1.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 1.0);
    }

    @Test
    void returnsOneThirdWhenFirstRelevantResultIsAtRankThree() {
        // Hand calculation:
        // Judgments:
        //   d1 -> grade 0 (not relevant, < 1)
        //   d3 -> grade 1 (relevant, >= 1)
        // Returned results: [d1, d2, d3, d4]
        // Rank 1: d1 (grade 0) -> not relevant
        // Rank 2: d2 (unjudged) -> not relevant
        // Rank 3: d3 (grade 1) -> first relevant at rank 3
        // Reciprocal Rank = 1 / 3 = 0.3333333333333333
        // Expected MRR = 1.0 / 3.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 0),
                new Judgment("q1", "d3", 1)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2", "d3", "d4")
        ));

        Metric metric = new MeanReciprocalRank();
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(1.0 / 3.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 1.0 / 3.0);
    }

    @Test
    void returnsZeroWhenNoRelevantResultsAppear() {
        // Hand calculation:
        // Judgments:
        //   d1 -> grade 0 (not relevant, < 1)
        //   d2 -> grade 2 (relevant, >= 1, but NOT returned by backend)
        // Returned results: [d1, unjudged1, unjudged2]
        // None of the returned results have grade >= 1.
        // Reciprocal Rank = 0.0
        // Expected MRR = 0.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 0),
                new Judgment("q1", "d2", 2)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "unjudged1", "unjudged2")
        ));

        Metric metric = new MeanReciprocalRank();
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 0.0);
    }

    @Test
    void computesMeanAcrossMultipleQueries() {
        // Hand calculation:
        // Query q1:
        //   Returned: [d1, d2] -> d1 is grade 2 (rank 1) -> RR(q1) = 1 / 1 = 1.0
        // Query q2:
        //   Returned: [unjudged, d3] -> d3 is grade 1 (rank 2) -> RR(q2) = 1 / 2 = 0.5
        // Query q3:
        //   Returned: [unjudged1, unjudged2] -> none relevant -> RR(q3) = 0.0
        // Expected MRR = (1.0 + 0.5 + 0.0) / 3 = 1.5 / 3 = 0.5
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 2),
                new Judgment("q2", "d3", 1),
                new Judgment("q3", "d4", 3)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2"),
                "q2", List.of("unjudged", "d3"),
                "q3", List.of("unjudged1", "unjudged2")
        ));

        Metric metric = new MeanReciprocalRank();
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.5);
        assertThat(result.perQueryValues()).containsEntry("q1", 1.0);
        assertThat(result.perQueryValues()).containsEntry("q2", 0.5);
        assertThat(result.perQueryValues()).containsEntry("q3", 0.0);
    }
}
