package io.github.vishwassp01.relevanceeval.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vishwassp01.relevanceeval.diff.ComparisonResult;
import io.github.vishwassp01.relevanceeval.diff.QueryDelta;
import io.github.vishwassp01.relevanceeval.diff.RunComparator;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.stats.PairedRandomizationTest;
import io.github.vishwassp01.relevanceeval.stats.SignificanceResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI subcommand to compare two evaluation runs (baseline vs candidate),
 * printing overall changes, statistical significance, top regressed queries, and top improved queries.
 */
@Command(
        name = "compare",
        mixinStandardHelpOptions = true,
        description = "Compares two evaluation run results, detecting regressions, improvements, and statistical significance."
)
public class CompareCommand implements Callable<Integer> {

    @Option(
            names = {"--baseline"},
            required = true,
            description = "Path to the baseline run JSON file."
    )
    private Path baselinePath;

    @Option(
            names = {"--candidate"},
            required = true,
            description = "Path to the candidate run JSON file."
    )
    private Path candidatePath;

    @Option(
            names = {"--threshold"},
            defaultValue = "0.1",
            description = "Regression threshold above which the command exits with code 1 (default: 0.1)."
    )
    private double threshold = 0.1;

    @Option(
            names = {"--significance-level"},
            defaultValue = "0.05",
            description = "Significance level (alpha) for statistical testing (default: 0.05)."
    )
    private double significanceLevel = 0.05;

    @Option(
            names = {"--trials"},
            defaultValue = "10000",
            description = "Number of trials for paired randomization test (default: 10000)."
    )
    private int trials = 10000;

    @Option(
            names = {"--fail-on-significant-regression"},
            description = "Exit with code 1 if the overall delta is negative and statistically significant."
    )
    private boolean failOnSignificantRegression;

    @Option(
            names = {"--junit-xml"},
            description = "Path to write the comparison results as a JUnit XML report."
    )
    private Path junitXmlPath;

    @Option(
            names = {"--html"},
            description = "Path to write the comparison results as a self-contained HTML report."
    )
    private Path htmlPath;

    @Option(
            names = {"--judged-warning-threshold"},
            defaultValue = "0.7",
            description = "Threshold below which a judged@k warning is shown in the HTML report (default: 0.7)."
    )
    private double judgedWarningThreshold = 0.7;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CompareCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        List<MetricResult> baselineRuns;
        List<MetricResult> candidateRuns;

        try {
            baselineRuns = loadRunFile(baselinePath);
        } catch (Exception e) {
            System.err.println("Failed to read baseline run file: " + e.getMessage());
            return 1;
        }

        try {
            candidateRuns = loadRunFile(candidatePath);
        } catch (Exception e) {
            System.err.println("Failed to read candidate run file: " + e.getMessage());
            return 1;
        }

        Map<String, MetricResult> candidateMap = candidateRuns.stream()
                .collect(Collectors.toMap(m -> m.metricName().toLowerCase(), m -> m, (a, b) -> a));

        RunComparator comparator = new RunComparator(trials, PairedRandomizationTest.DEFAULT_SEED);
        List<ComparisonResult> comparisons = new ArrayList<>();

        for (MetricResult baseMetric : baselineRuns) {
            MetricResult candMetric = candidateMap.get(baseMetric.metricName().toLowerCase());
            if (candMetric != null) {
                comparisons.add(comparator.compare(baseMetric, candMetric));
            } else {
                System.err.println("Notice: Metric '" + baseMetric.metricName() + "' was not found in candidate run.");
            }
        }

        if (comparisons.isEmpty()) {
            System.err.println("Error: No matching metrics found between baseline and candidate runs.");
            return 1;
        }

        boolean regressionExceeded = false;
        boolean significantRegression = false;

        for (ComparisonResult comparison : comparisons) {
            printComparisonTable(comparison);

            // Check if any query regressed by more than the threshold
            for (QueryDelta regressed : comparison.regressed()) {
                if (Math.abs(regressed.delta()) > threshold) {
                    regressionExceeded = true;
                }
            }

            if (failOnSignificantRegression && comparison.significance() != null) {
                if (comparison.overallDelta() < 0.0 && comparison.significance().pValue() <= significanceLevel) {
                    significantRegression = true;
                }
            }
        }

        if (junitXmlPath != null) {
            try {
                JUnitXmlReportWriter.writeReport(comparisons, threshold, junitXmlPath);
            } catch (Exception e) {
                System.err.println("Failed to write JUnit XML report to '" + junitXmlPath + "': " + e.getMessage());
                return 1;
            }
        }

