package io.github.vishwassp01.relevanceeval.clicks;

/**
 * Represents a single search result impression presented to a user, capturing whether the result was clicked.
 *
 * @param query      the search query text, must not be null or blank
 * @param documentId the identifier of the document shown to the user, must not be null or blank
 * @param position   the 1-based rank position as shown to the user, must be positive (&gt;= 1)
 * @param clicked    true if the user clicked on this document impression, false otherwise
 */
public record ClickEvent(String query, String documentId, int position, boolean clicked) {

    public ClickEvent {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be null or blank");
        }
        if (position < 1) {
            throw new IllegalArgumentException("position must be >= 1, but was: " + position);
        }
    }
}
