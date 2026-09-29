package io.github.vishwassp01.relevanceeval.clicks;

import io.github.vishwassp01.relevanceeval.model.Judgment;
import io.github.vishwassp01.relevanceeval.model.JudgmentSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Derives ground-truth {@link JudgmentSet} instances from historical search click logs using
 * Inverse Propensity Scoring (IPS) for position-bias correction.
 * <p>
 * <b>Algorithm:</b>
 * For each {@code (query, documentId)} pair with \(N\) raw impressions:
 * <ol>
 *   <li>{@code correctedRate = (1.0 / N) * sum over impressions of (clicked ? 1.0 / effectivePropensity(position) : 0.0)}</li>
 *   <li>{@code effectivePropensity(position) = Math.max(propensityAt(position), propensityFloor)}</li>
 *   <li>{@code clampedRate = Math.min(1.0, correctedRate)}</li>
 *   <li>Drop the pair entirely if raw impression count \(N &lt; \text{minImpressions}\) (counting raw impressions, not weights).</li>
 * </ol>
 * Pairs meeting the impression threshold are mapped to 0–3 grades according to configured thresholds.
 * <p>
 * <b>Why the propensity floor exists:</b><br>
 * Inverse propensity weighting has unbounded variance as the propensity approaches zero — a single click on a result
 * shown once at rank 200 would otherwise swamp hundreds of honest observations. The floor caps how much any one
 * observation can be worth. This is propensity clipping, and it trades a small amount of bias for a large reduction
 * in variance.
 */
public class ClickJudgmentBuilder {

    public static final String DEFAULT_JUDGMENT_SET_NAME = "click-derived-judgments";
    public static final int DEFAULT_MIN_IMPRESSIONS = 10;
    public static final double DEFAULT_PROPENSITY_FLOOR = 0.1;

    private final PropensityModel propensityModel;
    private final int minImpressions;
    private final double propensityFloor;
    private final GradeThresholds thresholds;

    /**
     * Constructs a {@code ClickJudgmentBuilder} with defaults:
     * {@link PositionBasedPropensity} (eta = 1.0), 10 minimum impressions, propensity floor 0.1,
     * and default thresholds (0.50, 0.25, 0.10).
     */
    public ClickJudgmentBuilder() {
        this(new PositionBasedPropensity(), DEFAULT_MIN_IMPRESSIONS, DEFAULT_PROPENSITY_FLOOR, GradeThresholds.DEFAULT);
    }

    /**
     * Constructs a {@code ClickJudgmentBuilder} with the specified minimum impressions and defaults for others.
     *
     * @param minImpressions minimum raw impression count required to retain a (query, document) pair
     */
    public ClickJudgmentBuilder(int minImpressions) {
        this(new PositionBasedPropensity(), minImpressions, DEFAULT_PROPENSITY_FLOOR, GradeThresholds.DEFAULT);
    }

    /**
     * Constructs a {@code ClickJudgmentBuilder} with the specified propensity model and minimum impressions.
     *
     * @param propensityModel examination propensity model
     * @param minImpressions  minimum raw impression count required
     */
    public ClickJudgmentBuilder(PropensityModel propensityModel, int minImpressions) {
        this(propensityModel, minImpressions, DEFAULT_PROPENSITY_FLOOR, GradeThresholds.DEFAULT);
    }

    /**
     * Constructs a {@code ClickJudgmentBuilder} with the specified propensity model, minimum impressions,
     * and propensity clipping floor.
     *
     * @param propensityModel examination propensity model
     * @param minImpressions  minimum raw impression count required
     * @param propensityFloor minimum propensity floor for inverse weighting (propensity clipping)
     */
    public ClickJudgmentBuilder(PropensityModel propensityModel, int minImpressions, double propensityFloor) {
        this(propensityModel, minImpressions, propensityFloor, GradeThresholds.DEFAULT);
    }

    /**
     * Constructs a {@code ClickJudgmentBuilder} with custom propensity model, minimum impressions, and grade thresholds.
     *
     * @param propensityModel examination propensity model, must not be null
     * @param minImpressions  minimum raw impression count required (must be non-negative)
     * @param thresholds      grade boundary thresholds, must not be null
     */
    public ClickJudgmentBuilder(PropensityModel propensityModel, int minImpressions, GradeThresholds thresholds) {
        this(propensityModel, minImpressions, DEFAULT_PROPENSITY_FLOOR, thresholds);
    }

    /**
     * Constructs a {@code ClickJudgmentBuilder} with custom propensity model, minimum impressions, propensity floor,
     * and grade thresholds.
     *
     * @param propensityModel examination propensity model, must not be null
     * @param minImpressions  minimum raw impression count required (must be non-negative)
     * @param propensityFloor minimum propensity floor for inverse weighting (must be in (0.0, 1.0])
     * @param thresholds      grade boundary thresholds, must not be null
     */
    public ClickJudgmentBuilder(PropensityModel propensityModel, int minImpressions,
                                double propensityFloor, GradeThresholds thresholds) {
        this.propensityModel = Objects.requireNonNull(propensityModel, "propensityModel must not be null");
        if (minImpressions < 0) {
            throw new IllegalArgumentException("minImpressions must be non-negative, but was: " + minImpressions);
        }
        if (propensityFloor <= 0.0 || propensityFloor > 1.0 || Double.isNaN(propensityFloor)) {
            throw new IllegalArgumentException("propensityFloor must be in (0.0, 1.0], but was: " + propensityFloor);
        }
        this.minImpressions = minImpressions;
        this.propensityFloor = propensityFloor;
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds must not be null");
    }

