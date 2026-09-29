package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import io.github.vishwassp01.relevanceeval.model.MetricResult;
import io.github.vishwassp01.relevanceeval.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultNormalizerTest {

    // =========================================================================
    // GAP 1: Deduplication Tests
    // =========================================================================

    @Test
    void sameDocIdAtRanks1And3ProducesSameNdcgAsRank3Removed() {
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "doc-1", 3),
                new Judgment("q1", "doc-2", 2),
                new Judgment("q1", "doc-3", 1)
        ));

        // Result list with doc-1 at rank 1 and duplicate doc-1 at rank 3
        List<SearchResult> resultsWithDuplicate = List.of(
                new SearchResult("doc-1", 1, 0.95),
                new SearchResult("doc-2", 2, 0.85),
                new SearchResult("doc-1", 3, 0.75), // duplicate doc-1
                new SearchResult("doc-3", 4, 0.65)
        );

        // Result list with rank 3 removed
        List<SearchResult> resultsWithoutDuplicate = List.of(
                new SearchResult("doc-1", 1, 0.95),
                new SearchResult("doc-2", 2, 0.85),
                new SearchResult("doc-3", 3, 0.65)
        );

        Metric ndcg = new NdcgAtK(5);
        MetricResult resultWithDup = ndcg.computeFrom(judgments, Map.of("q1", resultsWithDuplicate));
        MetricResult resultWithoutDup = ndcg.computeFrom(judgments, Map.of("q1", resultsWithoutDuplicate));

        assertThat(resultWithDup.overallValue())
                .isEqualTo(resultWithoutDup.overallValue());
        assertThat(resultWithDup.perQueryValues().get("q1"))
                .isEqualTo(resultWithoutDup.perQueryValues().get("q1"));
    }

    @Test
    void recallAtKNeverExceedsOneEvenWhenListContainsDuplicates() {
        // Query has only 1 relevant document in judgments
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "doc-1", 1)
        ));

        // Result list returns the same relevant document 4 times, plus duplicates of an unjudged doc
        List<SearchResult> resultsWithDuplicates = List.of(
                new SearchResult("doc-1", 1, 0.99),
                new SearchResult("doc-1", 2, 0.88),
                new SearchResult("doc-1", 3, 0.77),
                new SearchResult("doc-1", 4, 0.66),
                new SearchResult("doc-unjudged", 5, 0.55),
                new SearchResult("doc-unjudged", 6, 0.44)
        );

        Metric recall = new RecallAtK(10);
        MetricResult result = recall.computeFrom(judgments, Map.of("q1", resultsWithDuplicates));

        assertThat(result.overallValue()).isLessThanOrEqualTo(1.0);
        assertThat(result.overallValue()).isEqualTo(1.0);
        assertThat(result.perQueryValues().get("q1")).isEqualTo(1.0);
    }

    @Test
    void dedupKeepsFirstOccurrenceNotLast() {
        List<SearchResult> input = List.of(
                new SearchResult("doc-first", 1, 10.0),
                new SearchResult("doc-other", 2, 8.0),
                new SearchResult("doc-first", 3, 6.0) // duplicate with lower score and later rank
        );

        List<SearchResult> normalized = ResultNormalizer.normalize(input);

        // Resulting rank order must keep the first occurrence: doc-first at rank 1, doc-other at rank 2
        assertThat(normalized).hasSize(2);
        assertThat(normalized.get(0).docId()).isEqualTo("doc-first");
        assertThat(normalized.get(0).rank()).isEqualTo(1);
        assertThat(normalized.get(0).score()).isEqualTo(10.0);

        assertThat(normalized.get(1).docId()).isEqualTo("doc-other");
        assertThat(normalized.get(1).rank()).isEqualTo(2);
        assertThat(normalized.get(1).score()).isEqualTo(8.0);
    }

    // =========================================================================
    // GAP 2: Tie-breaking Tests
    // =========================================================================

    @Test
    void differentInputOrdersWithSameScoresProduceIdenticalNdcg() {
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "doc-A", 3),
                new Judgment("q1", "doc-B", 1)
        ));

        // Order 1: doc-B before doc-A, both tied with score 0.80
        List<SearchResult> list1 = List.of(
                new SearchResult("doc-B", 1, 0.80),
                new SearchResult("doc-A", 2, 0.80)
        );

        // Order 2: doc-A before doc-B, both tied with score 0.80
        List<SearchResult> list2 = List.of(
                new SearchResult("doc-A", 1, 0.80),
                new SearchResult("doc-B", 2, 0.80)
        );

        Metric ndcg = new NdcgAtK(5);
        MetricResult result1 = ndcg.computeFrom(judgments, Map.of("q1", list1));
        MetricResult result2 = ndcg.computeFrom(judgments, Map.of("q1", list2));

        assertThat(result1.overallValue())
                .isEqualTo(result2.overallValue());
        assertThat(result1.perQueryValues().get("q1"))
                .isEqualTo(result2.perQueryValues().get("q1"));
    }

    @Test
    void tiesAreBrokenByDocumentIdAscending() {
        // All three documents have the exact same score 5.0, provided in non-alphabetical order
        List<SearchResult> input = List.of(
                new SearchResult("charlie", 1, 5.0),
                new SearchResult("alice", 2, 5.0),
                new SearchResult("bob", 3, 5.0)
        );

        List<SearchResult> normalized = ResultNormalizer.normalize(input);

        // Ties must be broken by document ID ascending: alice, bob, charlie
        assertThat(normalized).extracting(SearchResult::docId)
                .containsExactly("alice", "bob", "charlie");
        assertThat(normalized.get(0).rank()).isEqualTo(1);
        assertThat(normalized.get(1).rank()).isEqualTo(2);
        assertThat(normalized.get(2).rank()).isEqualTo(3);
    }

    @Test
    void listWithNoTiesIsLeftInItsOriginalOrder() {
        // Scores are strictly descending (9.0 > 8.0 > 7.0), no ties exist
        // Note document IDs are intentionally NOT in alphabetical order (charlie > alice < bob)
        List<SearchResult> input = List.of(
                new SearchResult("charlie", 1, 9.0),
                new SearchResult("alice", 2, 8.0),
                new SearchResult("bob", 3, 7.0)
        );

        List<SearchResult> normalized = ResultNormalizer.normalize(input);

        // The list must remain in its original score-descending order without docId tie-breaking
        assertThat(normalized).extracting(SearchResult::docId)
                .containsExactly("charlie", "alice", "bob");
        assertThat(normalized.get(0).score()).isEqualTo(9.0);
        assertThat(normalized.get(1).score()).isEqualTo(8.0);
        assertThat(normalized.get(2).score()).isEqualTo(7.0);
    }

    // =========================================================================
    // Additional Coverage on other metrics
    // =========================================================================

    @Test
    void precisionAtKExcludesDuplicateResultsFromNumerator() {
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "doc-1", 2)
        ));

        // doc-1 is repeated 5 times; precision at 5 should be 1 / 5 = 0.20, not 5 / 5 = 1.0
        List<SearchResult> results = List.of(
                new SearchResult("doc-1", 1, 0.9),
                new SearchResult("doc-1", 2, 0.8),
                new SearchResult("doc-1", 3, 0.7),
                new SearchResult("doc-1", 4, 0.6),
                new SearchResult("doc-1", 5, 0.5)
        );

        Metric precision = new PrecisionAtK(5);
        MetricResult result = precision.computeFrom(judgments, Map.of("q1", results));

        assertThat(result.overallValue()).isEqualTo(0.20);
    }

    @Test
    void meanReciprocalRankAccountsForTieBreakingAndDeduplication() {
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "doc-relevant", 2)
        ));

        // doc-tied and doc-relevant both have score 8.0.
        // Alphabetically, "doc-relevant" comes after "doc-other"
        List<SearchResult> results = List.of(
                new SearchResult("doc-other", 1, 8.0),
                new SearchResult("doc-relevant", 2, 8.0)
        );

        Metric mrr = new MeanReciprocalRank();
        MetricResult result = mrr.computeFrom(judgments, Map.of("q1", results));

        // "doc-other" < "doc-relevant", so doc-other is rank 1, doc-relevant is rank 2 -> RR = 1/2 = 0.5
        assertThat(result.overallValue()).isEqualTo(0.5);
    }

    @Test
    void judgedAtKDoesNotInflateDenominatorWithDuplicates() {
        JudgmentSet judgments = new JudgmentSet("test-set", List.of(
                new Judgment("q1", "doc-1", 3)
        ));

        // 3 duplicates of doc-1; denominator should be min(k=10, deduplicated=1) = 1, score = 1/1 = 1.0
        List<SearchResult> results = List.of(
                new SearchResult("doc-1", 1, 0.99),
                new SearchResult("doc-1", 2, 0.88),
                new SearchResult("doc-1", 3, 0.77)
        );

        Metric judged = new JudgedAtK(10);
        MetricResult result = judged.computeFrom(judgments, Map.of("q1", results));

        assertThat(result.overallValue()).isEqualTo(1.0);
    }
}
