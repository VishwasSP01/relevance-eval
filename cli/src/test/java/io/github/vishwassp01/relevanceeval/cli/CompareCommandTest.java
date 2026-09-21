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
}
