package io.github.vishwassp01.relevanceeval.cli;

import io.github.vishwassp01.relevanceeval.backend.InMemorySearchBackend;
import io.github.vishwassp01.relevanceeval.backend.SearchBackend;
import io.github.vishwassp01.relevanceeval.io.JudgmentSetException;
import io.github.vishwassp01.relevanceeval.io.JudgmentSetLoader;
import io.github.vishwassp01.relevanceeval.metrics.MeanReciprocalRank;
import io.github.vishwassp01.relevanceeval.metrics.Metric;
import io.github.vishwassp01.relevanceeval.metrics.NdcgAtK;
import io.github.vishwassp01.relevanceeval.metrics.PrecisionAtK;
import io.github.vishwassp01.relevanceeval.metrics.RecallAtK;
import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Command-line entry point to evaluate search relevance against ground-truth judgment sets.
 */
@Command(
        name = "evaluate",
        mixinStandardHelpOptions = true,
        description = "Evaluates search relevance metrics for queries in a judgment set."
)
public class EvaluateCommand implements Callable<Integer> {

    @Option(
            names = {"--judgments"},
            required = true,
            description = "Path to the YAML judgment file."
    )
    private Path judgmentsPath;

    @Option(
            names = {"--metrics"},
            defaultValue = "ndcg@10,precision@10",
            split = ",",
            description = "Comma-separated metric specs (default: ndcg@10,precision@10)."
    )
    private List<String> metricSpecs = List.of("ndcg@10", "precision@10");

    @Option(
            names = {"--size"},
            defaultValue = "10",
            description = "How many results to request from the backend (default: 10)."
    )
    private int size = 10;

    @Option(
            names = {"--output"},
            description = "Path to write the evaluation results as JSON for later comparison."
    )
    private Path outputPath;

