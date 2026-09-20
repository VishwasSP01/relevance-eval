package io.github.vishwassp01.relevanceeval.diff;

import io.github.vishwassp01.relevanceeval.model.MetricResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RunComparatorTest {

    private RunComparator comparator;

    @BeforeEach
    void setUp() {
        comparator = new RunComparator();
    }

    @Test
    void correctlyCategorizesImprovedRegressedAndUnchangedQueries() {
        // Query 1 improved: 0.50 -> 0.80 (+0.30)
        // Query 2 regressed: 0.80 -> 0.40 (-0.40)
        // Query 3 unchanged: 0.60 -> 0.60 (0.00)
        MetricResult baseline = new MetricResult("NDCG@10", 0.633, Map.of(
                "q-improved", 0.50,
                "q-regressed", 0.80,
                "q-unchanged", 0.60
        ));

        MetricResult candidate = new MetricResult("NDCG@10", 0.600, Map.of(
                "q-improved", 0.80,
                "q-regressed", 0.40,
                "q-unchanged", 0.60
        ));

        ComparisonResult result = comparator.compare(baseline, candidate);

        assertThat(result.metricName()).isEqualTo("NDCG@10");
        assertThat(result.baselineOverall()).isEqualTo(0.633);
        assertThat(result.candidateOverall()).isEqualTo(0.600);

        // Improved
        assertThat(result.improved()).hasSize(1);
        QueryDelta improved = result.improved().get(0);
        assertThat(improved.query()).isEqualTo("q-improved");
        assertThat(improved.delta()).isCloseTo(0.30, within(1e-9));

        // Regressed
        assertThat(result.regressed()).hasSize(1);
        QueryDelta regressed = result.regressed().get(0);
        assertThat(regressed.query()).isEqualTo("q-regressed");
        assertThat(regressed.delta()).isCloseTo(-0.40, within(1e-9));

        // Unchanged
        assertThat(result.unchanged()).hasSize(1);
        QueryDelta unchanged = result.unchanged().get(0);
        assertThat(unchanged.query()).isEqualTo("q-unchanged");
        assertThat(unchanged.delta()).isCloseTo(0.00, within(1e-9));

        // Only in one
        assertThat(result.onlyInOne()).isEmpty();
    }

    @Test
    void capturesQueriesPresentOnlyInCandidate() {
        MetricResult baseline = new MetricResult("NDCG@10", 0.50, Map.of(
                "common-query", 0.50
        ));

        MetricResult candidate = new MetricResult("NDCG@10", 0.70, Map.of(
                "common-query", 0.50,
                "only-in-candidate", 0.90
        ));

        ComparisonResult result = comparator.compare(baseline, candidate);

        assertThat(result.unchanged()).hasSize(1);
        assertThat(result.unchanged().get(0).query()).isEqualTo("common-query");

        assertThat(result.improved()).isEmpty();
        assertThat(result.regressed()).isEmpty();

        assertThat(result.onlyInOne()).hasSize(1);
        QueryDelta missingInBaseline = result.onlyInOne().get(0);
        assertThat(missingInBaseline.query()).isEqualTo("only-in-candidate");
        assertThat(Double.isNaN(missingInBaseline.baselineValue())).isTrue();
        assertThat(missingInBaseline.candidateValue()).isEqualTo(0.90);
        assertThat(Double.isNaN(missingInBaseline.delta())).isTrue();
    }

    @Test
    void treatsFloatingPointNoiseBelowEpsilonAsUnchanged() {
        // Delta is 1e-12, well below the 1e-9 epsilon
        double baselineVal = 0.500000000000000;
        double candidateVal = 0.500000000000001;

        MetricResult baseline = new MetricResult("NDCG@10", baselineVal, Map.of(
                "noisy-query", baselineVal
        ));

        MetricResult candidate = new MetricResult("NDCG@10", candidateVal, Map.of(
                "noisy-query", candidateVal
        ));

        ComparisonResult result = comparator.compare(baseline, candidate);

        assertThat(result.improved()).isEmpty();
        assertThat(result.regressed()).isEmpty();
        assertThat(result.unchanged()).hasSize(1);
        assertThat(result.unchanged().get(0).query()).isEqualTo("noisy-query");
    }

    @Test
    void sortsRegressedWorstFirstAndImprovedBestFirst() {
        MetricResult baseline = new MetricResult("NDCG@10", 0.50, Map.of(
                "imp-small", 0.50,
                "imp-big", 0.20,
                "reg-mild", 0.80,
                "reg-severe", 0.90
        ));

        MetricResult candidate = new MetricResult("NDCG@10", 0.50, Map.of(
                "imp-small", 0.60,   // delta = +0.10
                "imp-big", 0.80,     // delta = +0.60
                "reg-mild", 0.70,    // delta = -0.10
                "reg-severe", 0.20   // delta = -0.70
        ));

        ComparisonResult result = comparator.compare(baseline, candidate);

        // Improved sorted best-first (largest positive delta first)
        assertThat(result.improved()).extracting(QueryDelta::query)
                .containsExactly("imp-big", "imp-small");

        // Regressed sorted worst-first (most negative delta first)
        assertThat(result.regressed()).extracting(QueryDelta::query)
                .containsExactly("reg-severe", "reg-mild");
    }
}
