package io.github.vishwassp01.relevanceeval.backend;

import io.github.vishwassp01.relevanceeval.model.SearchContext;
import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory implementation of {@link SearchBackend} designed exclusively for testing and simulation.
 * <p>
 * This backend serves pre-configured mappings of queries to ordered document IDs without requiring
 * external search infrastructure. Returned results are assigned 1-based ranks and synthetic scores
 * that descend with rank.
 */
public class InMemorySearchBackend implements SearchBackend {

    private final String name;
    private final Map<String, List<String>> queryToDocIds;

    /**
     * Creates an in-memory backend with a default name ("in-memory") and the specified query-to-document mappings.
     *
     * @param queryToDocIds map associating queries with their ordered list of returned document IDs
     */
    public InMemorySearchBackend(Map<String, List<String>> queryToDocIds) {
        this("in-memory", queryToDocIds);
    }

    /**
     * Creates an in-memory backend with a custom name and the specified query-to-document mappings.
     *
     * @param name           the backend identifier
     * @param queryToDocIds  map associating queries with their ordered list of returned document IDs
     */
    public InMemorySearchBackend(String name, Map<String, List<String>> queryToDocIds) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (queryToDocIds == null) {
            throw new IllegalArgumentException("queryToDocIds must not be null");
        }
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : queryToDocIds.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.name = name;
        this.queryToDocIds = Collections.unmodifiableMap(copy);
    }

    @Override
    public List<SearchResult> search(String query, SearchContext context) {
        if (query == null) {
            return List.of();
        }
        List<String> docIds = queryToDocIds.get(query);
        if (docIds == null || docIds.isEmpty()) {
            return List.of();
        }

        int limit = (context != null && context.size() > 0)
                ? Math.min(docIds.size(), context.size())
                : docIds.size();

        List<SearchResult> results = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            int rank = i + 1;
            double score = (double) (docIds.size() - i);
            results.add(new SearchResult(docIds.get(i), rank, score));
        }
        return Collections.unmodifiableList(results);
    }

    @Override
    public String name() {
        return name;
    }
}
