package io.github.vishwassp01.relevanceeval.scala

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite
import _root_.io.github.vishwassp01.relevanceeval.backend as jbackend
import _root_.io.github.vishwassp01.relevanceeval.metrics.{MeanReciprocalRank, Metric, NdcgAtK, PrecisionAtK, RecallAtK}
import _root_.io.github.vishwassp01.relevanceeval.scala.backend.{JavaSearchBackendAdapter, ScalaSearchBackend}
import _root_.io.github.vishwassp01.relevanceeval.scala.converters.*
import _root_.io.github.vishwassp01.relevanceeval.scala.io.JudgmentSetLoader
import _root_.io.github.vishwassp01.relevanceeval.scala.model.{Judgment, JudgmentSet, SearchContext, SearchResult}

import java.nio.file.{Files, Path}

class RelevanceEvalTest extends AnyFunSuite:

  private def resolveCoreFixture(filename: String): Path =
    val candidates = List(
      Path.of("core/src/test/resources", filename),
      Path.of("../core/src/test/resources", filename),
      Path.of("src/test/resources", filename)
    )
    candidates.find(Files.exists(_)).getOrElse(
      throw new IllegalArgumentException(s"Could not locate fixture '$filename' in: ${candidates.mkString(", ")}")
    )

  test("results match the Java implementation exactly for the same inputs"):
    val validPath = resolveCoreFixture("valid-judgments.yaml")
    val scalaJudgments = JudgmentSetLoader.load(validPath).toOption.get
    val javaJudgments = scalaJudgments.toJava

    val docMappings = java.util.Map.of(
      "waterproof jacket", java.util.List.of("SKU-1042", "SKU-3320", "SKU-8891"),
      "running shoes", java.util.List.of("SKU-2201", "SKU-9999")
    )
    val javaBackend = new jbackend.InMemorySearchBackend(docMappings)
    val scalaBackend = JavaSearchBackendAdapter(javaBackend)

    val metrics: List[Metric] = List(
      new NdcgAtK(10),
      new PrecisionAtK(10),
      new RecallAtK(10),
      new MeanReciprocalRank()
    )

    val scalaContext = SearchContext("products", 10, Map.empty)
    val javaContext = scalaContext.toJava

    // Compute via Java directly
    val javaResults = metrics.map(m => m.evaluate(javaJudgments, javaBackend, javaContext))

    // Compute via Scala concurrent RelevanceEval
    val scalaResults = RelevanceEval.evaluate(scalaJudgments, scalaBackend, metrics, scalaContext, concurrency = 4).unsafeRunSync()

    assert(scalaResults.length == javaResults.length)
    for i <- metrics.indices do
      assert(scalaResults(i).toJava == javaResults(i))
      assert(scalaResults(i).metricName == javaResults(i).metricName())
      assert(math.abs(scalaResults(i).overallValue - javaResults(i).overallValue()) < 1e-9)
      assert(scalaResults(i).perQueryValues.size == javaResults(i).perQueryValues().size())

  test("a failing search produces a failed IO"):
    val judgments = JudgmentSet("test-suite", List(
      Judgment("query-1", "DOC-1", 3),
      Judgment("query-2", "DOC-2", 2)
    ))

    val failingBackend = new ScalaSearchBackend:
      override def search(query: String, context: SearchContext): IO[List[SearchResult]] =
        IO.raiseError(new RuntimeException("Simulated search engine network timeout"))

      override def name: String = "failing-backend"

    val metrics: List[Metric] = List(new NdcgAtK(10))
    val context = SearchContext("products", 10, Map.empty)

    val thrown = intercept[RuntimeException]:
      RelevanceEval.evaluate(judgments, failingBackend, metrics, context).unsafeRunSync()

    assert(thrown.getMessage.contains("Simulated search engine network timeout"))

    val attempted = RelevanceEval.evaluate(judgments, failingBackend, metrics, context).attempt.unsafeRunSync()
    assert(attempted.isLeft)

  test("an empty judgment set doesn't blow up"):
    val emptyJudgments = JudgmentSet("empty-suite", Nil)
    val backend = new ScalaSearchBackend:
      override def search(query: String, context: SearchContext): IO[List[SearchResult]] =
        IO.pure(Nil)

      override def name: String = "empty-backend"

    val metrics: List[Metric] = List(
      new NdcgAtK(10),
      new PrecisionAtK(10),
      new RecallAtK(10),
      new MeanReciprocalRank()
    )
    val context = SearchContext("products", 10, Map.empty)

    val results = RelevanceEval.evaluate(emptyJudgments, backend, metrics, context).unsafeRunSync()

    assert(results.length == 4)
    for result <- results do
      assert(result.overallValue == 0.0)
      assert(result.perQueryValues.isEmpty)

  test("validates positive concurrency parameter"):
    val emptyJudgments = JudgmentSet("empty", Nil)
    val backend = new ScalaSearchBackend:
      override def search(query: String, context: SearchContext): IO[List[SearchResult]] = IO.pure(Nil)
      override def name: String = "backend"

    val metrics: List[Metric] = List(new NdcgAtK(10))
    val context = SearchContext("products", 10, Map.empty)

    assertThrows[IllegalArgumentException]:
      RelevanceEval.evaluate(emptyJudgments, backend, metrics, context, concurrency = 0)

    assertThrows[IllegalArgumentException]:
      RelevanceEval.evaluate(emptyJudgments, backend, metrics, context, concurrency = -1)