        if (htmlPath != null) {
            try {
                HtmlReportWriter.writeReport(
                        baselinePath,
                        candidatePath,
                        comparisons,
                        baselineRuns,
                        candidateRuns,
                        significanceLevel,
                        judgedWarningThreshold,
                        htmlPath
                );
            } catch (Exception e) {
                System.err.println("Failed to write HTML report to '" + htmlPath + "': " + e.getMessage());
                return 1;
            }
        }

        if (regressionExceeded) {
            System.err.printf("%nFAILURE: One or more queries regressed by more than threshold %.4f%n", threshold);
        }
        if (significantRegression) {
            System.err.printf("%nFAILURE: Statistically significant overall regression detected (p <= %s)%n", formatAlpha(significanceLevel));
        }

        if (regressionExceeded || significantRegression) {
            return 1;
        }

        return 0;
    }

    static List<MetricResult> loadRunFile(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(path.toFile());

        if (root.isArray()) {
            List<MetricResult> list = new ArrayList<>();
            for (JsonNode element : root) {
                list.add(mapper.treeToValue(element, MetricResult.class));
            }
            return list;
        } else {
            return List.of(mapper.treeToValue(root, MetricResult.class));
        }
    }

    private void printComparisonTable(ComparisonResult comparison) {
        System.out.println("================================================================================");
        System.out.printf("Metric Comparison: %s%n", comparison.metricName());
        System.out.println("================================================================================");
        System.out.printf("Baseline Overall:  %10.4f%n", comparison.baselineOverall());
        System.out.printf("Candidate Overall: %10.4f%n", comparison.candidateOverall());
        System.out.printf("Overall Delta:     %+10.4f%n%n", comparison.overallDelta());

        SignificanceResult sig = comparison.significance();
        if (sig != null) {
            System.out.printf("Significance:        p = %.4f  (n = %d queries, %d trials)%n",
                    sig.pValue(), sig.sampleSize(), sig.trials());
            if (sig.pValue() <= significanceLevel) {
                System.out.printf("                     Significant at alpha = %s%n%n", formatAlpha(significanceLevel));
            } else {
                System.out.printf("                     Not significant \u2014 this change is within noise%n%n");
            }
        }

        // Top regressed
        System.out.println("Top Regressed Queries (worst-first):");
        System.out.println("--------------------------------------------------------------------------------");
        if (comparison.regressed().isEmpty()) {
            System.out.println("  (None)");
        } else {
            printDeltaSection(comparison.regressed());
        }
        System.out.println();

        // Top improved
        System.out.println("Top Improved Queries (best-first):");
        System.out.println("--------------------------------------------------------------------------------");
        if (comparison.improved().isEmpty()) {
            System.out.println("  (None)");
        } else {
            printDeltaSection(comparison.improved());
        }

        // Only in one run (if present)
        if (!comparison.onlyInOne().isEmpty()) {
            System.out.println();
            System.out.println("Queries present in only one run:");
            System.out.println("--------------------------------------------------------------------------------");
            for (QueryDelta q : comparison.onlyInOne()) {
                if (Double.isNaN(q.baselineValue())) {
                    System.out.printf("  %s (Candidate only: %.4f)%n", q.query(), q.candidateValue());
                } else {
                    System.out.printf("  %s (Baseline only: %.4f)%n", q.query(), q.baselineValue());
                }
            }
        }

        System.out.println("================================================================================");
    }

    private void printDeltaSection(List<QueryDelta> deltas) {
        int maxQueryLen = Math.max("Query".length(), deltas.stream().mapToInt(d -> d.query().length()).max().orElse(10));
        String formatHeader = "%-" + (maxQueryLen + 2) + "s %12s %12s %12s%n";
        String formatRow = "%-" + (maxQueryLen + 2) + "s %12.4f %12.4f %+12.4f%n";

        System.out.printf(formatHeader, "Query", "Baseline", "Candidate", "Delta");
        System.out.println("-".repeat(Math.max(45, maxQueryLen + 40)));

        for (QueryDelta delta : deltas) {
            System.out.printf(formatRow, delta.query(), delta.baselineValue(), delta.candidateValue(), delta.delta());
        }
    }

    private static String formatAlpha(double alpha) {
        if (alpha == (long) alpha) {
            return String.format("%d", (long) alpha);
        }
        return String.valueOf(alpha);
    }
}
