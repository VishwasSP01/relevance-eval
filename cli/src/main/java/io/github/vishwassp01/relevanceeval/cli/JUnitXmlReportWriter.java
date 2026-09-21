package io.github.vishwassp01.relevanceeval.cli;

import io.github.vishwassp01.relevanceeval.diff.ComparisonResult;
import io.github.vishwassp01.relevanceeval.diff.QueryDelta;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates standard JUnit XML reports from relevance comparison results.
 */
public class JUnitXmlReportWriter {

    /**
     * Writes comparison results as a standard JUnit XML report to the specified path.
     *
     * @param comparisons the list of metric comparison results
     * @param threshold   the regression threshold above which queries are considered failures
     * @param outputPath  the path to write the JUnit XML report to
     * @throws ParserConfigurationException if a DocumentBuilder cannot be created
     * @throws TransformerException         if an error occurs during XML serialization
     * @throws IOException                  if an I/O error occurs writing to the file
     */
    public static void writeReport(List<ComparisonResult> comparisons, double threshold, Path outputPath)
            throws ParserConfigurationException, TransformerException, IOException {
        Document doc = createDocument(comparisons, threshold);

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        try (OutputStream out = Files.newOutputStream(outputPath)) {
            transformer.transform(new DOMSource(doc), new StreamResult(out));
        }
    }

    static Document createDocument(List<ComparisonResult> comparisons, double threshold)
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        int totalTests = 0;
        int totalFailures = 0;
        int totalSkipped = 0;

        for (ComparisonResult comparison : comparisons) {
            for (QueryDelta delta : comparison.regressed()) {
                totalTests++;
                if (Math.abs(delta.delta()) > threshold) {
                    totalFailures++;
                }
            }
            totalTests += comparison.improved().size();
            totalTests += comparison.unchanged().size();
            for (QueryDelta ignored : comparison.onlyInOne()) {
                totalTests++;
                totalSkipped++;
            }
        }

        Element testsuite = doc.createElement("testsuite");
        testsuite.setAttribute("name", "comparison");
        testsuite.setAttribute("tests", String.valueOf(totalTests));
        testsuite.setAttribute("failures", String.valueOf(totalFailures));
        testsuite.setAttribute("errors", "0");
        testsuite.setAttribute("skipped", String.valueOf(totalSkipped));
        doc.appendChild(testsuite);

        for (ComparisonResult comparison : comparisons) {
            String metric = comparison.metricName();

            // Regressed queries
            for (QueryDelta delta : comparison.regressed()) {
                Element testcase = doc.createElement("testcase");
                testcase.setAttribute("classname", metric);
                testcase.setAttribute("name", metric + " / " + delta.query());

                if (Math.abs(delta.delta()) > threshold) {
                    Element failure = doc.createElement("failure");
                    String msg = String.format(
                            "Regressed beyond threshold %.4f: baseline=%.4f, candidate=%.4f, delta=%+.4f",
                            threshold, delta.baselineValue(), delta.candidateValue(), delta.delta()
                    );
                    failure.setAttribute("message", msg);
                    failure.setAttribute("type", "ComparisonFailure");
                    failure.setTextContent(msg);
                    testcase.appendChild(failure);
                }
                testsuite.appendChild(testcase);
            }

            // Improved queries (passing)
            for (QueryDelta delta : comparison.improved()) {
                Element testcase = doc.createElement("testcase");
                testcase.setAttribute("classname", metric);
                testcase.setAttribute("name", metric + " / " + delta.query());
                testsuite.appendChild(testcase);
            }

            // Unchanged queries (passing)
            for (QueryDelta delta : comparison.unchanged()) {
                Element testcase = doc.createElement("testcase");
                testcase.setAttribute("classname", metric);
                testcase.setAttribute("name", metric + " / " + delta.query());
                testsuite.appendChild(testcase);
            }

            // Only in one run (skipped)
            for (QueryDelta delta : comparison.onlyInOne()) {
                Element testcase = doc.createElement("testcase");
                testcase.setAttribute("classname", metric);
                testcase.setAttribute("name", metric + " / " + delta.query());

                Element skipped = doc.createElement("skipped");
                String msg = Double.isNaN(delta.candidateValue())
                        ? "Missing from candidate run"
                        : "Missing from baseline run";
                skipped.setAttribute("message", msg);
                skipped.setTextContent(msg);
                testcase.appendChild(skipped);

                testsuite.appendChild(testcase);
            }
        }

        return doc;
    }
}
