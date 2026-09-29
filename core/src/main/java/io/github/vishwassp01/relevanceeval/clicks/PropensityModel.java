package io.github.vishwassp01.relevanceeval.clicks;

/**
 * Strategy interface for modeling position-dependent examination probabilities (propensities)
 * in search result presentation.
 */
@FunctionalInterface
public interface PropensityModel {

    /**
     * Returns the examination propensity for a given 1-based rank position.
     *
     * @param position 1-based rank position as shown to the user (must be &gt;= 1)
     * @return the probability of examination at the given position
     */
    double propensityAt(int position);
}
