package io.github.vishwassp01.relevanceeval.backend;

import io.github.vishwassp01.relevanceeval.model.SearchContext;
import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.List;

/**
 * Primary extension point for integrating search systems into the relevance evaluation framework.
 * <p>
 * Anyone implementing this interface can evaluate their own search system (e.g., Elasticsearch,
 * OpenSearch, Solr, Vespa, Lucene, or custom vector/hybrid search engines).
 * <p>
 * <b>Implementation contract:</b>
 * <ul>
 *   <li>Implementations must return results with 1-based rank (starting at 1).</li>
 *   <li>Results must be returned in descending relevance order (highest scoring / most relevant hit first).</li>
 * </ul>
 */
public interface SearchBackend {

    /**
     * Executes a search query against the underlying search engine using the given context.
     *
     * @param query   the search query string to execute
     * @param context the execution context containing index name, size limit, and optional parameters
     * @return a list of {@link SearchResult} objects with 1-based ranks in descending relevance order
     */
    List<SearchResult> search(String query, SearchContext context);

    /**
     * Returns a human-readable identifier for this backend, used in evaluation reports and summaries.
     *
     * @return the backend name
     */
    String name();
}
