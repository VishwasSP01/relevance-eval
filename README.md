# relevance-eval

Test your search relevance like you test your code.

Java 21 · Scala 3 · Gradle

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
      - { id: "SKU-4411", grade: 1 }
  - query: "running shoes"
    judgments:
      - { id: "SKU-2201", grade: 3 }
      - { id: "SKU-5544", grade: 2 }
      - { id: "SKU-7712", grade: 1 }
      - { id: "SKU-9901", grade: 0 }
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
./cli/build/install/cli/bin/cli compare --baseline examples/runs/baseline.json --candidate examples/runs/candidate.json --threshold 0.1
```

Output:

```
================================================================================
Metric Comparison: NDCG@10
================================================================================
Baseline Overall:      0.7391
Candidate Overall:     0.7998
Overall Delta:        +0.0607

Significance:        p = 0.0001  (n = 22 queries, 10000 trials)
                     Significant at alpha = 0.05

Top Regressed Queries (worst-first):
--------------------------------------------------------------------------------
Query             Baseline    Candidate        Delta
---------------------------------------------------
duffel bag          0.8800       0.8350      -0.0450
ceramic mug         0.9100       0.8750      -0.0350
yoga mat            0.8500       0.8200      -0.0300

Top Improved Queries (best-first):
--------------------------------------------------------------------------------
Query                     Baseline    Candidate        Delta
-----------------------------------------------------------
espresso machine            0.6900       0.7700      +0.0800
laptop backpack             0.7000       0.7800      +0.0800
mechanical keyboard         0.7300       0.8100      +0.0800
bluetooth speaker           0.6700       0.7500      +0.0800
hiking boots                0.7400       0.8200      +0.0800
wireless headphones         0.6800       0.7600      +0.0800
coffee grinder              0.7100       0.7850      +0.0750
electric toothbrush         0.7200       0.7950      +0.0750
fleece pullover             0.7100       0.7850      +0.0750
waterproof jacket           0.7200       0.7950      +0.0750
air purifier                0.6500       0.7250      +0.0750
chef knife                  0.7600       0.8350      +0.0750
gaming mouse                0.7500       0.8250      +0.0750
office chair                0.6400       0.7150      +0.0750
running shoes               0.7500       0.8250      +0.0750
standing desk               0.6600       0.7350      +0.0750
water bottle                0.7900       0.8650      +0.0750
cast iron skillet           0.7800       0.8500      +0.0700
trail running socks         0.7700       0.8400      +0.0700
================================================================================
```

The comparison output reports a p-value computed using a paired randomization test. In plain English, the p-value tells you whether the difference in search quality between baseline and candidate is real or just random fluctuation. A low p-value (typically below 0.05) indicates the change is statistically significant and unlikely to have happened by luck, whereas a higher p-value means the change is within normal ranking noise.

The command exits with code 1 when any query regresses beyond the `--threshold`. You can also pass `--fail-on-significant-regression` to fail whenever an overall drop is statistically significant.


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

## Using the Scala API

The `core-scala` module provides native Scala 3 case classes, an `Either`-based loader, and Cats Effect integration.

### Dependency

In Gradle (`build.gradle.kts`):

```kotlin
implementation("com.relevanceeval:core-scala:0.1.0-SNAPSHOT")
```

Or in sbt:

```scala
libraryDependencies += "com.relevanceeval" %% "core-scala" % "0.1.0-SNAPSHOT"
```

### Loading Judgments and Running an Evaluation

```scala
import cats.effect.{IO, IOApp}
import io.github.vishwassp01.relevanceeval.metrics.{NdcgAtK, PrecisionAtK}
import io.github.vishwassp01.relevanceeval.scala.RelevanceEval
import io.github.vishwassp01.relevanceeval.scala.backend.InMemorySearchBackend
import io.github.vishwassp01.relevanceeval.scala.io.JudgmentSetLoader
import io.github.vishwassp01.relevanceeval.scala.model.SearchContext
import java.nio.file.Path

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    for
      judgments <- IO.fromEither(
        JudgmentSetLoader.load(Path.of("examples/sample.yaml"))
          .left.map(err => new RuntimeException(err.message))
      )
      backend = InMemorySearchBackend(Map(
        "waterproof jacket" -> List("SKU-1042", "SKU-3320", "SKU-8891"),
        "running shoes"     -> List("SKU-2201", "SKU-5544")
      ))
      metrics = List(new NdcgAtK(10), new PrecisionAtK(10))
      context = SearchContext("sample-index", 10, Map.empty)
      results <- RelevanceEval.evaluate(judgments, backend, metrics, context)
      _       <- IO.println(s"Evaluated ${results.size} metrics across ${judgments.queries.size} queries")
    yield ()
```

### Why Either and IO?

- **`Either[LoadError, JudgmentSet]` for File Loading**: Parsing judgment files is a synchronous operation with deterministic failure modes (missing file, malformed syntax, out-of-range grade). Returning `Either` makes errors explicit in the type system without relying on runtime exceptions.
- **`IO[List[MetricResult]]` for Evaluation**: Querying search engines involves asynchronous network I/O and resource management. `IO` encapsulates these side effects, enabling safe concurrency controls (such as bounded parallel query execution with `parTraverseN`) without thread starvation or unhandled failures.

## Modules

- `core`: Core domain model, relevance metrics, YAML loader, and search backend interfaces.
- `core-scala`: Native Scala 3 domain model, Cats Effect concurrency, and `Either`-based loader.
- `backend-elasticsearch`: Elasticsearch backend implementation and Testcontainers integration tests.
- `cli`: Command-line interface for running evaluations and comparing benchmark runs.

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
