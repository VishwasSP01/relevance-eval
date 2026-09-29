package io.github.vishwassp01.relevanceeval.metrics;

import io.github.vishwassp01.relevanceeval.model.SearchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalizes search result lists before relevance metric computation by resolving two correctness gaps:
 * <ol>
 *   <li><b>Deduplication:</b> Eliminates duplicate document IDs within a query result list,
 *       retaining only the first occurrence (preserving rank order).</li>
 *   <li><b>Deterministic Tie-Breaking:</b> Sorts results by engine-assigned score descending,
 *       breaking score ties deterministically by document ID ascending.</li>
 * </ol>
 */
public final class ResultNormalizer {

    private ResultNormalizer() {
        // utility class
    }

    /**
     * Normalizes a list of search results by deduplicating by document ID (keeping the first occurrence)
     * and sorting by score descending with document ID ascending as the tie-breaker.
     *
     * @param results the raw search results list, may be null or empty
     * @return an unmodifiable list of normalized results with sequential 1-based ranks
     */
    public static List<SearchResult> normalize(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        // 1. Deduplicate keeping the FIRST occurrence (preserves rank order)
        Set<String> seenDocIds = new HashSet<>();
        List<SearchResult> deduped = new ArrayList<>(results.size());
        for (SearchResult result : results) {
            if (result != null && seenDocIds.add(result.docId())) {
                deduped.add(result);
            }
        }

        if (deduped.isEmpty()) {
            return List.of();
        }

        // 2. Sort by score descending, then document ID ascending as tie-breaker
        deduped.sort((a, b) -> {
            int scoreCmp = Double.compare(b.score(), a.score()); // score descending
            if (scoreCmp != 0) {
                return scoreCmp;
            }
            return a.docId().compareTo(b.docId()); // docId ascending
        });

        // 3. Re-index ranks to 1-based sequential order
        List<SearchResult> normalized = new ArrayList<>(deduped.size());
        for (int i = 0; i < deduped.size(); i++) {
            SearchResult r = deduped.get(i);
            normalized.add(new SearchResult(r.docId(), i + 1, r.score()));
        }

        return Collections.unmodifiableList(normalized);
    }
}
