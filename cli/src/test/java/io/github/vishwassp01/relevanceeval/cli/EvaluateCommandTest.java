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
