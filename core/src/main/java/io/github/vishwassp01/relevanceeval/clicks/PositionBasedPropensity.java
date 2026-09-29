package io.github.vishwassp01.relevanceeval.clicks;

/**
 * A position-based examination propensity model parameterized by a power-law decay parameter {@code eta}.
 * <p>
 * The propensity at 1-based position \(k\) is computed as:
 * <pre>
 *   propensityAt(position) = Math.pow(1.0 / position, eta)
 * </pre>
 * <p>
 * <b>Important Notice:</b><br>
 * This model uses an <b>assumed</b> propensity curve with a fixed decay parameter (defaulting to {@code eta = 1.0}),
 * rather than a curve estimated empirically from observed click data. In real-world retrieval applications, estimating
 * true position propensities requires interventions such as result randomization (e.g. interleaving, random swaps)
 * or expectation-maximization (EM) based approaches. Using this predefined power-law curve is a deliberate simplification
 * that the caller should be aware of when interpreting position-bias-corrected metrics and derived judgments.
 */
public class PositionBasedPropensity implements PropensityModel {

    public static final double DEFAULT_ETA = 1.0;

    private final double eta;

    /**
     * Constructs a {@code PositionBasedPropensity} model with the default decay exponent {@code eta = 1.0}.
     */
    public PositionBasedPropensity() {
        this(DEFAULT_ETA);
    }

    /**
     * Constructs a {@code PositionBasedPropensity} model with the specified decay parameter.
     *
     * @param eta the power-law decay exponent (must be non-negative)
     */
    public PositionBasedPropensity(double eta) {
        if (eta < 0.0 || Double.isNaN(eta)) {
            throw new IllegalArgumentException("eta must be non-negative, but was: " + eta);
        }
        this.eta = eta;
    }

    @Override
    public double propensityAt(int position) {
        if (position < 1) {
            throw new IllegalArgumentException("position must be >= 1, but was: " + position);
        }
        return Math.pow(1.0 / position, eta);
    }

    /**
     * Returns the decay parameter {@code eta}.
     *
     * @return the power-law decay exponent
     */
    public double eta() {
        return eta;
    }
}
