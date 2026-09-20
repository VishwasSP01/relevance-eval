package io.github.vishwassp01.relevanceeval.diff;

import java.util.List;

/**
 * Encapsulates the differential analysis between two evaluation runs for a given metric.
 * <p>
 * Contains overall aggregate metrics and partitioned query results:
 * <ul>
 *   <li>{@code improved} - queries where the candidate outscored baseline (sorted best-first)</li>
 *   <li>{@code regressed} - queries where the candidate scored lower than baseline (sorted worst-first)</li>
 *   <li>{@code unchanged} - queries whose score delta is within the noise threshold</li>
 *   <li>{@code onlyInOne} - queries present in only one of the two runs</li>
 * </ul>
 *
 * @param metricName       the evaluated metric name
 * @param baselineOverall  the overall macro score in the baseline run
 * @param candidateOverall the overall macro score in the candidate run
 * @param improved         queries with positive delta, sorted descending by delta (best-first)
 * @param regressed        queries with negative delta, sorted ascending by delta (worst-first)
 * @param unchanged        queries with no significant change (|delta| &lt; 1e-9)
 * @param onlyInOne        queries present in one run but not the other
 */
public record ComparisonResult(
        String metricName,
        double baselineOverall,
        double candidateOverall,
        List<QueryDelta> improved,
        List<QueryDelta> regressed,
        List<QueryDelta> unchanged,
        List<QueryDelta> onlyInOne
) {

    public ComparisonResult {
        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("metricName must not be null or blank");
        }
        improved = List.copyOf(improved);
        regressed = List.copyOf(regressed);
        unchanged = List.copyOf(unchanged);
        onlyInOne = List.copyOf(onlyInOne);
    }

    /**
     * Calculates the overall change in the macro metric score (candidate - baseline).
     *
     * @return the overall delta
     */
    public double overallDelta() {
        return candidateOverall - baselineOverall;
    }
}
