package io.github.vishwassp01.relevanceeval.clicks;

import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ClickJudgmentBuilderTest {

    private Optional<Judgment> findJudgment(JudgmentSet set, String query, String docId) {
        return set.judgmentsFor(query).stream()
                .filter(j -> j.docId().equals(docId))
                .findFirst();
    }

    @Test
    @DisplayName("1. Rank-1 vs rank-10 with same clicks and impressions: rank-10 scores higher")
    void rank1VsRank10SameClicksAndImpressions() {
        // doc A: 20 impressions at rank 1, 2 clicks
        //   -> (1/20) * 2 * (1/1.0) = 0.10 -> grade 1
        // doc B: 20 impressions at rank 10, 2 clicks
        //   -> (1/20) * 2 * (1/0.1) = 1.00 -> grade 3
        String query = "dslr camera";
        String docA = "doc-rank1";
        String docB = "doc-rank10";

        List<ClickEvent> events = new ArrayList<>();
        // doc A: 2 clicks, 18 non-clicks at rank 1
        for (int i = 0; i < 20; i++) {
            events.add(new ClickEvent(query, docA, 1, i < 2));
        }
        // doc B: 2 clicks, 18 non-clicks at rank 10
        for (int i = 0; i < 20; i++) {
            events.add(new ClickEvent(query, docB, 10, i < 2));
        }

        ClickJudgmentBuilder builder = new ClickJudgmentBuilder(new PositionBasedPropensity(1.0), 10, 0.1);
        JudgmentSet judgmentSet = builder.build(events);

        Map<QueryDocPair, Double> rates = builder.computeCorrectedRates(events);
        double rateA = rates.get(new QueryDocPair(query, docA));
        double rateB = rates.get(new QueryDocPair(query, docB));

        // Assert exact values and that B > A
        assertThat(rateA).isCloseTo(0.10, within(1e-9));
        assertThat(rateB).isCloseTo(1.00, within(1e-9));
        assertThat(rateB).isGreaterThan(rateA);

        Optional<Judgment> judgmentA = findJudgment(judgmentSet, query, docA);
        Optional<Judgment> judgmentB = findJudgment(judgmentSet, query, docB);

        assertThat(judgmentA).isPresent();
        assertThat(judgmentB).isPresent();
        assertThat(judgmentA.get().grade()).isEqualTo(1);
        assertThat(judgmentB.get().grade()).isEqualTo(3);
        assertThat(judgmentB.get().grade()).isGreaterThan(judgmentA.get().grade());
    }

    @Test
    @DisplayName("2. eta = 0 disables the correction and correctedRate == raw CTR")
    void etaZeroDisablesCorrectionAndEqualsRawCtr() {
        // Every propensity becomes 1.0, so correctedRate == raw CTR.
        // doc: 20 impressions, 2 clicks -> exactly 0.10.
        PositionBasedPropensity unbiasedPropensity = new PositionBasedPropensity(0.0);
        ClickJudgmentBuilder builder = new ClickJudgmentBuilder(unbiasedPropensity, 10, 0.1);

        String query = "laptop bag";
        String doc = "bag-42";

        List<ClickEvent> events = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            // Various positions 1 to 10
            events.add(new ClickEvent(query, doc, (i % 10) + 1, i < 2));
        }

        double correctedRate = builder.computeCorrectedRate(events);
        double rawCtr = 2.0 / 20.0; // 0.10

        // Assert exactly 0.10
        assertThat(correctedRate).isEqualTo(0.10);
        assertThat(correctedRate).isEqualTo(rawCtr);

        JudgmentSet judgmentSet = builder.build(events);
        Optional<Judgment> judgment = findJudgment(judgmentSet, query, doc);
        assertThat(judgment).isPresent();
        assertThat(judgment.get().grade()).isEqualTo(1);
    }

    @Test
    @DisplayName("3. The propensity floor actually bites: caps weight at 10 instead of 100")
    void propensityFloorActuallyBites() {
        // doc: 100 impressions at rank 100, 1 click.
        // Unclipped propensity would be 0.01 (weight 100), giving 1.00.
        // With floor 0.1 the weight is capped at 10, giving (1/100) * 1 * 10 = 0.10.
        // Assert 0.10, not 1.00.
        ClickJudgmentBuilder builder = new ClickJudgmentBuilder(new PositionBasedPropensity(1.0), 10, 0.1);

        String query = "monitor stand";
        String doc = "stand-100";

        List<ClickEvent> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(new ClickEvent(query, doc, 100, i == 0));
        }

        double correctedRate = builder.computeCorrectedRate(events);
        assertThat(correctedRate).isCloseTo(0.10, within(1e-9));
        assertThat(correctedRate).isNotEqualTo(1.00);

        JudgmentSet judgmentSet = builder.build(events);
        Optional<Judgment> judgment = findJudgment(judgmentSet, query, doc);
        assertThat(judgment).isPresent();
        assertThat(judgment.get().grade()).isEqualTo(1);
    }

    @Test
    @DisplayName("4. Clamping: correctedRate is capped at 1.00")
    void clampingCapsRateAtOne() {
        // doc: 10 impressions at rank 10, 5 clicks
        //   -> (1/10) * 5 * 10 = 5.0, clamped to 1.00 -> grade 3.
        ClickJudgmentBuilder builder = new ClickJudgmentBuilder(new PositionBasedPropensity(1.0), 10, 0.1);

        String query = "ergonomic chair";
        String doc = "chair-7";

        List<ClickEvent> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(new ClickEvent(query, doc, 10, i < 5));
        }

        double correctedRate = builder.computeCorrectedRate(events);
        assertThat(correctedRate).isEqualTo(1.00);

        JudgmentSet judgmentSet = builder.build(events);
        Optional<Judgment> judgment = findJudgment(judgmentSet, query, doc);
        assertThat(judgment).isPresent();
        assertThat(judgment.get().grade()).isEqualTo(3);
    }

    @Test
    @DisplayName("5. Zero clicks -> rate 0.0 -> grade 0")
    void zeroClicksYieldsRateZeroAndGradeZero() {
        ClickJudgmentBuilder builder = new ClickJudgmentBuilder(10);
        String query = "mechanical keyboard";
        String doc = "keyboard-99";

        List<ClickEvent> events = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            events.add(new ClickEvent(query, doc, (i % 5) + 1, false));
        }

        double correctedRate = builder.computeCorrectedRate(events);
        assertThat(correctedRate).isEqualTo(0.0);

        JudgmentSet judgmentSet = builder.build(events);
        Optional<Judgment> judgment = findJudgment(judgmentSet, query, doc);
        assertThat(judgment).isPresent();
        assertThat(judgment.get().grade()).isEqualTo(0);
    }

    @Test
    @DisplayName("6. Below minImpressions -> absent from the JudgmentSet entirely")
    void belowMinImpressionsAbsentFromJudgmentSet() {
        int minImpressions = 10;
        ClickJudgmentBuilder builder = new ClickJudgmentBuilder(minImpressions);

        String query = "hiking boots";
        String docUnderThreshold = "boots-rare";
        String docMeetingThreshold = "boots-common";

        List<ClickEvent> events = new ArrayList<>();
        // 9 impressions (below minImpressions), all clicked (100% CTR)
        for (int i = 1; i <= 9; i++) {
            events.add(new ClickEvent(query, docUnderThreshold, 1, true));
        }
        // 10 impressions (meets minImpressions), 4 clicked
        for (int i = 1; i <= 10; i++) {
            events.add(new ClickEvent(query, docMeetingThreshold, 1, i <= 4));
        }

        JudgmentSet judgmentSet = builder.build(events);

        // Under-threshold pair must be absent entirely
        assertThat(findJudgment(judgmentSet, query, docUnderThreshold)).isEmpty();

        // Meeting-threshold pair must be retained
        assertThat(findJudgment(judgmentSet, query, docMeetingThreshold)).isPresent();
        assertThat(judgmentSet.judgments()).hasSize(1);
    }

    @Test
    @DisplayName("7. Empty event list -> empty JudgmentSet, no exception")
    void emptyEventListProducesEmptyJudgmentSet() {
        ClickJudgmentBuilder builder = new ClickJudgmentBuilder();

        JudgmentSet emptyListResult = builder.build(List.of());
        assertThat(emptyListResult).isNotNull();
        assertThat(emptyListResult.judgments()).isEmpty();

        JudgmentSet nullListResult = builder.build(null);
        assertThat(nullListResult).isNotNull();
        assertThat(nullListResult.judgments()).isEmpty();

        assertThat(builder.computeCorrectedRate(List.of())).isEqualTo(0.0);
        assertThat(builder.computeCorrectedRate(null)).isEqualTo(0.0);
        assertThat(builder.computeCorrectedRates(List.of())).isEmpty();
        assertThat(builder.computeCorrectedRates(null)).isEmpty();
    }

    @Test
    @DisplayName("8. ClickEvent rejects position 0 and negative positions")
    void clickEventRejectsInvalidPositions() {
        assertThatThrownBy(() -> new ClickEvent("query", "doc1", 0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position must be >= 1");

        assertThatThrownBy(() -> new ClickEvent("query", "doc1", -1, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position must be >= 1");

        assertThatThrownBy(() -> new ClickEvent("query", "doc1", -100, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position must be >= 1");
    }

    @Test
    @DisplayName("ClickEvent rejects null or blank query and documentId")
    void clickEventRejectsNullOrBlankIdentifiers() {
        assertThatThrownBy(() -> new ClickEvent(null, "doc1", 1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClickEvent("   ", "doc1", 1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClickEvent("query", null, 1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClickEvent("query", "   ", 1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PositionBasedPropensity validates eta and position")
    void positionBasedPropensityValidation() {
        assertThatThrownBy(() -> new PositionBasedPropensity(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eta must be non-negative");

        PositionBasedPropensity p = new PositionBasedPropensity(1.0);
        assertThat(p.propensityAt(1)).isEqualTo(1.0);
        assertThat(p.propensityAt(2)).isEqualTo(0.5);
        assertThat(p.propensityAt(4)).isEqualTo(0.25);
        assertThat(p.propensityAt(10)).isEqualTo(0.1);

        assertThatThrownBy(() -> p.propensityAt(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position must be >= 1");

        assertThatThrownBy(() -> p.propensityAt(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position must be >= 1");
    }

    @Test
    @DisplayName("GradeThresholds validates strictly decreasing positive thresholds in [0, 1]")
    void gradeThresholdsValidation() {
        assertThatThrownBy(() -> new GradeThresholds(0.20, 0.40, 0.10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradeThresholds(0.50, 0.10, 0.20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradeThresholds(1.50, 0.50, 0.10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradeThresholds(0.50, 0.25, -0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradeThresholds(Double.NaN, 0.25, 0.10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ClickJudgmentBuilder validates propensityFloor")
    void clickJudgmentBuilderPropensityFloorValidation() {
        assertThatThrownBy(() -> new ClickJudgmentBuilder(new PositionBasedPropensity(1.0), 10, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propensityFloor");

        assertThatThrownBy(() -> new ClickJudgmentBuilder(new PositionBasedPropensity(1.0), 10, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propensityFloor");

        assertThatThrownBy(() -> new ClickJudgmentBuilder(new PositionBasedPropensity(1.0), 10, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propensityFloor");

        assertThatThrownBy(() -> new ClickJudgmentBuilder(new PositionBasedPropensity(1.0), 10, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propensityFloor");
    }
}
