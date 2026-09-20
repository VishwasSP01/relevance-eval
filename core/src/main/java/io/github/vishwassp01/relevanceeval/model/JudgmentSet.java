package io.github.vishwassp01.relevanceeval.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a named collection of ground-truth {@link Judgment} instances.
 * <p>
 * This record aggregates judgments into test suites or benchmark datasets, providing
 * convenient query-level indexing and lookup methods used during relevance evaluation runs.
 *
 * @param name      the descriptive name of this judgment set, must not be null or blank
 * @param judgments the list of relevance judgments, defensively copied and must not contain null elements
 */
public record JudgmentSet(String name, List<Judgment> judgments) {

    public JudgmentSet {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        judgments = List.copyOf(judgments);
    }

    /**
     * Returns the distinct queries present in this judgment set.
     *
     * @return an unmodifiable set of distinct query strings preserving insertion order
     */
    public Set<String> queries() {
        return judgments.stream()
                .map(Judgment::query)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        Collections::unmodifiableSet
                ));
    }

    /**
     * Returns all judgments corresponding to the specified query.
     *
     * @param query the query to filter judgments for, must not be null
     * @return an unmodifiable list of judgments matching the query
     */
    public List<Judgment> judgmentsFor(String query) {
        Objects.requireNonNull(query, "query must not be null");
        return judgments.stream()
                .filter(j -> j.query().equals(query))
                .toList();
    }
}
