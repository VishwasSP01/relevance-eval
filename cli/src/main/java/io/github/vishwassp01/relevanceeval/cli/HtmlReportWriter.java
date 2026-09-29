package io.github.vishwassp01.relevanceeval.cli;

import io.github.vishwassp01.relevanceeval.diff.ComparisonResult;
import io.github.vishwassp01.relevanceeval.diff.QueryDelta;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.stats.SignificanceResult;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Generates self-contained, responsive HTML reports from relevance comparison results.
 */
public class HtmlReportWriter {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    /**
     * Writes comparison results as a self-contained HTML report to the specified path.
     *
     * @param baselinePath           the path to the baseline run file
     * @param candidatePath          the path to the candidate run file
     * @param comparisons            the metric comparison results
     * @param baselineRuns           the baseline metric results
     * @param candidateRuns          the candidate metric results
     * @param significanceLevel      the significance level (alpha)
     * @param judgedWarningThreshold the threshold below which a coverage warning is displayed
     * @param outputPath             the output HTML file path
     * @throws IOException if an error occurs writing the report
     */
    public static void writeReport(
            Path baselinePath,
            Path candidatePath,
            List<ComparisonResult> comparisons,
            List<MetricResult> baselineRuns,
            List<MetricResult> candidateRuns,
            double significanceLevel,
            double judgedWarningThreshold,
            Path outputPath
    ) throws IOException {
        writeReport(
                baselinePath,
                candidatePath,
                comparisons,
                baselineRuns,
                candidateRuns,
                significanceLevel,
                judgedWarningThreshold,
                outputPath,
                Instant.now()
        );
    }

    /**
     * Writes comparison results as a self-contained HTML report with a specified generation timestamp.
     */
    public static void writeReport(
            Path baselinePath,
            Path candidatePath,
            List<ComparisonResult> comparisons,
            List<MetricResult> baselineRuns,
            List<MetricResult> candidateRuns,
            double significanceLevel,
            double judgedWarningThreshold,
            Path outputPath,
            Instant generatedAt
    ) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        String html = generateHtml(
                baselinePath,
                candidatePath,
                comparisons,
                baselineRuns,
                candidateRuns,
                significanceLevel,
                judgedWarningThreshold,
                generatedAt
        );

