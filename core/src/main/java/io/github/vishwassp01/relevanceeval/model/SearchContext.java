package io.github.vishwassp01.relevanceeval.model;

import java.util.Map;

/**
 * Encapsulates the execution context and configuration parameters used when querying a search engine.
 * <p>
 * This record captures reproducible environment details such as the target index name, requested
 * result window size, and any custom query parameters or search filters, ensuring evaluation runs
 * can be accurately traced and reproduced.
 *
 * @param indexName  the name of the target search index, must not be null or blank
 * @param size       the maximum number of results requested, must not be negative
 * @param parameters additional search parameters and query settings, defensively copied
 */
public record SearchContext(String indexName, int size, Map<String, Object> parameters) {

    public SearchContext {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName must not be null or blank");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative, but was: " + size);
        }
        parameters = Map.copyOf(parameters);
    }
}
