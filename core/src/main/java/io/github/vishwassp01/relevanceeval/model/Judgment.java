package io.github.vishwassp01.relevanceeval.model;

/**
 * Represents a human or ground-truth relevance judgment associating a query and a document
 * with a graded relevance rating.
 * <p>
 * This record serves as the benchmark ground truth against which search engine ranking results
 * are measured in relevance evaluation metrics (such as NDCG, Precision@k, or MAP).
 *
 * @param query the search query text, must not be null or blank
 * @param docId the identifier of the document being evaluated, must not be null or blank
 * @param grade the relevance score assigned to this document for the query, must be between 0 and 3 inclusive
 */
public record Judgment(String query, String docId, int grade) {

    public Judgment {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId must not be null or blank");
        }
        if (grade < 0 || grade > 3) {
            throw new IllegalArgumentException("grade must be between 0 and 3, but was: " + grade);
        }
    }
}