    @Option(
            names = {"--demo"},
            description = "Runs evaluation against an in-memory simulation backend with demo data."
    )
    private boolean demo = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new EvaluateCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // 1. Parse metric specifications
        List<Metric> metrics = new ArrayList<>();
        for (String spec : metricSpecs) {
            try {
                metrics.add(parseMetricSpec(spec));
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        // 2. Load the judgment set
        JudgmentSet judgmentSet;
        try {
            judgmentSet = new JudgmentSetLoader().load(judgmentsPath);
        } catch (JudgmentSetException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        // 3. Resolve search backend
        SearchBackend backend;
        if (demo) {
            backend = createDemoBackend(judgmentSet);
        } else {
            System.err.println("Error: No search backend configured. Use --demo to evaluate against the in-memory demo backend.");
            return 1;
        }

        // 4. Run evaluation
        SearchContext context = new SearchContext("eval-index", size, Map.of());
        List<MetricResult> results = new ArrayList<>();
        for (Metric metric : metrics) {
            results.add(metric.evaluate(judgmentSet, backend, context));
        }

        // 5. Print results table
        printResultsTable(judgmentSet, backend, results);

        // 6. Optionally save output to JSON file
        if (outputPath != null) {
            try {
                ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                Object toWrite = (results.size() == 1) ? results.get(0) : results;
                mapper.writeValue(outputPath.toFile(), toWrite);
            } catch (Exception e) {
                System.err.println("Failed to write output JSON to '" + outputPath + "': " + e.getMessage());
                return 1;
            }
        }

        return 0;
    }

    private Metric parseMetricSpec(String spec) {
        String trimmed = spec.trim().toLowerCase();
        if (trimmed.equals("mrr")) {
            return new MeanReciprocalRank();
        }

        int atIndex = trimmed.indexOf('@');
        if (atIndex == -1) {
            throw new IllegalArgumentException("Invalid metric spec: '" + spec + "'. Expected format: <name>@<k> (e.g. ndcg@10, precision@10, recall@10) or 'mrr'");
        }
        String name = trimmed.substring(0, atIndex);
        String kStr = trimmed.substring(atIndex + 1);

        int k;
        try {
            k = Integer.parseInt(kStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid cutoff k in metric spec '" + spec + "': must be an integer.");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("Cutoff k in metric spec '" + spec + "' must be positive (> 0).");
        }

        return switch (name) {
            case "ndcg" -> new NdcgAtK(k);
            case "precision", "p" -> new PrecisionAtK(k);
            case "recall", "r" -> new RecallAtK(k);
            case "mrr" -> new MeanReciprocalRank();
            default -> throw new IllegalArgumentException("Unsupported metric '" + name + "' in spec '" + spec + "'. Supported: ndcg, precision, recall, mrr");
        };
    }

    private SearchBackend createDemoBackend(JudgmentSet judgmentSet) {
        Map<String, List<String>> queryToDocIds = new HashMap<>();
        for (String query : judgmentSet.queries()) {
            List<Judgment> judgments = judgmentSet.judgmentsFor(query);
            List<String> docIds = new ArrayList<>();

            if ("waterproof jacket".equalsIgnoreCase(query.trim())) {
                // Return imperfect ranking: place a grade 0 (unjudged) document above a grade 2 document
                // so NDCG comes out strictly between 0 and 1.
                String grade2DocId = judgments.stream()
                        .filter(j -> j.grade() == 2)
                        .map(Judgment::docId)
                        .findFirst()
                        .orElse(null);

                for (Judgment j : judgments) {
                    if (j.docId().equals(grade2DocId)) {
                        // Insert unjudged (grade 0) document ahead of grade 2 document
                        docIds.add("DEMO-IRRELEVANT-DOC");
                    }
                    docIds.add(j.docId());
                }
                if (grade2DocId == null) {
                    docIds.add(0, "DEMO-IRRELEVANT-DOC");
                }
            } else {
                for (Judgment j : judgments) {
                    docIds.add(j.docId());
                }
            }

            // Fill remaining slots up to requested size with synthetic demo documents
            int fillIndex = 1;
            while (docIds.size() < size) {
                String filler = "DEMO-DOC-" + fillIndex++;
                if (!docIds.contains(filler)) {
                    docIds.add(filler);
                }
            }
            queryToDocIds.put(query, docIds);
        }
        return new InMemorySearchBackend("demo-in-memory", queryToDocIds);
    }

    private void printResultsTable(JudgmentSet judgmentSet, SearchBackend backend, List<MetricResult> results) {
        System.out.println("================================================================================");
        System.out.printf("Evaluation: %s (Backend: %s, Requested Size: %d)%n", judgmentSet.name(), backend.name(), size);
        System.out.println("================================================================================");

        // Overall summary table
        System.out.printf("%-25s %15s%n", "Metric", "Overall Value");
        System.out.println("-----------------------------------------");
        for (MetricResult result : results) {
            System.out.printf("%-25s %15.4f%n", result.metricName(), result.overallValue());
        }
        System.out.println();

        // Per-query breakdown table
        Set<String> queries = judgmentSet.queries();
        int maxQueryLen = Math.max("Query".length(), queries.stream().mapToInt(String::length).max().orElse(10));
        int maxMetricLen = Math.max("Metric".length(), results.stream().mapToInt(r -> r.metricName().length()).max().orElse(10));

        String headerFormat = "%-" + (maxQueryLen + 2) + "s %-" + (maxMetricLen + 2) + "s %15s%n";
        String rowFormat = "%-" + (maxQueryLen + 2) + "s %-" + (maxMetricLen + 2) + "s %15.4f%n";

        System.out.println("Per-Query Breakdown:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf(headerFormat, "Query", "Metric", "Score");
        System.out.println("-".repeat(Math.max(45, maxQueryLen + maxMetricLen + 22)));

        for (String query : queries) {
            for (MetricResult result : results) {
                Double score = result.perQueryValues().getOrDefault(query, 0.0);
                System.out.printf(rowFormat, query, result.metricName(), score);
            }
        }
        System.out.println("================================================================================");
    }
}
