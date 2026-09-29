package io.github.vishwassp01.relevanceeval.scala

import cats.effect.IO
import cats.effect.syntax.all.*
import cats.syntax.all.*
import scala.jdk.CollectionConverters.*
import _root_.io.github.vishwassp01.relevanceeval.metrics.Metric
import _root_.io.github.vishwassp01.relevanceeval.model as jmodel
import _root_.io.github.vishwassp01.relevanceeval.scala.backend.ScalaSearchBackend
import _root_.io.github.vishwassp01.relevanceeval.scala.converters.*
import _root_.io.github.vishwassp01.relevanceeval.scala.model.{JudgmentSet, MetricResult, SearchContext, SearchResult}

object RelevanceEval:

  def evaluate(
    judgments: JudgmentSet,
    backend: ScalaSearchBackend,
    metrics: List[Metric],
    context: SearchContext,
    concurrency: Int = 8
  ): IO[List[MetricResult]] =
    require(judgments != null, "judgments must not be null")
    require(backend != null, "backend must not be null")
    require(metrics != null, "metrics must not be null")
    require(context != null, "context must not be null")
    require(concurrency > 0, s"concurrency must be positive (> 0), but was: $concurrency")

    val queries = judgments.queries.toList

    for
      // Bounding the concurrency limit is critical: search backends execute network I/O
      // against remote search clusters (such as Elasticsearch or OpenSearch). Unbounded concurrency
      // across hundreds or thousands of queries would hammer the search cluster, exhaust connection
      // pools, or trigger rate limits.
      queryResults <- queries.parTraverseN(concurrency) { query =>
        backend.search(query, context).map(results => query -> results)
      }
      resultsMap = queryResults.toMap
      javaJudgments = judgments.toJava
      javaResultsByQuery: java.util.Map[String, java.util.List[jmodel.SearchResult]] =
        resultsMap.map { (q, res) =>
          (q, res.map(_.toJava).asJava)
        }.asJava
    yield
      metrics.map { metric =>
        metric.computeFrom(javaJudgments, javaResultsByQuery).toScala
      }

def evaluate(
  judgments: JudgmentSet,
  backend: ScalaSearchBackend,
  metrics: List[Metric],
  context: SearchContext,
  concurrency: Int = 8
): IO[List[MetricResult]] =
  RelevanceEval.evaluate(judgments, backend, metrics, context, concurrency)
