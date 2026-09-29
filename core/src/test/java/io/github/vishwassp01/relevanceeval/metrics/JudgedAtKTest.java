package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JudgedAtKTest {

    /**
     * Hand-calculated reasoning:
     * - Query: "running shoes"
     * - Judgments: doc-1 (grade 3), doc-2 (grade 2), doc-3 (grade 1)
     * - Top-3 returned: [doc-1, doc-2, doc-3]
     * - Evaluated positions: min(k=3, results.size()=3) = 3
     * - Judged documents in top-3: doc-1, doc-2, doc-3 (3 judged)
     * - Score: 3 / 3 = 1.0
     * - Overall score: 1.0 / 1 = 1.0
     */
    @Test
    void allResultsJudgedYieldsOne() {
        JudgmentSet judgments = new JudgmentSet("test", List.of(
                new Judgment("running shoes", "doc-1", 3),
                new Judgment("running shoes", "doc-2", 2),
                new Judgment("running shoes", "doc-3", 1)
        ));

        Map<String, List<SearchResult>> results = Map.of(
                "running shoes", List.of(
                        new SearchResult("doc-1", 1, 0.99),
                        new SearchResult("doc-2", 2, 0.95),
                        new SearchResult("doc-3", 3, 0.90)
                )
        );

        MetricResult result = new JudgedAtK(3).computeFrom(judgments, results);

        assertThat(result.perQueryValues().get("running shoes")).isCloseTo(1.0, within(1e-9));
        assertThat(result.overallValue()).isCloseTo(1.0, within(1e-9));
    }

    /**
     * Hand-calculated reasoning:
     * - Query: "running shoes"
     * - Judgments: doc-1 (grade 3), doc-2 (grade 2)
     * - Top-3 returned: [doc-x, doc-y, doc-z] (none present in judgment set)
     * - Evaluated positions: min(k=3, results.size()=3) = 3
     * - Judged documents in top-3: 0
     * - Score: 0 / 3 = 0.0
     * - Overall score: 0.0 / 1 = 0.0
     */
    @Test
    void noneJudgedYieldsZero() {
        JudgmentSet judgments = new JudgmentSet("test", List.of(
                new Judgment("running shoes", "doc-1", 3),
                new Judgment("running shoes", "doc-2", 2)
        ));

        Map<String, List<SearchResult>> results = Map.of(
                "running shoes", List.of(
                        new SearchResult("doc-x", 1, 0.99),
                        new SearchResult("doc-y", 2, 0.95),
                        new SearchResult("doc-z", 3, 0.90)
                )
        );

        MetricResult result = new JudgedAtK(3).computeFrom(judgments, results);

        assertThat(result.perQueryValues().get("running shoes")).isCloseTo(0.0, within(1e-9));
        assertThat(result.overallValue()).isCloseTo(0.0, within(1e-9));
    }

    /**
     * Hand-calculated reasoning:
     * - Query: "running shoes"
     * - Judgments: doc-1 (grade 3), doc-2 (grade 2)
     * - Top-4 returned: [doc-1, doc-x, doc-2, doc-y]
     * - Evaluated positions: min(k=4, results.size()=4) = 4
     * - Judged documents in top-4: doc-1 (at rank 1), doc-2 (at rank 3) = 2 judged
     * - Score: 2 / 4 = 0.5
     * - Overall score: 0.5 / 1 = 0.5
     */
    @Test
    void halfJudgedYieldsHalf() {
        JudgmentSet judgments = new JudgmentSet("test", List.of(
                new Judgment("running shoes", "doc-1", 3),
                new Judgment("running shoes", "doc-2", 2)
        ));

        Map<String, List<SearchResult>> results = Map.of(
                "running shoes", List.of(
                        new SearchResult("doc-1", 1, 0.99),
                        new SearchResult("doc-x", 2, 0.85),
                        new SearchResult("doc-2", 3, 0.75),
                        new SearchResult("doc-y", 4, 0.65)
                )
        );

        MetricResult result = new JudgedAtK(4).computeFrom(judgments, results);

        assertThat(result.perQueryValues().get("running shoes")).isCloseTo(0.5, within(1e-9));
        assertThat(result.overallValue()).isCloseTo(0.5, within(1e-9));
    }

    /**
     * Hand-calculated reasoning:
     * - Query: "running shoes"
     * - Judgments: doc-zero (grade 0, irrelevant but judged), doc-two (grade 2)
     * - Top-2 returned: [doc-zero, doc-two]
     * - Evaluated positions: min(k=2, results.size()=2) = 2
     * - Judged documents in top-2: both doc-zero and doc-two have judgments = 2 judged
     * - Note: In Precision@2, grade 0 would contribute 0 (giving 1/2 = 0.50).
     *   In Judged@2, grade 0 explicitly counts as judged, giving 2/2 = 1.0.
     * - Score: 2 / 2 = 1.0
     * - Overall score: 1.0 / 1 = 1.0
     */
    @Test
    void gradeZeroDocumentCountsAsJudged() {
        JudgmentSet judgments = new JudgmentSet("test", List.of(
                new Judgment("running shoes", "doc-zero", 0),
                new Judgment("running shoes", "doc-two", 2)
        ));

        Map<String, List<SearchResult>> results = Map.of(
                "running shoes", List.of(
                        new SearchResult("doc-zero", 1, 0.95),
                        new SearchResult("doc-two", 2, 0.80)
                )
        );

        MetricResult result = new JudgedAtK(2).computeFrom(judgments, results);

        assertThat(result.perQueryValues().get("running shoes")).isCloseTo(1.0, within(1e-9));
        assertThat(result.overallValue()).isCloseTo(1.0, within(1e-9));
    }

    /**
     * Hand-calculated reasoning:
     * - Query: "empty query"
     * - Judgments: doc-1 (grade 3)
     * - Returned: [] (no results returned)
     * - Evaluated positions: 0
     * - Specified edge case: when no results are returned, value is defined as 0.0
     * - Score: 0.0
     * - Overall score: 0.0 / 1 = 0.0
     */
    @Test
    void queryReturningNothingYieldsZero() {
        JudgmentSet judgments = new JudgmentSet("test", List.of(
                new Judgment("empty query", "doc-1", 3)
        ));

        Map<String, List<SearchResult>> results = Map.of(
                "empty query", List.of()
        );

        MetricResult result = new JudgedAtK(10).computeFrom(judgments, results);

        assertThat(result.perQueryValues().get("empty query")).isCloseTo(0.0, within(1e-9));
        assertThat(result.overallValue()).isCloseTo(0.0, within(1e-9));
    }

    /**
     * Hand-calculated reasoning:
     * - Query: "running shoes"
     * - Cutoff: k = 10
     * - Judgments: doc-1 (grade 3), doc-2 (grade 2)
     * - Returned: [doc-1, doc-2] (only 2 results returned)
     * - Denominator: min(k=10, results.size()=2) = 2 (NOT k=10)
     * - Judged documents in top-k: doc-1, doc-2 = 2 judged
     * - Score: 2 / 2 = 1.0 (if divided by k=10, it would falsely report 2/10 = 0.20)
     * - Overall score: 1.0 / 1 = 1.0
     */
    @Test
    void fewerResultsThanKDoesNotDistortValue() {
        JudgmentSet judgments = new JudgmentSet("test", List.of(
                new Judgment("running shoes", "doc-1", 3),
                new Judgment("running shoes", "doc-2", 2)
        ));

        Map<String, List<SearchResult>> results = Map.of(
                "running shoes", List.of(
                        new SearchResult("doc-1", 1, 0.99),
                        new SearchResult("doc-2", 2, 0.95)
                )
        );

        MetricResult result = new JudgedAtK(10).computeFrom(judgments, results);

        assertThat(result.perQueryValues().get("running shoes")).isCloseTo(1.0, within(1e-9));
        assertThat(result.overallValue()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void rejectsInvalidCutoff() {
        assertThatThrownBy(() -> new JudgedAtK(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("k must be positive");

        assertThatThrownBy(() -> new JudgedAtK(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("k must be positive");
    }
}