    /**
     * Constructs a {@code ClickJudgmentBuilder} with custom parameters and explicit threshold values.
     *
     * @param propensityModel examination propensity model, must not be null
     * @param minImpressions  minimum raw impression count required
     * @param propensityFloor minimum propensity floor for inverse weighting (propensity clipping)
     * @param grade3Threshold minimum corrected rate for grade 3
     * @param grade2Threshold minimum corrected rate for grade 2
     * @param grade1Threshold minimum corrected rate for grade 1
     */
    public ClickJudgmentBuilder(PropensityModel propensityModel, int minImpressions, double propensityFloor,
                                double grade3Threshold, double grade2Threshold, double grade1Threshold) {
        this(propensityModel, minImpressions, propensityFloor,
                new GradeThresholds(grade3Threshold, grade2Threshold, grade1Threshold));
    }

    /**
     * Derives a {@link JudgmentSet} from the provided click log events using the default judgment set name.
     *
     * @param events list of click and impression events
     * @return a {@link JudgmentSet} containing graded relevance judgments for pairs meeting {@code minImpressions}
     */
    public JudgmentSet build(List<ClickEvent> events) {
        return build(DEFAULT_JUDGMENT_SET_NAME, events);
    }

    /**
     * Derives a named {@link JudgmentSet} from the provided click log events.
     *
     * @param name   name for the resulting judgment set
     * @param events list of click and impression events
     * @return a {@link JudgmentSet} containing graded relevance judgments for pairs meeting {@code minImpressions}
     */
    public JudgmentSet build(String name, List<ClickEvent> events) {
        String setName = (name != null && !name.isBlank()) ? name : DEFAULT_JUDGMENT_SET_NAME;
        if (events == null || events.isEmpty()) {
            return new JudgmentSet(setName, List.of());
        }

        Map<QueryDocPair, List<ClickEvent>> grouped = groupEvents(events);
        List<Judgment> judgments = new ArrayList<>();

        for (Map.Entry<QueryDocPair, List<ClickEvent>> entry : grouped.entrySet()) {
            QueryDocPair pair = entry.getKey();
            List<ClickEvent> pairEvents = entry.getValue();

            // Drop the pair entirely if raw impression COUNT < minImpressions
            if (pairEvents.size() < minImpressions) {
                continue;
            }

            double correctedRate = computeCorrectedRate(pairEvents);
            int grade = thresholds.toGrade(correctedRate);
            judgments.add(new Judgment(pair.query(), pair.documentId(), grade));
        }

        return new JudgmentSet(setName, judgments);
    }

    /**
     * Computes the position-bias-corrected click-through rate for a list of impression events
     * associated with a single (query, document) pair.
     * <p>
     * For \(N\) raw impressions:
     * <pre>
     *   correctedRate = (1.0 / N) * sum over impressions of (clicked ? 1.0 / effectivePropensity(position) : 0.0)
     *   effectivePropensity(position) = Math.max(propensityAt(position), propensityFloor)
     *   clampedRate = Math.min(1.0, correctedRate)
     * </pre>
     *
     * @param events the events for a query-document pair
     * @return the corrected click rate clamped to [0.0, 1.0], or 0.0 if empty
     */
    public double computeCorrectedRate(List<ClickEvent> events) {
        if (events == null || events.isEmpty()) {
            return 0.0;
        }

        int n = events.size();
        double weightedClicks = 0.0;

        for (ClickEvent e : events) {
            if (e.clicked()) {
                double rawPropensity = propensityModel.propensityAt(e.position());
                double effectivePropensity = Math.max(rawPropensity, propensityFloor);
                weightedClicks += (1.0 / effectivePropensity);
            }
        }

        double correctedRate = (1.0 / n) * weightedClicks;
        return Math.min(1.0, correctedRate);
    }

    /**
     * Computes corrected click-through rates for all (query, document) pairs that meet {@code minImpressions}.
     *
     * @param events list of click events
     * @return an unmodifiable map of (query, documentId) pairs to corrected click rates
     */
    public Map<QueryDocPair, Double> computeCorrectedRates(List<ClickEvent> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        Map<QueryDocPair, List<ClickEvent>> grouped = groupEvents(events);
        Map<QueryDocPair, Double> rates = new LinkedHashMap<>();

        for (Map.Entry<QueryDocPair, List<ClickEvent>> entry : grouped.entrySet()) {
            if (entry.getValue().size() >= minImpressions) {
                rates.put(entry.getKey(), computeCorrectedRate(entry.getValue()));
            }
        }

        return Collections.unmodifiableMap(rates);
    }

    /**
     * Computes corrected click-through rates for all (query, document) pairs regardless of impression count.
     *
     * @param events list of click events
     * @return an unmodifiable map of (query, documentId) pairs to corrected click rates
     */
    public Map<QueryDocPair, Double> computeAllCorrectedRates(List<ClickEvent> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        Map<QueryDocPair, List<ClickEvent>> grouped = groupEvents(events);
        Map<QueryDocPair, Double> rates = new LinkedHashMap<>();

        for (Map.Entry<QueryDocPair, List<ClickEvent>> entry : grouped.entrySet()) {
            rates.put(entry.getKey(), computeCorrectedRate(entry.getValue()));
        }

        return Collections.unmodifiableMap(rates);
    }

    private Map<QueryDocPair, List<ClickEvent>> groupEvents(List<ClickEvent> events) {
        Map<QueryDocPair, List<ClickEvent>> grouped = new LinkedHashMap<>();
        for (ClickEvent event : events) {
            if (event != null) {
                QueryDocPair key = new QueryDocPair(event.query(), event.documentId());
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
            }
        }
        return grouped;
    }

    public PropensityModel propensityModel() {
        return propensityModel;
    }

    public int minImpressions() {
        return minImpressions;
    }

    public double propensityFloor() {
        return propensityFloor;
    }

    public GradeThresholds thresholds() {
        return thresholds;
    }
}
