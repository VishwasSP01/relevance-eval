package io.github.vishwassp01.relevanceeval.model;

/**
 * Represents a single search result returned by an information retrieval system.
 * <p>
 * This record captures a retrieved document identifier alongside its 1-based rank position
 * and engine-assigned retrieval score, serving as the actual candidate output to be evaluated
 * against benchmark ground-truth judgments.
 *
 * @param docId the identifier of the retrieved document, must not be null or blank
 * @param rank  the 1-based rank position of the result in the search response, must be positive (&gt;= 1)
 * @param score the relevance or matching score assigned by the search engine
 */
public record SearchResult(String docId, int rank, double score) {

    public SearchResult {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId must not be null or blank");
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be positive (>= 1), but was: " + rank);
        }
    }
}
