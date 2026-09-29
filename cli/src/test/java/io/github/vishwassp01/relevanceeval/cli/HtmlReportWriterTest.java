package io.github.vishwassp01.relevanceeval.cli;

import io.github.vishwassp01.relevanceeval.diff.ComparisonResult;
import io.github.vishwassp01.relevanceeval.diff.QueryDelta;
import io.github.vishwassp01.relevanceeval.stats.SignificanceResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlReportWriterTest {

    @Test
    void escapesSpecialHtmlCharactersCorrectly() {
        assertThat(HtmlReportWriter.escapeHtml(null)).isEmpty();
        assertThat(HtmlReportWriter.escapeHtml("plain text")).isEqualTo("plain text");
        assertThat(HtmlReportWriter.escapeHtml("<tag>")).isEqualTo("&lt;tag&gt;");
        assertThat(HtmlReportWriter.escapeHtml("Tom & Jerry")).isEqualTo("Tom &amp; Jerry");
        assertThat(HtmlReportWriter.escapeHtml("\"quoted\"")).isEqualTo("&quot;quoted&quot;");
        assertThat(HtmlReportWriter.escapeHtml("it's")).isEqualTo("it&#39;s");
        assertThat(HtmlReportWriter.escapeHtml("<script>alert(\"xss & 'danger'\")</script>"))
                .isEqualTo("&lt;script&gt;alert(&quot;xss &amp; &#39;danger&#39;&quot;)&lt;/script&gt;");
    }

    @Test
    void rendersNotSignificantStateCorrectly() {
        ComparisonResult comp = new ComparisonResult(
                "NDCG@10",
                0.70,
                0.71,
                List.of(new QueryDelta("q1", 0.70, 0.71)),
                List.of(),
                List.of(),
                List.of(),
                new SignificanceResult(0.6543, 0.01, 10, 5000)
        );

        String html = HtmlReportWriter.generateHtml(
                Path.of("base.json"),
                Path.of("cand.json"),
                List.of(comp),
                null,
                null,
                0.05,
                0.7,
                Instant.parse("2026-09-29T12:00:00Z")
        );

        assertThat(html)
                .contains("significance-banner not-significant")
                .contains("Not significant")
                .contains("this change is within noise (p = 0.6543, n = 10 queries, 5000 trials)")
                .contains("2026-09-29 12:00:00 UTC");
    }

    @Test
    void handlesEmptyListsAndNoCoverageGracefully() {
        ComparisonResult comp = new ComparisonResult(
                "NDCG@10",
                0.80,
                0.80,
                List.of(),
                List.of(),
                List.of(new QueryDelta("same-query", 0.80, 0.80)),
                List.of()
        );

        String html = HtmlReportWriter.generateHtml(
                Path.of("base.json"),
                Path.of("cand.json"),
                List.of(comp),
                null,
                null,
                0.05,
                0.7,
                Instant.parse("2026-09-29T12:00:00Z")
        );

        assertThat(html)
                .contains("No queries regressed.")
                .contains("No queries improved.")
                .contains("Unchanged Queries (1)")
                .contains("same-query")
                .contains("All queries were present in both runs.")
                .contains("Judgment Coverage")
                .contains("Not evaluated in these runs");
    }
}
