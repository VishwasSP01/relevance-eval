package io.github.vishwassp01.relevanceeval.backend.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.github.vishwassp01.relevanceeval.backend.SearchBackend;
import io.github.vishwassp01.relevanceeval.backend.SearchBackendException;
import io.github.vishwassp01.relevanceeval.model.SearchContext;
import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An implementation of {@link SearchBackend} that executes relevance queries against an Elasticsearch cluster.
 * <p>
 * Performs a {@code multi_match} query across specified fields (or wildcard {@code ["*"]} by default)
 * and maps the returned hits into {@link SearchResult} objects with 1-based ranks and Elasticsearch relevance scores.
 */
public class ElasticsearchBackend implements SearchBackend {

    private final ElasticsearchClient client;
    private final String indexName;

    /**
     * Constructs an Elasticsearch search backend.
     *
     * @param client    the configured Elasticsearch client instance, must not be null
     * @param indexName the target index name, must not be null or blank
     */
    public ElasticsearchBackend(ElasticsearchClient client, String indexName) {
        this.client = Objects.requireNonNull(client, "ElasticsearchClient must not be null");
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName must not be null or blank");
        }
        this.indexName = indexName;
    }

    @Override
    public List<SearchResult> search(String query, SearchContext context) {
        try {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Search query must not be null or blank");
            }

            List<String> targetFields = extractFields(context);
            int searchSize = (context != null) ? context.size() : 10;

            SearchResponse<Void> response = client.search(s -> s
                            .index(indexName)
                            .size(searchSize)
                            .query(q -> q
                                    .multiMatch(m -> m
                                            .query(query)
                                            .fields(targetFields)
                                    )
                            ),
                    Void.class
            );

            List<Hit<Void>> hits = (response.hits() != null && response.hits().hits() != null)
                    ? response.hits().hits()
                    : List.of();

            List<SearchResult> results = new ArrayList<>(hits.size());
            for (int i = 0; i < hits.size(); i++) {
                Hit<Void> hit = hits.get(i);
                int rank = i + 1;
                double score = (hit.score() != null) ? hit.score() : 0.0;
                results.add(new SearchResult(hit.id(), rank, score));
            }

            return Collections.unmodifiableList(results);
        } catch (Exception e) {
            throw new SearchBackendException(
                    String.format("Elasticsearch search failed on index '%s' for query '%s': %s",
                            indexName, query, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public String name() {
        return "elasticsearch[" + indexName + "]";
    }

    private List<String> extractFields(SearchContext context) {
        if (context == null || context.parameters() == null) {
            return List.of("*");
        }
        Object fieldsObj = context.parameters().get("fields");
        if (fieldsObj == null) {
            return List.of("*");
        }
        if (fieldsObj instanceof List<?> list) {
            List<String> fieldList = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    fieldList.add(item.toString());
                }
            }
            return fieldList.isEmpty() ? List.of("*") : fieldList;
        }
        if (fieldsObj instanceof String[] array) {
            List<String> fieldList = new ArrayList<>();
            for (String item : array) {
                if (item != null && !item.isBlank()) {
                    fieldList.add(item);
                }
            }
            return fieldList.isEmpty() ? List.of("*") : fieldList;
        }
        if (fieldsObj instanceof String str && !str.isBlank()) {
            return List.of(str);
        }
        return List.of("*");
    }
}
