package io.github.vishwassp01.relevanceeval.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompareCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsZeroWhenNoQueriesRegressBeyondThreshold() throws IOException {
        Path baselinePath = tempDir.resolve("baseline.json");
        Path candidatePath = tempDir.resolve("candidate.json");

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Baseline: query1 = 0.80, query2 = 0.50
        MetricResult baseline = new MetricResult("NDCG@10", 0.65, Map.of(
                "query1", 0.80,
                "query2", 0.50
        ));
        mapper.writeValue(baselinePath.toFile(), baseline);

        // Candidate: query1 = 0.75 (mild regression: -0.05, within default 0.1 threshold), query2 = 0.70 (+0.20)
        MetricResult candidate = new MetricResult("NDCG@10", 0.725, Map.of(
                "query1", 0.75,
                "query2", 0.70
        ));
        mapper.writeValue(candidatePath.toFile(), candidate);

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "0.1"
        );

        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void returnsOneWhenAQueryRegressesBeyondThreshold() throws IOException {
        Path baselinePath = tempDir.resolve("baseline.json");
        Path candidatePath = tempDir.resolve("candidate.json");

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Baseline: query1 = 0.80
        MetricResult baseline = new MetricResult("NDCG@10", 0.80, Map.of(
                "query1", 0.80
        ));
        mapper.writeValue(baselinePath.toFile(), baseline);

        // Candidate: query1 = 0.50 (severe regression: -0.30, exceeds 0.1 threshold)
        MetricResult candidate = new MetricResult("NDCG@10", 0.50, Map.of(
                "query1", 0.50
        ));
        mapper.writeValue(candidatePath.toFile(), candidate);

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "0.1"
        );

        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void evaluateCanOutputJsonForCompare() throws IOException {
        Path judgmentFile = resolveTestResource("sample-judgments.yaml");
        Path outputPath = tempDir.resolve("eval-run.json");

        // Run evaluate with --output
        int evalExit = new CommandLine(new RelevanceEvalCommand()).execute(
                "evaluate",
                "--judgments", judgmentFile.toString(),
                "--metrics", "ndcg@10",
                "--demo",
                "--output", outputPath.toString()
        );
        assertThat(evalExit).isEqualTo(0);
        assertThat(outputPath).exists();

        // Run compare comparing run to itself (delta 0, exit 0)
        int compareExit = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", outputPath.toString(),
                "--candidate", outputPath.toString()
        );
        assertThat(compareExit).isEqualTo(0);
    }

    private Path resolveTestResource(String filename) {
        Path direct = Path.of("src/test/resources", filename);
        if (java.nio.file.Files.exists(direct)) {
            return direct;
        }
        return Path.of("cli/src/test/resources", filename);
    }
}
