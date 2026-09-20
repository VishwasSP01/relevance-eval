# relevance-eval

Test your search relevance like you test your code.

## The Problem

Engineers regularly tune search queries, boost fields, or change ranking models without a reliable way to verify whether results improved. Latency regressions get caught immediately by production monitoring, but relevance regressions ship silently because search queries still return HTTP 200 responses. Without automated relevance testing against ground-truth benchmarks, teams find out about ranking bugs only after search conversion drops or users complain.

## Quick Start

Build the project and run the demo evaluation against a sample judgment set:

```bash
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli evaluate --judgments core/src/test/resources/valid-judgments.yaml --demo
```

Output:
```
================================================================================
Evaluation: product-search-baseline (Backend: demo-in-memory, Requested Size: 10)
================================================================================
Metric                      Overall Value
-----------------------------------------
NDCG@10                            0.9779
Precision@10                       0.1500

Per-Query Breakdown:
--------------------------------------------------------------------------------
Query               Metric                   Score
---------------------------------------------------
waterproof jacket   NDCG@10                 0.9558
waterproof jacket   Precision@10            0.2000
running shoes       NDCG@10                 1.0000
running shoes       Precision@10            0.1000
================================================================================
```

## Judgment File Format

Benchmark datasets are defined as YAML files containing queries and human- or rule-graded document relevance judgments:

```yaml
name: product-search-baseline
queries:
  - query: "waterproof jacket"
    judgments:
      - { id: "SKU-1042", grade: 3 }
      - { id: "SKU-8891", grade: 2 }
  - query: "running shoes"
    judgments:
      - { id: "SKU-2201", grade: 3 }
```

Grades range from 0 to 3:
- **3 (Exact match)**: Fully relevant document that directly satisfies the query intent.
- **2 (Relevant)**: Good match; satisfies the core need with minor differences.
- **1 (Marginal)**: Partially relevant or related item.
- **0 (Irrelevant)**: Not relevant to the query.

## Metrics

- **NDCG@k (Normalized Discounted Cumulative Gain)**: Measures graded ranking quality by placing higher weights on highly relevant documents ranked near the top; unjudged documents receive an implicit grade of 0 and contribute zero gain while still consuming a rank position.
- **Precision@k**: Measures the fraction of the top-k results that have a relevance grade of at least 1, dividing by the fixed cutoff k; unjudged documents are treated as irrelevant (grade 0) and contribute 0 to the numerator.

## Comparing Two Runs

Save evaluation results to JSON from a baseline and a candidate run:

```bash
./cli/build/install/cli/bin/cli evaluate --judgments core/src/test/resources/valid-judgments.yaml --demo --metrics ndcg@10 --output baseline.json
./cli/build/install/cli/bin/cli evaluate --judgments core/src/test/resources/valid-judgments.yaml --demo --metrics ndcg@10 --output candidate.json
```

Then compare them to inspect regressions:

```bash
./cli/build/install/cli/bin/cli compare --baseline baseline.json --candidate candidate.json --threshold 0.10
```

If any query regresses by more than `--threshold` (default 0.1), the command exits with code 1.

Example output:
```
================================================================================
Metric Comparison: NDCG@10
================================================================================
Baseline Overall:      0.9779
Candidate Overall:     0.8500
Overall Delta:        -0.1279

Top Regressed Queries (worst-first):
--------------------------------------------------------------------------------
Query                   Baseline    Candidate        Delta
---------------------------------------------------------
waterproof jacket         0.9558       0.7000      -0.2558

Top Improved Queries (best-first):
--------------------------------------------------------------------------------
  (None)
================================================================================
```

## Backends

To evaluate a search system, implement the `SearchBackend` interface from the `core` module:

```java
public interface SearchBackend {
    List<SearchResult> search(String query, SearchContext context);
    String name();
}
```

Implementing these two methods is all it takes to evaluate any search system (Elasticsearch, OpenSearch, Solr, Vespa, or a custom in-house engine). The `backend-elasticsearch` module provides an official implementation using the Elasticsearch Java client.

## Building and Testing

Run unit tests across all modules (does not require Docker):
```bash
./gradlew test
```

Run integration tests against containerized Elasticsearch instances (requires Docker):
```bash
./gradlew integrationTest
```

## Status

Early stage. The public API may change.

## License

Apache 2.0
