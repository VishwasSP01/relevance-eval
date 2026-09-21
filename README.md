# relevance-eval

Test your search relevance like you test your code.

## The Problem

Teams change search ranking with no way to know whether results actually improved. Latency regressions get caught by monitoring; relevance regressions ship silently because nothing fails. Without automated testing against relevance benchmarks, ranking bugs only surface after search conversion drops or users complain.

## Quick Start

```bash
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli evaluate --judgments examples/sample.yaml --demo
```

Output:

```
================================================================================
Evaluation: demo-judgments (Backend: demo-in-memory, Requested Size: 10)
================================================================================
Metric                      Overall Value
-----------------------------------------
NDCG@10                            0.9779
Precision@10                       0.2000

Per-Query Breakdown:
--------------------------------------------------------------------------------
Query               Metric                   Score
---------------------------------------------------
waterproof jacket   NDCG@10                 0.9558
waterproof jacket   Precision@10            0.2000
running shoes       NDCG@10                 1.0000
running shoes       Precision@10            0.2000
================================================================================
```

## Judgment File Format

```yaml
name: demo-judgments
queries:
  - query: "waterproof jacket"
    judgments:
      - { id: "SKU-1042", grade: 3 }
      - { id: "SKU-8891", grade: 2 }
      - { id: "SKU-3320", grade: 0 }
  - query: "running shoes"
    judgments:
      - { id: "SKU-2201", grade: 3 }
      - { id: "SKU-5544", grade: 1 }
```

Grades range from 0 (irrelevant) to 3 (exact match), with 1 (marginal) and 2 (relevant) in between.

## Metrics

- **NDCG@k**: Measures ranking quality by placing higher weights on highly relevant documents ranked near the top. Unjudged documents are treated as grade 0 (contributing zero gain while taking a rank slot).
- **Precision@k**: Measures the fraction of the top-k results that are relevant (grade 1 or higher), dividing by k rather than the number of returned results. Unjudged documents are treated as irrelevant (grade 0).

## Comparing Two Runs

Save evaluation results to JSON from a baseline and a candidate run:

```bash
./cli/build/install/cli/bin/cli evaluate --judgments examples/sample.yaml --demo --metrics ndcg@10 --output baseline.json
./cli/build/install/cli/bin/cli evaluate --judgments examples/sample.yaml --demo --metrics ndcg@10 --output candidate.json
```

Compare the two runs:

```bash
./cli/build/install/cli/bin/cli compare --baseline baseline.json --candidate candidate.json --threshold 0.05
```

Output when regressions exceed the threshold:

```
================================================================================
Metric Comparison: NDCG@10
================================================================================
Baseline Overall:      0.9779
Candidate Overall:     0.8279
Overall Delta:        -0.1500

Top Regressed Queries (worst-first):
--------------------------------------------------------------------------------
Query                   Baseline    Candidate        Delta
---------------------------------------------------------
waterproof jacket         0.9558       0.6558      -0.3000

Top Improved Queries (best-first):
--------------------------------------------------------------------------------
  (None)
================================================================================

FAILURE: One or more queries regressed by more than threshold 0.0500
```

The command exits with code 1 when any query regresses beyond the threshold, allowing you to fail a CI build on relevance regressions.

## See it in CI

The `relevance-example` job in GitHub Actions runs the tool against [`examples/runs/baseline.json`](examples/runs/baseline.json) and [`examples/runs/candidate.json`](examples/runs/candidate.json) on every push, publishing the comparison results as a JUnit test report in the Actions summary. Check the **Actions** tab to see live test summaries.

## Plugging in Your Own Search Engine

Implementing the `SearchBackend` interface is all it takes to evaluate any search engine:

```java
public interface SearchBackend {
    List<SearchResult> search(String query, SearchContext context);
    String name();
}
```

## Building and Testing

Run unit tests (no Docker needed):

```bash
./gradlew test
```

Run integration tests (requires Docker; uses Testcontainers 2.x):

```bash
./gradlew :backend-elasticsearch:integrationTest
```

## Status

Early stage, APIs may change.

## License

Apache 2.0
