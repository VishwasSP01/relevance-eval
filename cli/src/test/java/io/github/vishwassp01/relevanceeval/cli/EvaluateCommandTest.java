package io.github.vishwassp01.relevanceeval.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluateCommandTest {

    @Test
    void runsSuccessfullyWithValidJudgmentFileAndReturnsExitCodeZero() {
        Path judgmentFile = resolveTestResource("sample-judgments.yaml");

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "evaluate",
                "--judgments", judgmentFile.toString(),
                "--metrics", "ndcg@10,precision@10",
                "--size", "10",
                "--demo"
        );

        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void runsSuccessfullyWithRecallAndMrrMetrics() {
        Path judgmentFile = resolveTestResource("sample-judgments.yaml");

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "evaluate",
                "--judgments", judgmentFile.toString(),
                "--metrics", "recall@10,mrr",
                "--size", "10",
                "--demo"
        );

        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void runsSuccessfullyWithJudgedAtKMetric() {
        Path judgmentFile = resolveTestResource("sample-judgments.yaml");

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "evaluate",
                "--judgments", judgmentFile.toString(),
                "--metrics", "judged@10",
                "--size", "10",
                "--demo"
        );

        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void printsWarningWhenJudgedIsBelowThreshold() {
        Path judgmentFile = resolveTestResource("sample-judgments.yaml");

        java.io.ByteArrayOutputStream outContent = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(outContent));
            int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                    "evaluate",
                    "--judgments", judgmentFile.toString(),
                    "--metrics", "ndcg@10",
                    "--size", "10",
                    "--demo",
                    "--judged-warning-threshold", "0.7"
            );
            assertThat(exitCode).isEqualTo(0);
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        assertThat(output)
                .contains("WARNING: judged@10 is")
                .contains("of returned results have no judgment.")
                .contains("Scores may be unreliable. Consider expanding the judgment set.");
    }

    @Test
    void suppressesWarningWhenJudgedIsAboveThreshold() {
        Path judgmentFile = resolveTestResource("sample-judgments.yaml");

        java.io.ByteArrayOutputStream outContent = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(outContent));
            int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                    "evaluate",
                    "--judgments", judgmentFile.toString(),
                    "--metrics", "ndcg@10",
                    "--size", "10",
                    "--demo",
                    "--judged-warning-threshold", "0.0"
            );
            assertThat(exitCode).isEqualTo(0);
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        assertThat(output).doesNotContain("WARNING: judged@");
    }

    private Path resolveTestResource(String filename) {
        URL resource = getClass().getClassLoader().getResource(filename);
        if (resource != null) {
            try {
                return Path.of(resource.toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        Path relative = Path.of("src/test/resources", filename);
        if (Files.exists(relative)) {
            return relative;
        }
        return Path.of("cli/src/test/resources", filename);
    }
}
