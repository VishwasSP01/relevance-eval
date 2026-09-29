package io.github.vishwassp01.relevanceeval.scala.example

import cats.effect.{IO, IOApp}
import _root_.io.github.vishwassp01.relevanceeval.metrics.{NdcgAtK, PrecisionAtK}
import _root_.io.github.vishwassp01.relevanceeval.scala.RelevanceEval
import _root_.io.github.vishwassp01.relevanceeval.scala.backend.InMemorySearchBackend
import _root_.io.github.vishwassp01.relevanceeval.scala.io.JudgmentSetLoader
import _root_.io.github.vishwassp01.relevanceeval.scala.model.{JudgmentSet, MetricResult, SearchContext}

import java.nio.file.{Files, Path}

object ScalaExample extends IOApp.Simple:

  private def resolvePath(filename: String): Path =
    val candidates = List(
      Path.of(filename),
      Path.of("..", filename)
    )
    candidates.find(Files.exists(_)).getOrElse(Path.of(filename))

  private def printTable(judgmentSetName: String, backendName: String, results: List[MetricResult]): IO[Unit] =
    IO.delay {
      val sep = "=" * 80
      val subSep = "-" * 80

      println(sep)
      println(s"Evaluation: $judgmentSetName (Backend: $backendName)")
      println(sep)
      println(f"${"Metric"}%-28s ${"Overall Value"}%15s")
      println("-" * 44)
      for res <- results do
        println(f"${res.metricName}%-28s ${res.overallValue}%15.4f")

      println()
      println("Per-Query Breakdown:")
      println(subSep)
      println(f"${"Query"}%-25s ${"Metric"}%-20s ${"Score"}%10s")
      println("-" * 57)
      val allQueries = results.flatMap(_.perQueryValues.keys).distinct
      for query <- allQueries do
        for res <- results do
          val score = res.perQueryValues.getOrElse(query, 0.0)
          println(f"$query%-25s ${res.metricName}%-20s $score%10.4f")
      println(sep)
    }

  override def run: IO[Unit] =
    for
      path      <- IO.pure(resolvePath("examples/sample.yaml"))
      loadRes   <- IO.delay(JudgmentSetLoader.load(path))
      judgments <- loadRes match
        case Left(error) =>
          for
            _ <- IO.println(s"Error loading judgments: ${error.message}")
            _ <- IO.blocking(System.exit(1))
          yield null.asInstanceOf[JudgmentSet]
        case Right(js) =>
          IO.pure(js)
      backend = InMemorySearchBackend(
        "demo-in-memory",
        Map(
          "waterproof jacket" -> List("SKU-1042", "SKU-3320", "SKU-8891"),
          "running shoes"     -> List("SKU-2201", "SKU-5544")
        )
      )
      metrics = List(new NdcgAtK(10), new PrecisionAtK(10))
      context = SearchContext("sample-index", 10, Map.empty)
      results <- RelevanceEval.evaluate(judgments, backend, metrics, context)
      _       <- printTable(judgments.name, backend.name, results)
    yield ()
