package io.github.vishwassp01.relevanceeval.clicks;

import java.util.Objects;

/**
 * Identifies a unique (query, documentId) pair in search click logs.
 *
 * @param query      the search query string
 * @param documentId the document identifier
 */
public record QueryDocPair(String query, String documentId) {

    public QueryDocPair {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
    }
}
