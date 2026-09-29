package io.github.vishwassp01.relevanceeval.diff;

import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.stats.PairedRandomizationTest;
import io.github.vishwassp01.relevanceeval.stats.SignificanceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares two evaluation runs for a metric, isolating queries that improved, regressed,
 * or remained unchanged, highlighting queries that appear only in one run, and
 * computing statistical significance via a paired randomization test.
 */
public class RunComparator {

    public static final double EPSILON = 1e-9;

    private final PairedRandomizationTest randomizationTest;
    private final int trials;
    private final long seed;

    public RunComparator() {
        this(new PairedRandomizationTest(), PairedRandomizationTest.DEFAULT_TRIALS, PairedRandomizationTest.DEFAULT_SEED);
    }

    public RunComparator(int trials, long seed) {
        this(new PairedRandomizationTest(), trials, seed);
    }

    public RunComparator(PairedRandomizationTest randomizationTest, int trials, long seed) {
        this.randomizationTest = Objects.requireNonNull(randomizationTest, "randomizationTest must not be null");
        this.trials = trials;
        this.seed = seed;
    }

    /**
     * Compares baseline and candidate {@link MetricResult} instances using configured trials and seed.
     *
     * @param baseline  the baseline evaluation result, must not be null
     * @param candidate the candidate evaluation result, must not be null
     * @return a {@link ComparisonResult} detailing overall changes, per-query deltas, and statistical significance
     */
    public ComparisonResult compare(MetricResult baseline, MetricResult candidate) {
        return compare(baseline, candidate, this.trials, this.seed);
    }

    /**
     * Compares baseline and candidate {@link MetricResult} instances with explicit trials and seed.
     *
     * @param baseline  the baseline evaluation result, must not be null
     * @param candidate the candidate evaluation result, must not be null
     * @param trials    the number of randomization trials for statistical significance
     * @param seed      the seed for reproducibility
     * @return a {@link ComparisonResult} detailing overall changes, per-query deltas, and statistical significance
     */
    public ComparisonResult compare(MetricResult baseline, MetricResult candidate, int trials, long seed) {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");

        if (!baseline.metricName().equalsIgnoreCase(candidate.metricName())) {
            throw new IllegalArgumentException(String.format(
                    "Cannot compare different metrics: '%s' vs '%s'",
                    baseline.metricName(), candidate.metricName()
            ));
        }

        Map<String, Double> baseMap = baseline.perQueryValues();
        Map<String, Double> candMap = candidate.perQueryValues();

        Set<String> allQueries = new TreeSet<>(baseMap.keySet());
        allQueries.addAll(candMap.keySet());

        List<QueryDelta> improved = new ArrayList<>();
        List<QueryDelta> regressed = new ArrayList<>();
        List<QueryDelta> unchanged = new ArrayList<>();
        List<QueryDelta> onlyInOne = new ArrayList<>();

        for (String query : allQueries) {
            boolean inBase = baseMap.containsKey(query);
            boolean inCand = candMap.containsKey(query);

            if (inBase && inCand) {
                double baseVal = baseMap.get(query);
                double candVal = candMap.get(query);
                double delta = candVal - baseVal;
                QueryDelta qDelta = new QueryDelta(query, baseVal, candVal);

                if (Math.abs(delta) < EPSILON) {
                    unchanged.add(qDelta);
                } else if (delta > 0.0) {
                    improved.add(qDelta);
                } else {
                    regressed.add(qDelta);
                }
            } else if (inBase) {
                onlyInOne.add(new QueryDelta(query, baseMap.get(query), Double.NaN));
            } else {
                onlyInOne.add(new QueryDelta(query, Double.NaN, candMap.get(query)));
            }
        }

        // Improved sorted best-first (descending delta, largest positive first)
        improved.sort(Comparator.comparingDouble(QueryDelta::delta).reversed());

        // Regressed sorted worst-first (ascending delta, most negative first)
        regressed.sort(Comparator.comparingDouble(QueryDelta::delta));

        // Deterministic ordering for unchanged and onlyInOne
        unchanged.sort(Comparator.comparing(QueryDelta::query));
        onlyInOne.sort(Comparator.comparing(QueryDelta::query));

        // Statistical significance using paired randomization test
        SignificanceResult significance = randomizationTest.test(baseMap, candMap, trials, seed);

        return new ComparisonResult(
                baseline.metricName(),
                baseline.overallValue(),
                candidate.overallValue(),
                improved,
                regressed,
                unchanged,
                onlyInOne,
                significance
        );
    }
}
