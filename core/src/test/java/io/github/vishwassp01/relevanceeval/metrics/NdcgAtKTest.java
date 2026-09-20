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
import static org.assertj.core.api.Assertions.within;

class NdcgAtKTest {

    private final SearchContext defaultContext = new SearchContext("test-index", 10, Map.of());

    @Test
    void returnsOneForPerfectRanking() {
        // Hand calculation for k = 3:
        // Judgments: d1 (grade 3), d2 (grade 2), d3 (grade 1)
        // Ideal ranking: [d1, d2, d3]
        //   rank 1 (d1, grade 3): (2^3 - 1) / log2(1 + 1) = 7.0 / 1.0 = 7.0
        //   rank 2 (d2, grade 2): (2^2 - 1) / log2(2 + 1) = 3.0 / log2(3) ≈ 1.892789
        //   rank 3 (d3, grade 1): (2^1 - 1) / log2(3 + 1) = 1.0 / log2(4) = 1.0 / 2.0 = 0.5
        // IDCG = 7.0 + (3.0 / log2(3)) + 0.5 ≈ 9.392789
        //
        // Returned ranking matches ideal exactly: [d1, d2, d3]
        // DCG = IDCG ≈ 9.392789
        // Expected NDCG = DCG / IDCG = 9.392789 / 9.392789 = 1.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 3),
                new Judgment("q1", "d2", 2),
                new Judgment("q1", "d3", 1)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2", "d3")
        ));

        Metric metric = new NdcgAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(1.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 1.0);
    }

    @Test
    void calculatesCorrectNdcgForReversedRanking() {
        // Hand calculation for k = 3:
        // Judgments: d1 (grade 3), d2 (grade 2), d3 (grade 1)
        //
        // Ideal order: [d1, d2, d3]
        //   rank 1 (d1, grade 3): (2^3 - 1) / log2(1 + 1) = 7.0 / log2(2) = 7.0 / 1.0 = 7.0
        //   rank 2 (d2, grade 2): (2^2 - 1) / log2(2 + 1) = 3.0 / log2(3) ≈ 3.0 / 1.5849625 = 1.892789
        //   rank 3 (d3, grade 1): (2^1 - 1) / log2(3 + 1) = 1.0 / log2(4) = 1.0 / 2.0 = 0.5
        // IDCG = 7.0 + 1.89278926 + 0.5 = 9.39278926
        //
        // Returned reversed order: [d3, d2, d1]
        //   rank 1 (d3, grade 1): (2^1 - 1) / log2(1 + 1) = 1.0 / log2(2) = 1.0 / 1.0 = 1.0
        //   rank 2 (d2, grade 2): (2^2 - 1) / log2(2 + 1) = 3.0 / log2(3) ≈ 3.0 / 1.5849625 = 1.892789
        //   rank 3 (d1, grade 3): (2^3 - 1) / log2(3 + 1) = 7.0 / log2(4) = 7.0 / 2.0 = 3.5
        // DCG = 1.0 + 1.89278926 + 3.5 = 6.39278926
        //
        // Expected NDCG = DCG / IDCG = 6.39278926 / 9.39278926 ≈ 0.680606
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 3),
                new Judgment("q1", "d2", 2),
                new Judgment("q1", "d3", 1)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d3", "d2", "d1")
        ));

        Metric metric = new NdcgAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isCloseTo(0.680606, within(1e-5));
        assertThat(result.perQueryValues().get("q1")).isCloseTo(0.680606, within(1e-5));
    }

    @Test
    void returnsZeroWhenAllReturnedResultsAreUnjudged() {
        // Hand calculation for k = 3:
        // Judgments: d1 (grade 3), d2 (grade 2) -> IDCG > 0
        // Returned: [unknown1, unknown2, unknown3]
        // Unjudged documents have implicit grade 0:
        //   rank 1: (2^0 - 1) / log2(2) = 0.0 / 1.0 = 0.0
        //   rank 2: (2^0 - 1) / log2(3) = 0.0 / log2(3) = 0.0
        //   rank 3: (2^0 - 1) / log2(4) = 0.0 / 2.0 = 0.0
        // DCG = 0.0
        // Expected NDCG = 0.0 / IDCG = 0.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 3),
                new Judgment("q1", "d2", 2)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("unknown1", "unknown2", "unknown3")
        ));

        Metric metric = new NdcgAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 0.0);
    }

    @Test
    void returnsZeroAndDoesNotThrowOrProduceNanWhenNoJudgmentsExistForQuery() {
        // Hand calculation:
        // Judgment has grade 0: d1 (grade 0)
        // Ideal ranking has no positive grades: IDCG = 0.0
        // Spec requirement: "NDCG = DCG / IDCG, and must be 0 when IDCG is 0"
        // Expected NDCG = 0.0 (must not be NaN or throw ArithmeticException / division by zero)
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 0)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2")
        ));

        Metric metric = new NdcgAtK(3);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(0.0);
        assertThat(Double.isNaN(result.overallValue())).isFalse();
        assertThat(result.perQueryValues().get("q1")).isEqualTo(0.0);
        assertThat(Double.isNaN(result.perQueryValues().get("q1"))).isFalse();
    }

    @Test
    void computesNdcgCorrectlyWhenFewerResultsReturnedThanK() {
        // Hand calculation for k = 5, but backend returns only 2 results:
        // Judgments: d1 (grade 3), d2 (grade 2)
        // Ideal ordering over top k (k = 5):
        //   rank 1 (d1, grade 3): (2^3 - 1) / log2(1 + 1) = 7.0 / 1.0 = 7.0
        //   rank 2 (d2, grade 2): (2^2 - 1) / log2(2 + 1) = 3.0 / log2(3) ≈ 1.892789
        //   ranks 3-5: no remaining positive judgments = 0.0
        // IDCG = 7.0 + 1.89278926 = 8.89278926
        //
        // Returned results (2 items): [d1, d2]
        //   rank 1 (d1, grade 3): (2^3 - 1) / log2(1 + 1) = 7.0 / 1.0 = 7.0
        //   rank 2 (d2, grade 2): (2^2 - 1) / log2(2 + 1) = 3.0 / log2(3) ≈ 1.892789
        //   ranks 3-5: no returned results = 0.0
        // DCG = 7.0 + 1.89278926 = 8.89278926
        //
        // Expected NDCG = DCG / IDCG = 8.89278926 / 8.89278926 = 1.0
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 3),
                new Judgment("q1", "d2", 2)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1", "d2")
        ));

        Metric metric = new NdcgAtK(5);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.overallValue()).isEqualTo(1.0);
        assertThat(result.perQueryValues()).containsEntry("q1", 1.0);
    }

    @Test
    void populatesPerQueryValuesForEveryQueryInJudgmentSet() {
        // Hand calculation:
        // q1: judgment d1 (grade 3), returned [d1] -> NDCG = 1.0
        // q2: judgment d2 (grade 2), returned [unjudged] -> NDCG = 0.0
        // Mean overall value = (1.0 + 0.0) / 2 = 0.5
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "d1", 3),
                new Judgment("q2", "d2", 2)
        ));

        SearchBackend backend = new InMemorySearchBackend(Map.of(
                "q1", List.of("d1"),
                "q2", List.of("otherDoc")
        ));

        Metric metric = new NdcgAtK(1);
        MetricResult result = metric.evaluate(judgments, backend, defaultContext);

        assertThat(result.perQueryValues())
                .containsOnlyKeys("q1", "q2")
                .containsEntry("q1", 1.0)
                .containsEntry("q2", 0.0);

        assertThat(result.overallValue()).isEqualTo(0.5);
    }
}