        try (OutputStream out = Files.newOutputStream(outputPath)) {
            out.write(html.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generates self-contained HTML content for the comparison report.
     */
    public static String generateHtml(
            Path baselinePath,
            Path candidatePath,
            List<ComparisonResult> comparisons,
            List<MetricResult> baselineRuns,
            List<MetricResult> candidateRuns,
            double significanceLevel,
            double judgedWarningThreshold,
            Instant generatedAt
    ) {
        String baselineName = baselinePath != null ? baselinePath.getFileName().toString() : "baseline.json";
        String candidateName = candidatePath != null ? candidatePath.getFileName().toString() : "candidate.json";
        String baselineDisplay = baselinePath != null ? baselinePath.toString() : baselineName;
        String candidateDisplay = candidatePath != null ? candidatePath.toString() : candidateName;
        String timestampStr = DATE_FORMATTER.format(generatedAt) + " UTC";

        StringBuilder sb = new StringBuilder(16384);
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>Relevance Comparison: ").append(escapeHtml(baselineName))
                .append(" vs ").append(escapeHtml(candidateName)).append("</title>\n");
        sb.append("  <style>\n");
        appendCss(sb);
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<div class=\"container\">\n");

        // 1. Header
        sb.append("  <header>\n");
        sb.append("    <h1>Relevance Evaluation Comparison</h1>\n");
        sb.append("    <div class=\"meta\">\n");
        sb.append("      <div class=\"meta-row\"><span class=\"meta-label\">Baseline:</span> ")
                .append(escapeHtml(baselineName))
                .append(" <span class=\"meta-path\">(").append(escapeHtml(baselineDisplay)).append(")</span></div>\n");
        sb.append("      <div class=\"meta-row\"><span class=\"meta-label\">Candidate:</span> ")
                .append(escapeHtml(candidateName))
                .append(" <span class=\"meta-path\">(").append(escapeHtml(candidateDisplay)).append(")</span></div>\n");
        sb.append("      <div class=\"meta-row\"><span class=\"meta-label\">Generated:</span> ")
                .append(escapeHtml(timestampStr)).append("</div>\n");
        sb.append("    </div>\n");
        sb.append("  </header>\n\n");

        // 2. Summary block per metric
        sb.append("  <section class=\"section\">\n");
        sb.append("    <h2>Metric Summary</h2>\n");
        for (ComparisonResult cr : comparisons) {
            appendMetricSummary(sb, cr, significanceLevel);
        }
        sb.append("  </section>\n\n");

        // 3. Judged@k coverage block
        CoverageInfo coverage = findCoverageInfo(comparisons, baselineRuns, candidateRuns);
        appendCoverageSection(sb, coverage, judgedWarningThreshold);

        // 4. Query tables per comparison
        boolean multiMetric = comparisons.size() > 1;
        for (ComparisonResult cr : comparisons) {
            sb.append("  <section class=\"section\">\n");
            if (multiMetric) {
                sb.append("    <h2>Query Breakdown: ").append(escapeHtml(cr.metricName())).append("</h2>\n");
            } else {
                sb.append("    <h2>Query Breakdown</h2>\n");
            }

            // Regressed queries (worst first, in red)
            appendRegressedTable(sb, cr.regressed());

            // Improved queries (best first, in green)
            appendImprovedTable(sb, cr.improved());

            // Unchanged queries (collapsed / muted)
            appendUnchangedSection(sb, cr.unchanged());

            // Queries present in only one run (clearly marked)
            appendOnlyInOneSection(sb, cr.onlyInOne());

            sb.append("  </section>\n\n");
        }

        sb.append("</div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        return sb.toString();
    }

    private static void appendMetricSummary(StringBuilder sb, ComparisonResult cr, double alpha) {
        sb.append("    <div class=\"metric-card\">\n");
        sb.append("      <div class=\"metric-header\">\n");
        sb.append("        <h3 class=\"metric-title\">").append(escapeHtml(cr.metricName())).append("</h3>\n");
        sb.append("      </div>\n");

        sb.append("      <div class=\"stat-grid\">\n");
        sb.append("        <div class=\"stat-box\">\n");
        sb.append("          <div class=\"stat-label\">Baseline</div>\n");
        sb.append("          <div class=\"stat-value\">").append(formatScore(cr.baselineOverall())).append("</div>\n");
        sb.append("        </div>\n");

        sb.append("        <div class=\"stat-box\">\n");
        sb.append("          <div class=\"stat-label\">Candidate</div>\n");
        sb.append("          <div class=\"stat-value\">").append(formatScore(cr.candidateOverall())).append("</div>\n");
        sb.append("        </div>\n");

        double delta = cr.overallDelta();
        String deltaClass = delta > 1e-9 ? "positive" : (delta < -1e-9 ? "negative" : "neutral");
        sb.append("        <div class=\"stat-box\">\n");
        sb.append("          <div class=\"stat-label\">Overall Delta</div>\n");
        sb.append("          <div class=\"stat-value ").append(deltaClass).append("\">")
                .append(formatDelta(delta)).append("</div>\n");
        sb.append("        </div>\n");

        SignificanceResult sig = cr.significance();
        if (sig != null) {
            sb.append("        <div class=\"stat-box\">\n");
            sb.append("          <div class=\"stat-label\">P-Value</div>\n");
            sb.append("          <div class=\"stat-value\">").append(formatPValue(sig.pValue())).append("</div>\n");
            sb.append("        </div>\n");
        }
        sb.append("      </div>\n");

        if (sig != null) {
            boolean isSignificant = sig.pValue() <= alpha;
            String statusClass = isSignificant ? "significant" : "not-significant";
            sb.append("      <div class=\"significance-banner ").append(statusClass).append("\">\n");
            if (isSignificant) {
                sb.append("        <strong>Significant</strong> at alpha = ").append(formatAlpha(alpha))
                        .append(" (p = ").append(formatPValue(sig.pValue()))
                        .append(", n = ").append(sig.sampleSize())
                        .append(" queries, ").append(sig.trials()).append(" trials)\n");
            } else {
                sb.append("        <strong>Not significant</strong> &mdash; this change is within noise (p = ")
                        .append(formatPValue(sig.pValue()))
                        .append(", n = ").append(sig.sampleSize())
                        .append(" queries, ").append(sig.trials()).append(" trials)\n");
            }
            sb.append("      </div>\n");
        }
        sb.append("    </div>\n");
    }

    private static void appendCoverageSection(StringBuilder sb, CoverageInfo coverage, double threshold) {
        sb.append("  <section class=\"section\">\n");
        sb.append("    <h2>Judgment Coverage</h2>\n");

        if (coverage == null) {
            sb.append("    <div class=\"coverage-box coverage-muted\">\n");
            sb.append("      <p>Not evaluated in these runs. Include <code>judged@k</code> in evaluation metrics to assess judgment coverage.</p>\n");
            sb.append("    </div>\n");
            sb.append("  </section>\n\n");
            return;
        }

        Double candCov = coverage.candidateCoverage();
        Double baseCov = coverage.baselineCoverage();
        boolean candidateBelow = candCov != null && candCov < threshold;
        boolean baselineBelow = baseCov != null && baseCov < threshold;

        if (candidateBelow || baselineBelow) {
            double effectiveCov = candCov != null ? candCov : baseCov;
            double unjudgedPct = Math.max(0.0, 1.0 - effectiveCov) * 100.0;
            long unjudgedRounded = Math.round(unjudgedPct);

            sb.append("    <div class=\"coverage-box coverage-warning\">\n");
            sb.append("      <div class=\"coverage-warning-title\">Warning: Low judgment coverage</div>\n");
            sb.append("      <p>").append(escapeHtml(coverage.metricName()))
                    .append(" is <strong>").append(formatScore(effectiveCov))
                    .append("</strong> &mdash; ").append(unjudgedRounded)
                    .append("% of returned results have no judgment. Scores may be unreliable. Consider expanding the judgment set.</p>\n");
            sb.append("      <div class=\"coverage-details\">\n");
            if (baseCov != null) {
                sb.append("        <span>Baseline: <code>").append(formatScore(baseCov)).append("</code></span>\n");
            }
            if (candCov != null) {
                sb.append("        <span>Candidate: <code>").append(formatScore(candCov)).append("</code></span>\n");
            }
            sb.append("        <span>Warning Threshold: <code>").append(formatScore(threshold)).append("</code></span>\n");
            sb.append("      </div>\n");
            sb.append("    </div>\n");
        } else {
            sb.append("    <div class=\"coverage-box coverage-ok\">\n");
            sb.append("      <p><strong>").append(escapeHtml(coverage.metricName())).append(" Coverage:</strong> ");
            if (candCov != null) {
                sb.append("Candidate: <code>").append(formatScore(candCov)).append("</code> (")
                        .append(Math.round(candCov * 100.0)).append("%)");
            }
            if (baseCov != null) {
                if (candCov != null) sb.append(", ");
                sb.append("Baseline: <code>").append(formatScore(baseCov)).append("</code> (")
                        .append(Math.round(baseCov * 100.0)).append("%)");
            }
            sb.append(" &mdash; meets threshold ").append(formatScore(threshold)).append(".</p>\n");
            sb.append("    </div>\n");
        }

        sb.append("  </section>\n\n");
    }

    private static void appendRegressedTable(StringBuilder sb, List<QueryDelta> regressed) {
        sb.append("    <div class=\"query-block\">\n");
        sb.append("      <h3>Top Regressed Queries (").append(regressed.size()).append(")</h3>\n");
        if (regressed.isEmpty()) {
            sb.append("      <p class=\"muted\">No queries regressed.</p>\n");
        } else {
            sb.append("      <div class=\"table-wrapper\">\n");
            sb.append("        <table class=\"delta-table regressed-table\">\n");
            sb.append("          <thead>\n");
            sb.append("            <tr>\n");
            sb.append("              <th>Query</th>\n");
            sb.append("              <th class=\"num\">Baseline</th>\n");
            sb.append("              <th class=\"num\">Candidate</th>\n");
            sb.append("              <th class=\"num\">Delta</th>\n");
            sb.append("            </tr>\n");
            sb.append("          </thead>\n");
            sb.append("          <tbody>\n");
            for (QueryDelta delta : regressed) {
                sb.append("            <tr>\n");
                sb.append("              <td class=\"query-cell\">").append(escapeHtml(delta.query())).append("</td>\n");
                sb.append("              <td class=\"num\">").append(formatScore(delta.baselineValue())).append("</td>\n");
                sb.append("              <td class=\"num\">").append(formatScore(delta.candidateValue())).append("</td>\n");
                sb.append("              <td class=\"num delta negative\">").append(formatDelta(delta.delta())).append("</td>\n");
                sb.append("            </tr>\n");
            }
            sb.append("          </tbody>\n");
            sb.append("        </table>\n");
            sb.append("      </div>\n");
        }
        sb.append("    </div>\n");
    }

    private static void appendImprovedTable(StringBuilder sb, List<QueryDelta> improved) {
        sb.append("    <div class=\"query-block\">\n");
        sb.append("      <h3>Top Improved Queries (").append(improved.size()).append(")</h3>\n");
        if (improved.isEmpty()) {
            sb.append("      <p class=\"muted\">No queries improved.</p>\n");
        } else {
            sb.append("      <div class=\"table-wrapper\">\n");
            sb.append("        <table class=\"delta-table improved-table\">\n");
            sb.append("          <thead>\n");
            sb.append("            <tr>\n");
            sb.append("              <th>Query</th>\n");
            sb.append("              <th class=\"num\">Baseline</th>\n");
            sb.append("              <th class=\"num\">Candidate</th>\n");
            sb.append("              <th class=\"num\">Delta</th>\n");
            sb.append("            </tr>\n");
            sb.append("          </thead>\n");
            sb.append("          <tbody>\n");
            for (QueryDelta delta : improved) {
                sb.append("            <tr>\n");
                sb.append("              <td class=\"query-cell\">").append(escapeHtml(delta.query())).append("</td>\n");
                sb.append("              <td class=\"num\">").append(formatScore(delta.baselineValue())).append("</td>\n");
                sb.append("              <td class=\"num\">").append(formatScore(delta.candidateValue())).append("</td>\n");
                sb.append("              <td class=\"num delta positive\">").append(formatDelta(delta.delta())).append("</td>\n");
                sb.append("            </tr>\n");
            }
            sb.append("          </tbody>\n");
            sb.append("        </table>\n");
            sb.append("      </div>\n");
        }
        sb.append("    </div>\n");
    }

    private static void appendUnchangedSection(StringBuilder sb, List<QueryDelta> unchanged) {
        sb.append("    <div class=\"query-block\">\n");
        sb.append("      <details class=\"unchanged-section\">\n");
        sb.append("        <summary>Unchanged Queries (").append(unchanged.size()).append(")</summary>\n");
        if (unchanged.isEmpty()) {
            sb.append("        <p class=\"muted\">No unchanged queries.</p>\n");
        } else {
            sb.append("        <div class=\"table-wrapper\">\n");
            sb.append("          <table class=\"delta-table muted-table\">\n");
            sb.append("            <thead>\n");
            sb.append("              <tr>\n");
            sb.append("                <th>Query</th>\n");
            sb.append("                <th class=\"num\">Score</th>\n");
            sb.append("              </tr>\n");
            sb.append("            </thead>\n");
            sb.append("            <tbody>\n");
            for (QueryDelta delta : unchanged) {
                sb.append("              <tr>\n");
                sb.append("                <td class=\"query-cell\">").append(escapeHtml(delta.query())).append("</td>\n");
                sb.append("                <td class=\"num\">").append(formatScore(delta.candidateValue())).append("</td>\n");
                sb.append("              </tr>\n");
            }
            sb.append("            </tbody>\n");
            sb.append("          </table>\n");
            sb.append("        </div>\n");
        }
        sb.append("      </details>\n");
        sb.append("    </div>\n");
    }

    private static void appendOnlyInOneSection(StringBuilder sb, List<QueryDelta> onlyInOne) {
        sb.append("    <div class=\"query-block\">\n");
        sb.append("      <h3>Queries Present in Only One Run (").append(onlyInOne.size()).append(")</h3>\n");
        if (onlyInOne.isEmpty()) {
            sb.append("      <p class=\"muted\">All queries were present in both runs.</p>\n");
        } else {
            sb.append("      <div class=\"table-wrapper\">\n");
            sb.append("        <table class=\"delta-table only-one-table\">\n");
            sb.append("          <thead>\n");
            sb.append("            <tr>\n");
            sb.append("              <th>Query</th>\n");
            sb.append("              <th>Run Presence</th>\n");
            sb.append("              <th class=\"num\">Score</th>\n");
            sb.append("            </tr>\n");
            sb.append("          </thead>\n");
            sb.append("          <tbody>\n");
            for (QueryDelta delta : onlyInOne) {
                sb.append("            <tr>\n");
                sb.append("              <td class=\"query-cell\">").append(escapeHtml(delta.query())).append("</td>\n");
                if (Double.isNaN(delta.candidateValue())) {
                    sb.append("              <td><span class=\"badge badge-baseline\">Baseline only</span></td>\n");
                    sb.append("              <td class=\"num\">").append(formatScore(delta.baselineValue())).append("</td>\n");
                } else {
                    sb.append("              <td><span class=\"badge badge-candidate\">Candidate only</span></td>\n");
                    sb.append("              <td class=\"num\">").append(formatScore(delta.candidateValue())).append("</td>\n");
                }
                sb.append("            </tr>\n");
            }
            sb.append("          </tbody>\n");
            sb.append("        </table>\n");
            sb.append("      </div>\n");
        }
        sb.append("    </div>\n");
    }

    static CoverageInfo findCoverageInfo(
            List<ComparisonResult> comparisons,
            List<MetricResult> baselineRuns,
            List<MetricResult> candidateRuns
    ) {
        // 1. Check comparisons
        if (comparisons != null) {
            for (ComparisonResult cr : comparisons) {
                if (cr.metricName().toLowerCase(Locale.ROOT).startsWith("judged@")) {
                    return new CoverageInfo(cr.metricName(), cr.baselineOverall(), cr.candidateOverall());
                }
            }
        }

        // 2. Check candidateRuns and baselineRuns
        String metricName = null;
        Double candVal = null;
        Double baseVal = null;

        if (candidateRuns != null) {
            for (MetricResult mr : candidateRuns) {
                if (mr.metricName().toLowerCase(Locale.ROOT).startsWith("judged@")) {
                    metricName = mr.metricName();
                    candVal = mr.overallValue();
                    break;
                }
            }
        }

        if (baselineRuns != null) {
            for (MetricResult mr : baselineRuns) {
                if (mr.metricName().toLowerCase(Locale.ROOT).startsWith("judged@")) {
                    if (metricName == null) metricName = mr.metricName();
                    baseVal = mr.overallValue();
                    break;
                }
            }
        }

        if (metricName != null) {
            return new CoverageInfo(metricName, baseVal, candVal);
        }

        return null;
    }

    record CoverageInfo(String metricName, Double baselineCoverage, Double candidateCoverage) {}

    public static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String formatScore(double value) {
        if (Double.isNaN(value)) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String formatDelta(double delta) {
        if (Double.isNaN(delta)) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%+.4f", delta);
    }

    private static String formatPValue(double p) {
        return String.format(Locale.ROOT, "%.4f", p);
    }

    private static String formatAlpha(double alpha) {
        if (alpha == (long) alpha) {
            return String.format(Locale.ROOT, "%d", (long) alpha);
        }
        return String.valueOf(alpha);
    }

    private static void appendCss(StringBuilder sb) {
        sb.append("""
            :root {
              --bg: #ffffff;
              --card-bg: #f8f9fa;
              --text: #212529;
              --text-muted: #6c757d;
              --border: #dee2e6;
              --border-light: #e9ecef;
              --red-text: #b02a37;
              --red-bg: #fff5f5;
              --red-border: #f1aeb5;
              --green-text: #146c43;
              --green-bg: #f4fbf6;
              --green-border: #a3cfbb;
              --warn-text: #664d03;
              --warn-bg: #fff3cd;
              --warn-border: #ffe69c;
              --font-sans: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              --font-mono: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
            }
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              font-family: var(--font-sans);
              background: var(--bg);
              color: var(--text);
              line-height: 1.5;
              padding: 1.5rem 1rem;
            }
            .container {
              max-width: 980px;
              margin: 0 auto;
            }
            header {
              border-bottom: 2px solid var(--border);
              padding-bottom: 1.25rem;
              margin-bottom: 2rem;
            }
            h1 {
              font-size: 1.75rem;
              font-weight: 700;
              margin-bottom: 0.75rem;
              color: var(--text);
            }
            h2 {
              font-size: 1.3rem;
              font-weight: 600;
              margin-bottom: 1rem;
              color: var(--text);
            }
            h3 {
              font-size: 1.05rem;
              font-weight: 600;
              margin-bottom: 0.5rem;
              color: var(--text);
            }
            .meta {
              font-size: 0.9rem;
              color: var(--text-muted);
            }
            .meta-row {
              margin-bottom: 0.25rem;
            }
            .meta-label {
              font-weight: 600;
              color: var(--text);
              min-width: 90px;
              display: inline-block;
            }
            .meta-path {
              color: var(--text-muted);
              font-size: 0.85rem;
            }
            .section {
              margin-bottom: 2rem;
            }
            .metric-card {
              background: var(--card-bg);
              border: 1px solid var(--border);
              border-radius: 4px;
              padding: 1.25rem;
              margin-bottom: 1.25rem;
            }
            .metric-header {
              margin-bottom: 1rem;
              border-bottom: 1px solid var(--border-light);
              padding-bottom: 0.5rem;
            }
            .metric-title {
              font-size: 1.2rem;
              font-weight: 600;
              margin: 0;
            }
            .stat-grid {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
              gap: 0.75rem;
              margin-bottom: 1rem;
            }
            .stat-box {
              background: #ffffff;
              border: 1px solid var(--border);
              border-radius: 4px;
              padding: 0.6rem 0.75rem;
            }
            .stat-label {
              font-size: 0.75rem;
              color: var(--text-muted);
              text-transform: uppercase;
              letter-spacing: 0.5px;
              margin-bottom: 0.25rem;
            }
            .stat-value {
              font-size: 1.25rem;
              font-weight: 700;
              font-family: var(--font-mono);
            }
            .stat-value.positive { color: var(--green-text); }
            .stat-value.negative { color: var(--red-text); }
            .significance-banner {
              padding: 0.65rem 0.9rem;
              border-radius: 4px;
              font-size: 0.9rem;
            }
            .significance-banner.significant {
              background: #d1e7dd;
              color: var(--green-text);
              border: 1px solid var(--green-border);
            }
            .significance-banner.not-significant {
              background: #e9ecef;
              color: var(--text-muted);
              border: 1px solid var(--border);
            }
            .coverage-box {
              border-radius: 4px;
              padding: 1rem 1.25rem;
              margin-bottom: 1.5rem;
              font-size: 0.95rem;
            }
            .coverage-warning {
              background: var(--warn-bg);
              color: var(--warn-text);
              border: 1px solid var(--warn-border);
            }
            .coverage-warning-title {
              font-weight: 700;
              margin-bottom: 0.25rem;
            }
            .coverage-details {
              margin-top: 0.5rem;
              font-size: 0.85rem;
              display: flex;
              gap: 1.5rem;
              flex-wrap: wrap;
            }
            .coverage-ok {
              background: var(--card-bg);
              border: 1px solid var(--border);
              color: var(--text);
            }
            .coverage-muted {
              background: var(--card-bg);
              border: 1px solid var(--border);
              color: var(--text-muted);
            }
            .query-block {
              margin-bottom: 1.5rem;
            }
            .table-wrapper {
              overflow-x: auto;
              -webkit-overflow-scrolling: touch;
              border: 1px solid var(--border);
              border-radius: 4px;
              margin-top: 0.5rem;
            }
            table {
              width: 100%;
              min-width: 480px;
              border-collapse: collapse;
              font-size: 0.9rem;
              text-align: left;
            }
            th {
              background: var(--card-bg);
              border-bottom: 1px solid var(--border);
              padding: 0.55rem 0.75rem;
              font-weight: 600;
              color: var(--text);
            }
            td {
              padding: 0.55rem 0.75rem;
              border-bottom: 1px solid var(--border-light);
            }
            tr:last-child td {
              border-bottom: none;
            }
            th.num, td.num {
              text-align: right;
              font-family: var(--font-mono);
            }
            .query-cell {
              font-family: var(--font-mono);
              word-break: break-word;
            }
            .regressed-table tbody tr {
              background-color: var(--red-bg);
            }
            .regressed-table th {
              color: var(--red-text);
            }
            .regressed-table td.delta {
              color: var(--red-text);
              font-weight: 700;
            }
            .improved-table tbody tr {
              background-color: var(--green-bg);
            }
            .improved-table th {
              color: var(--green-text);
            }
            .improved-table td.delta {
              color: var(--green-text);
              font-weight: 700;
            }
            .muted-table {
              color: var(--text-muted);
            }
            details.unchanged-section {
              border: 1px solid var(--border);
              border-radius: 4px;
              background: var(--card-bg);
              padding: 0.6rem 0.9rem;
            }
            details.unchanged-section summary {
              cursor: pointer;
              font-weight: 600;
              color: var(--text-muted);
            }
            details.unchanged-section[open] {
              padding-bottom: 0.9rem;
            }
            details.unchanged-section .table-wrapper {
              margin-top: 0.75rem;
            }
            .badge {
              display: inline-block;
              padding: 0.2rem 0.5rem;
              border-radius: 3px;
              font-size: 0.75rem;
              font-weight: 600;
            }
            .badge-baseline {
              background: #e2e3e5;
              color: #41464b;
            }
            .badge-candidate {
              background: #cff4fc;
              color: #055160;
            }
            .muted {
              color: var(--text-muted);
              font-style: italic;
              font-size: 0.9rem;
            }
            code {
              font-family: var(--font-mono);
              background: #e9ecef;
              padding: 0.15rem 0.35rem;
              border-radius: 3px;
              font-size: 0.85em;
            }
            @media (max-width: 600px) {
              body { padding: 1rem 0.5rem; }
              .stat-grid { grid-template-columns: 1fr 1fr; }
              h1 { font-size: 1.4rem; }
              .meta-label { min-width: auto; }
              .coverage-details { gap: 0.75rem; flex-direction: column; }
            }
            """);
    }
}
