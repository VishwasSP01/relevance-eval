package io.github.vishwassp01.relevanceeval.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import picocli.CommandLine;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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

    @Test
    void writesStandardJunitXmlReportWithRegressedImprovedMissingAndSpecialCharQueries() throws Exception {
        Path baselinePath = tempDir.resolve("baseline.json");
        Path candidatePath = tempDir.resolve("candidate.json");
        Path xmlPath = tempDir.resolve("reports/junit-report.xml");

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // 1. Regressed query: "waterproof jacket" (0.95 -> 0.60, delta -0.35, exceeds threshold 0.1)
        // 2. Improved query: "running shoes" (0.50 -> 0.80, delta +0.30)
        // 3. Missing query: "trail boots" (0.70 in baseline, absent in candidate)
        // 4. Special characters query: "hiking <gear> & \"boots\"" (0.85 -> 0.85, unchanged)
        String specialQuery = "hiking <gear> & \"boots\"";

        MetricResult baseline = new MetricResult("NDCG@10", 0.75, Map.of(
                "waterproof jacket", 0.95,
                "running shoes", 0.50,
                "trail boots", 0.70,
                specialQuery, 0.85
        ));
        mapper.writeValue(baselinePath.toFile(), baseline);

        MetricResult candidate = new MetricResult("NDCG@10", 0.75, Map.of(
                "waterproof jacket", 0.60,
                "running shoes", 0.80,
                specialQuery, 0.85
        ));
        mapper.writeValue(candidatePath.toFile(), candidate);

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "0.1",
                "--junit-xml", xmlPath.toString()
        );

        // A query regressed beyond threshold -> exit code 1
        assertThat(exitCode).isEqualTo(1);
        assertThat(xmlPath).exists();

        // Parse and assert on the generated JUnit XML document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlPath.toFile());

        Element testsuite = doc.getDocumentElement();
        assertThat(testsuite.getNodeName()).isEqualTo("testsuite");
        assertThat(testsuite.getAttribute("name")).isEqualTo("comparison");
        assertThat(testsuite.getAttribute("tests")).isEqualTo("4");
        assertThat(testsuite.getAttribute("failures")).isEqualTo("1");
        assertThat(testsuite.getAttribute("skipped")).isEqualTo("1");
        assertThat(testsuite.getAttribute("errors")).isEqualTo("0");

        NodeList testcases = testsuite.getElementsByTagName("testcase");
        assertThat(testcases.getLength()).isEqualTo(4);

        Map<String, Element> testcaseMap = new HashMap<>();
        for (int i = 0; i < testcases.getLength(); i++) {
            Element tc = (Element) testcases.item(i);
            testcaseMap.put(tc.getAttribute("name"), tc);
        }

        // Assert on regressed testcase (failed)
        Element regressedCase = testcaseMap.get("NDCG@10 / waterproof jacket");
        assertThat(regressedCase).isNotNull();
        NodeList regressedFailures = regressedCase.getElementsByTagName("failure");
        assertThat(regressedFailures.getLength()).isEqualTo(1);
        Element failureElem = (Element) regressedFailures.item(0);
        String failureMsg = failureElem.getAttribute("message");
        assertThat(failureMsg)
                .contains("0.95")
                .contains("0.60")
                .contains("-0.35");

        // Assert on improved testcase (passing)
        Element improvedCase = testcaseMap.get("NDCG@10 / running shoes");
        assertThat(improvedCase).isNotNull();
        assertThat(improvedCase.getElementsByTagName("failure").getLength()).isEqualTo(0);
        assertThat(improvedCase.getElementsByTagName("skipped").getLength()).isEqualTo(0);

        // Assert on missing query testcase (skipped)
        Element missingCase = testcaseMap.get("NDCG@10 / trail boots");
        assertThat(missingCase).isNotNull();
        NodeList missingSkipped = missingCase.getElementsByTagName("skipped");
        assertThat(missingSkipped.getLength()).isEqualTo(1);
        Element skippedElem = (Element) missingSkipped.item(0);
        assertThat(skippedElem.getAttribute("message")).contains("Missing from candidate run");

        // Assert on special characters query testcase (properly escaped in XML, parsed correctly)
        Element specialCase = testcaseMap.get("NDCG@10 / " + specialQuery);
        assertThat(specialCase).isNotNull();
        assertThat(specialCase.getAttribute("name")).isEqualTo("NDCG@10 / " + specialQuery);
        assertThat(specialCase.getElementsByTagName("failure").getLength()).isEqualTo(0);
        assertThat(specialCase.getElementsByTagName("skipped").getLength()).isEqualTo(0);

        // Verify XML escaping in raw file text
        String rawXml = Files.readString(xmlPath);
        assertThat(rawXml)
                .contains("&lt;gear&gt;")
                .contains("&amp;")
                .contains("&quot;boots&quot;");
    }

    private Path resolveTestResource(String filename) {
        Path direct = Path.of("src/test/resources", filename);
        if (java.nio.file.Files.exists(direct)) {
            return direct;
        }
        return Path.of("cli/src/test/resources", filename);
    }

    @Test
    void printsSignificanceInformationInConsoleOutput() throws IOException {
        Path baselinePath = tempDir.resolve("sig-baseline.json");
        Path candidatePath = tempDir.resolve("sig-candidate.json");

        ObjectMapper mapper = new ObjectMapper();
        MetricResult baseline = new MetricResult("NDCG@10", 0.65, Map.of(
                "q1", 0.80,
                "q2", 0.50
        ));
        mapper.writeValue(baselinePath.toFile(), baseline);

        MetricResult candidate = new MetricResult("NDCG@10", 0.65, Map.of(
                "q1", 0.80,
                "q2", 0.50
        ));
        mapper.writeValue(candidatePath.toFile(), candidate);

        java.io.ByteArrayOutputStream outContent = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(outContent));
            int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                    "compare",
                    "--baseline", baselinePath.toString(),
                    "--candidate", candidatePath.toString(),
                    "--significance-level", "0.05",
                    "--trials", "2000"
            );
            assertThat(exitCode).isEqualTo(0);
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        assertThat(output)
                .contains("Significance:        p = 1.0000  (n = 2 queries, 2000 trials)")
                .contains("Not significant \u2014 this change is within noise");
    }

    @Test
    void printsSignificantWhenPValueBelowAlpha() throws IOException {
        Path baselinePath = tempDir.resolve("sig-sub-baseline.json");
        Path candidatePath = tempDir.resolve("sig-sub-candidate.json");

        Map<String, Double> baseMap = new HashMap<>();
        Map<String, Double> candMap = new HashMap<>();
        for (int i = 1; i <= 20; i++) {
            baseMap.put("q" + i, 0.30);
            candMap.put("q" + i, 0.90);
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(baselinePath.toFile(), new MetricResult("NDCG@10", 0.30, baseMap));
        mapper.writeValue(candidatePath.toFile(), new MetricResult("NDCG@10", 0.90, candMap));

        java.io.ByteArrayOutputStream outContent = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(outContent));
            int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                    "compare",
                    "--baseline", baselinePath.toString(),
                    "--candidate", candidatePath.toString(),
                    "--significance-level", "0.05",
                    "--trials", "1000"
            );
            assertThat(exitCode).isEqualTo(0);
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        assertThat(output)
                .contains("Significance:        p =")
                .contains("(n = 20 queries, 1000 trials)")
                .contains("Significant at alpha = 0.05");
    }

    @Test
    void failsOnSignificantRegressionWhenFlagEnabled() throws IOException {
        Path baselinePath = tempDir.resolve("reg-baseline.json");
        Path candidatePath = tempDir.resolve("reg-candidate.json");

        Map<String, Double> baseMap = new HashMap<>();
        Map<String, Double> candMap = new HashMap<>();
        for (int i = 1; i <= 20; i++) {
            baseMap.put("query" + i, 0.80);
            candMap.put("query" + i, 0.30); // each query regresses by 0.50
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(baselinePath.toFile(), new MetricResult("NDCG@10", 0.80, baseMap));
        mapper.writeValue(candidatePath.toFile(), new MetricResult("NDCG@10", 0.30, candMap));

        // When flag is NOT passed and threshold is 1.0 (so per-query gate passes): exit code 0
        int exitWithoutFlag = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "1.0",
                "--trials", "1000"
        );
        assertThat(exitWithoutFlag).isEqualTo(0);

        // When flag IS passed and threshold is 1.0: fails due to statistically significant overall regression
        int exitWithFlag = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "1.0",
                "--trials", "1000",
                "--fail-on-significant-regression"
        );
        assertThat(exitWithFlag).isEqualTo(1);
    }

    @Test
    void existingPerQueryGateStillBehavesSameRegardlessOfSignificance() throws IOException {
        Path baselinePath = tempDir.resolve("gate-baseline.json");
        Path candidatePath = tempDir.resolve("gate-candidate.json");

        MetricResult baseline = new MetricResult("NDCG@10", 0.50, Map.of(
                "q-regressed", 0.80,
                "q-improved", 0.20
        ));
        MetricResult candidate = new MetricResult("NDCG@10", 0.60, Map.of(
                "q-regressed", 0.55, // -0.25 regression
                "q-improved", 0.65  // +0.45 improvement (overall delta is +0.10)
        ));

        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(baselinePath.toFile(), baseline);
        mapper.writeValue(candidatePath.toFile(), candidate);

        // Without flag: still fails because q-regressed exceeded 0.1
        int exitWithoutFlag = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "0.1"
        );
        assertThat(exitWithoutFlag).isEqualTo(1);

        // With flag: still fails because per-query gate triggered
        int exitWithFlag = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "0.1",
                "--fail-on-significant-regression"
        );
        assertThat(exitWithFlag).isEqualTo(1);
    }

    @Test
    void writesSelfContainedHtmlReportWithExpectedQueriesEscapingAndSignificance() throws Exception {
        Path baselinePath = tempDir.resolve("html-baseline.json");
        Path candidatePath = tempDir.resolve("html-candidate.json");
        Path htmlPath = tempDir.resolve("reports/html-report.html");

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        String specialQuery = "hiking <gear> & \"boots\" 'fast'";
        MetricResult baseline = new MetricResult("NDCG@10", 0.75, Map.of(
                "waterproof jacket", 0.95,
                "running shoes", 0.50,
                "trail boots", 0.70,
                specialQuery, 0.85
        ));
        mapper.writeValue(baselinePath.toFile(), baseline);

        MetricResult candidate = new MetricResult("NDCG@10", 0.75, Map.of(
                "waterproof jacket", 0.60,
                "running shoes", 0.80,
                specialQuery, 0.85
        ));
        mapper.writeValue(candidatePath.toFile(), candidate);

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "0.5",
                "--html", htmlPath.toString(),
                "--trials", "1000"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(htmlPath).exists();

        String html = Files.readString(htmlPath);

        // 1. Fully self-contained: contains style block, no CDN / external stylesheets, no script
        assertThat(html)
                .contains("<!DOCTYPE html>")
                .contains("<style>")
                .contains("</style>")
                .doesNotContain("<link rel=\"stylesheet\"")
                .doesNotContain("<script");

        // 2. Header contains run names
        assertThat(html)
                .contains("html-baseline.json")
                .contains("html-candidate.json")
                .contains("Generated:");

        // 3. Expected queries are present
        assertThat(html)
                .contains("waterproof jacket")
                .contains("running shoes")
                .contains("trail boots");

        // 4. Special characters in query are escaped
        assertThat(html)
                .contains("hiking &lt;gear&gt; &amp; &quot;boots&quot; &#39;fast&#39;")
                .doesNotContain("<gear>")
                .doesNotContain("& \"boots\"");

        // 5. Structure and tables
        assertThat(html)
                .contains("Top Regressed Queries")
                .contains("Top Improved Queries")
                .contains("Unchanged Queries")
                .contains("Queries Present in Only One Run")
                .contains("Baseline only");

        // 6. Renders significance banner
        assertThat(html)
                .contains("significance-banner");

        // 7. No emojis
        assertThat(html).doesNotContain("⚠️").doesNotContain("✅").doesNotContain("❌");
    }

    @Test
    void rendersSignificantStateInHtmlReport() throws Exception {
        Path baselinePath = tempDir.resolve("html-sig-baseline.json");
        Path candidatePath = tempDir.resolve("html-sig-candidate.json");
        Path htmlPath = tempDir.resolve("reports/sig-report.html");

        Map<String, Double> baseMap = new HashMap<>();
        Map<String, Double> candMap = new HashMap<>();
        for (int i = 1; i <= 20; i++) {
            baseMap.put("query" + i, 0.30);
            candMap.put("query" + i, 0.90);
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(baselinePath.toFile(), new MetricResult("NDCG@10", 0.30, baseMap));
        mapper.writeValue(candidatePath.toFile(), new MetricResult("NDCG@10", 0.90, candMap));

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--html", htmlPath.toString(),
                "--significance-level", "0.05",
                "--trials", "1000"
        );
        assertThat(exitCode).isEqualTo(0);
        assertThat(htmlPath).exists();

        String html = Files.readString(htmlPath);
        assertThat(html)
                .contains("significance-banner significant")
                .contains("Significant")
                .contains("at alpha = 0.05");
    }

    @Test
    void rendersJudgedCoverageWarningInHtmlWhenBelowThreshold() throws Exception {
        Path baselinePath = tempDir.resolve("html-judged-warn-baseline.json");
        Path candidatePath = tempDir.resolve("html-judged-warn-candidate.json");
        Path htmlPath = tempDir.resolve("reports/judged-warn-report.html");

        ObjectMapper mapper = new ObjectMapper();
        List<MetricResult> baseline = List.of(
                new MetricResult("NDCG@10", 0.70, Map.of("q1", 0.70)),
                new MetricResult("Judged@10", 0.80, Map.of("q1", 0.80))
        );
        List<MetricResult> candidate = List.of(
                new MetricResult("NDCG@10", 0.75, Map.of("q1", 0.75)),
                new MetricResult("Judged@10", 0.42, Map.of("q1", 0.42)) // 0.42 < 0.70
        );
        mapper.writeValue(baselinePath.toFile(), baseline);
        mapper.writeValue(candidatePath.toFile(), candidate);

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--threshold", "0.5",
                "--html", htmlPath.toString(),
                "--judged-warning-threshold", "0.7"
        );
        assertThat(exitCode).isEqualTo(0);

        String html = Files.readString(htmlPath);
        assertThat(html)
                .contains("coverage-box coverage-warning")
                .contains("Warning: Low judgment coverage")
                .contains("Judged@10 is <strong>0.4200</strong>")
                .contains("58% of returned results have no judgment");
    }

    @Test
    void rendersJudgedCoverageOkInHtmlWhenAboveThreshold() throws Exception {
        Path baselinePath = tempDir.resolve("html-judged-ok-baseline.json");
        Path candidatePath = tempDir.resolve("html-judged-ok-candidate.json");
        Path htmlPath = tempDir.resolve("reports/judged-ok-report.html");

        ObjectMapper mapper = new ObjectMapper();
        List<MetricResult> baseline = List.of(
                new MetricResult("NDCG@10", 0.70, Map.of("q1", 0.70)),
                new MetricResult("Judged@10", 0.80, Map.of("q1", 0.80))
        );
        List<MetricResult> candidate = List.of(
                new MetricResult("NDCG@10", 0.75, Map.of("q1", 0.75)),
                new MetricResult("Judged@10", 0.88, Map.of("q1", 0.88)) // 0.88 >= 0.70
        );
        mapper.writeValue(baselinePath.toFile(), baseline);
        mapper.writeValue(candidatePath.toFile(), candidate);

        int exitCode = new CommandLine(new RelevanceEvalCommand()).execute(
                "compare",
                "--baseline", baselinePath.toString(),
                "--candidate", candidatePath.toString(),
                "--html", htmlPath.toString(),
                "--judged-warning-threshold", "0.7"
        );
        assertThat(exitCode).isEqualTo(0);

        String html = Files.readString(htmlPath);
        assertThat(html)
                .contains("class=\"coverage-box coverage-ok\"")
                .doesNotContain("class=\"coverage-box coverage-warning\"")
                .contains("Judged@10 Coverage:")
                .contains("meets threshold 0.7000");
    }
}

