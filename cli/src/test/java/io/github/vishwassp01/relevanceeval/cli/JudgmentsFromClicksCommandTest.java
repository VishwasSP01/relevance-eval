package io.github.vishwassp01.relevanceeval.cli;

import io.github.vishwassp01.relevanceeval.io.JudgmentSetLoader;
import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentsFromClicksCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A valid CSV produces a judgment file that JudgmentSetLoader loads back without error (round-trip)")
    void validCsvProducesJudgmentFileLoadableByJudgmentSetLoader() throws Exception {
        Path csvFile = tempDir.resolve("clicks.csv");
        Path outputFile = tempDir.resolve("judgments.yaml");

        StringBuilder csvContent = new StringBuilder("query,documentId,position,clicked\n");
        // docA: 20 impressions at rank 1, 2 clicks -> rate 0.10 -> grade 1
        for (int i = 0; i < 20; i++) {
            csvContent.append("running shoes,SKU-A,1,").append(i < 2).append("\n");
        }
        // docB: 20 impressions at rank 10, 2 clicks -> rate 1.00 -> grade 3
        for (int i = 0; i < 20; i++) {
            csvContent.append("running shoes,SKU-B,10,").append(i < 2).append("\n");
        }
        Files.writeString(csvFile, csvContent.toString());

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "judgments-from-clicks",
                "--clicks", csvFile.toString(),
                "--output", outputFile.toString(),
                "--eta", "1.0",
                "--propensity-floor", "0.1",
                "--min-impressions", "10"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(outputFile)).isTrue();

        // Check provenance comment header
        String yamlContent = Files.readString(outputFile);
        assertThat(yamlContent)
                .contains("# Derived from click logs by relevance-eval.")
                .contains("# These are INFERRED judgments, not human judgments.")
                .contains("# source: " + csvFile)
                .contains("# eta: 1.0   propensity floor: 0.1   min impressions: 10")
                .contains("# generated: ");

        // Round-trip load using JudgmentSetLoader
        JudgmentSetLoader loader = new JudgmentSetLoader();
        JudgmentSet judgmentSet = loader.load(outputFile);

        assertThat(judgmentSet).isNotNull();
        assertThat(judgmentSet.queries()).containsExactly("running shoes");

        List<Judgment> judgments = judgmentSet.judgmentsFor("running shoes");
        assertThat(judgments).hasSize(2);

        Judgment judgmentA = judgments.stream().filter(j -> j.docId().equals("SKU-A")).findFirst().orElseThrow();
        Judgment judgmentB = judgments.stream().filter(j -> j.docId().equals("SKU-B")).findFirst().orElseThrow();

        assertThat(judgmentA.grade()).isEqualTo(1);
        assertThat(judgmentB.grade()).isEqualTo(3);
    }

    @Test
    @DisplayName("The console summary counts match the input")
    void summaryCountsMatchInput() throws Exception {
        Path csvFile = tempDir.resolve("clicks-summary.csv");
        Path outputFile = tempDir.resolve("output-summary.yaml");

        StringBuilder csvContent = new StringBuilder("query,documentId,position,clicked\n");
        // docA: 10 impressions at rank 1, 0 clicks -> grade 0
        for (int i = 0; i < 10; i++) {
            csvContent.append("coffee grinder,SKU-1,1,false\n");
        }
        // docB: 10 impressions at rank 1, 2 clicks -> (1/10)*2 = 0.20 -> grade 1
        for (int i = 0; i < 10; i++) {
            csvContent.append("coffee grinder,SKU-2,1,").append(i < 2).append("\n");
        }
        // docC: 10 impressions at rank 2, 2 clicks -> (1/10)*2*2 = 0.40 -> grade 2
        for (int i = 0; i < 10; i++) {
            csvContent.append("coffee grinder,SKU-3,2,").append(i < 2).append("\n");
        }
        // docD: 10 impressions at rank 10, 1 click -> (1/10)*1*10 = 1.00 -> grade 3
        for (int i = 0; i < 10; i++) {
            csvContent.append("coffee grinder,SKU-4,10,").append(i < 1).append("\n");
        }
        // docE: 5 impressions at rank 5 -> dropped (5 < 10)
        for (int i = 0; i < 5; i++) {
            csvContent.append("coffee grinder,SKU-5,5,true\n");
        }
        Files.writeString(csvFile, csvContent.toString());

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                    "judgments-from-clicks",
                    "--clicks", csvFile.toString(),
                    "--output", outputFile.toString(),
                    "--min-impressions", "10"
            );
            assertThat(exitCode).isEqualTo(0);
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        // Total rows = 10 + 10 + 10 + 10 + 5 = 45 events
        // Pairs found = 5
        // Pairs dropped = 1 (SKU-5 has 5 impressions)
        // Surviving pairs = 4: grade 3: 1, grade 2: 1, grade 1: 1, grade 0: 1
        assertThat(output).contains("Click events read: 45");
        assertThat(output).contains("(query, document) pairs found: 5");
        assertThat(output).contains("Pairs dropped for being below min impressions: 1");
        assertThat(output).contains("Grade distribution: grade 3: 1, grade 2: 1, grade 1: 1, grade 0: 1");
    }

    @Test
    @DisplayName("A malformed row produces an error naming the line number")
    void malformedRowProducesErrorNamingLineNumber() throws Exception {
        // Case 1: invalid position on line 4
        Path csvPos = tempDir.resolve("bad-position.csv");
        Files.writeString(csvPos,
                "query,documentId,position,clicked\n" +
                "running shoes,SKU-1,1,true\n" +
                "running shoes,SKU-2,2,false\n" +
                "running shoes,SKU-3,0,true\n" // Line 4: position 0
        );

        ByteArrayOutputStream errContent1 = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent1));
            int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                    "judgments-from-clicks",
                    "--clicks", csvPos.toString(),
                    "--output", tempDir.resolve("out.yaml").toString()
            );
            assertThat(exitCode).isEqualTo(1);
        } finally {
            System.setErr(originalErr);
        }

        assertThat(errContent1.toString())
                .contains("Line 4")
                .contains("position");

        // Case 2: invalid clicked value on line 3
        Path csvClicked = tempDir.resolve("bad-clicked.csv");
        Files.writeString(csvClicked,
                "query,documentId,position,clicked\n" +
                "running shoes,SKU-1,1,true\n" +
                "running shoes,SKU-2,2,maybe\n" // Line 3: invalid clicked
        );

        ByteArrayOutputStream errContent2 = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errContent2));
            int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                    "judgments-from-clicks",
                    "--clicks", csvClicked.toString(),
                    "--output", tempDir.resolve("out.yaml").toString()
            );
            assertThat(exitCode).isEqualTo(1);
        } finally {
            System.setErr(originalErr);
        }

        assertThat(errContent2.toString())
                .contains("Line 3")
                .contains("clicked");
    }

    @Test
    @DisplayName("A missing or incorrect header is rejected naming line 1")
    void missingHeaderIsRejectedNamingLine1() throws Exception {
        Path csvNoHeader = tempDir.resolve("no-header.csv");
        Files.writeString(csvNoHeader,
                "running shoes,SKU-1,1,true\n" +
                "running shoes,SKU-2,2,false\n"
        );

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                    "judgments-from-clicks",
                    "--clicks", csvNoHeader.toString(),
                    "--output", tempDir.resolve("out.yaml").toString()
            );
            assertThat(exitCode).isEqualTo(1);
        } finally {
            System.setErr(originalErr);
        }

        assertThat(errContent.toString())
                .contains("Line 1")
                .contains("header");
    }
}
