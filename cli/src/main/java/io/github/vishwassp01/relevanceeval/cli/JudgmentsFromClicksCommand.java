package io.github.vishwassp01.relevanceeval.cli;

import io.github.vishwassp01.relevanceeval.clicks.ClickEvent;
import io.github.vishwassp01.relevanceeval.clicks.ClickJudgmentBuilder;
import io.github.vishwassp01.relevanceeval.clicks.ClickLogParseException;
import io.github.vishwassp01.relevanceeval.clicks.ClickLogReader;
import io.github.vishwassp01.relevanceeval.clicks.PositionBasedPropensity;
import io.github.vishwassp01.relevanceeval.clicks.QueryDocPair;
import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI subcommand that derives ground-truth relevance judgments from search click logs
 * with position-bias correction and writes a YAML judgment file.
 */
@Command(
        name = "judgments-from-clicks",
        mixinStandardHelpOptions = true,
        description = "Derives a judgment file from search click logs with position-bias correction."
)
public class JudgmentsFromClicksCommand implements Callable<Integer> {

    @Option(
            names = {"--clicks"},
            required = true,
            description = "Path to the CSV clicks input file."
    )
    private Path clicksPath;

    @Option(
            names = {"--output"},
            required = true,
            description = "Path to the output YAML judgment file."
    )
    private Path outputPath;

    @Option(
            names = {"--eta"},
            defaultValue = "1.0",
            description = "Power-law decay exponent for position propensity (default: 1.0)."
    )
    private double eta = 1.0;

    @Option(
            names = {"--propensity-floor"},
            defaultValue = "0.1",
            description = "Propensity clipping floor to cap variance (default: 0.1)."
    )
    private double propensityFloor = 0.1;

    @Option(
            names = {"--min-impressions"},
            defaultValue = "10",
            description = "Minimum raw impressions required to keep a pair (default: 10)."
    )
    private int minImpressions = 10;

    @Override
    public Integer call() {
        List<ClickEvent> events;
        try {
            events = ClickLogReader.read(clicksPath);
        } catch (ClickLogParseException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error reading click log: " + e.getMessage());
            return 1;
        }

        ClickJudgmentBuilder builder;
        try {
            builder = new ClickJudgmentBuilder(
                    new PositionBasedPropensity(eta),
                    minImpressions,
                    propensityFloor
            );
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid configuration: " + e.getMessage());
            return 1;
        }

        JudgmentSet judgmentSet = builder.build(events);

        // Group events to compute summary stats
        Map<QueryDocPair, List<ClickEvent>> grouped = new LinkedHashMap<>();
        for (ClickEvent event : events) {
            grouped.computeIfAbsent(new QueryDocPair(event.query(), event.documentId()), k -> new ArrayList<>())
                    .add(event);
        }

        int totalEvents = events.size();
        int totalPairs = grouped.size();
        int droppedPairs = 0;
        for (List<ClickEvent> pairEvents : grouped.values()) {
            if (pairEvents.size() < minImpressions) {
                droppedPairs++;
            }
        }

        long grade3 = judgmentSet.judgments().stream().filter(j -> j.grade() == 3).count();
        long grade2 = judgmentSet.judgments().stream().filter(j -> j.grade() == 2).count();
        long grade1 = judgmentSet.judgments().stream().filter(j -> j.grade() == 1).count();
        long grade0 = judgmentSet.judgments().stream().filter(j -> j.grade() == 0).count();

        try {
            writeJudgmentFile(judgmentSet);
        } catch (IOException e) {
            System.err.println("Error writing judgment file: " + e.getMessage());
            return 1;
        }

        // Print console summary
        System.out.println("Click events read: " + totalEvents);
        System.out.println("(query, document) pairs found: " + totalPairs);
        System.out.println("Pairs dropped for being below min impressions: " + droppedPairs);
        System.out.printf("Grade distribution: grade 3: %d, grade 2: %d, grade 1: %d, grade 0: %d%n",
                grade3, grade2, grade1, grade0);

        return 0;
    }

    private void writeJudgmentFile(JudgmentSet judgmentSet) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Derived from click logs by relevance-eval.\n");
        sb.append("# These are INFERRED judgments, not human judgments.\n");
        sb.append("# source: ").append(clicksPath).append("\n");
        sb.append(String.format(Locale.US, "# eta: %s   propensity floor: %s   min impressions: %d\n",
                eta, propensityFloor, minImpressions));
        sb.append("# generated: ").append(Instant.now().truncatedTo(ChronoUnit.SECONDS)).append("\n\n");

        sb.append("name: click-derived-judgments\n");
        sb.append("queries:\n");
        for (String query : judgmentSet.queries()) {
            sb.append("  - query: \"").append(escapeYaml(query)).append("\"\n");
            sb.append("    judgments:\n");
            for (Judgment j : judgmentSet.judgmentsFor(query)) {
                sb.append("      - { id: \"").append(escapeYaml(j.docId()))
                        .append("\", grade: ").append(j.grade()).append(" }\n");
            }
        }

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String escapeYaml(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
