package io.github.vishwassp01.relevanceeval.scala.converters

import org.scalatest.funsuite.AnyFunSuite
import io.github.vishwassp01.relevanceeval.model as jmodel
import io.github.vishwassp01.relevanceeval.scala.model as smodel

class ConvertersTest extends AnyFunSuite:

  test("round-trips Judgment between Java and Scala"):
    val javaObj = new jmodel.Judgment("waterproof jacket", "DOC-101", 3)
    val scalaObj = javaObj.toScala

    assert(scalaObj.query == "waterproof jacket")
    assert(scalaObj.docId == "DOC-101")
    assert(scalaObj.grade == 3)

    val roundTripJava = scalaObj.toJava
    assert(roundTripJava == javaObj)
    assert(roundTripJava.toScala == scalaObj)

  test("round-trips JudgmentSet between Java and Scala"):
    val javaList = java.util.List.of(
      new jmodel.Judgment("waterproof jacket", "DOC-101", 3),
      new jmodel.Judgment("waterproof jacket", "DOC-102", 1),
      new jmodel.Judgment("hiking boots", "DOC-201", 2)
    )
    val javaObj = new jmodel.JudgmentSet("test-suite", javaList)
    val scalaObj = javaObj.toScala

    assert(scalaObj.name == "test-suite")
    assert(scalaObj.queries == Set("waterproof jacket", "hiking boots"))
    assert(scalaObj.judgmentsFor("waterproof jacket").map(_.docId) == List("DOC-101", "DOC-102"))
    assert(scalaObj.judgmentsFor("hiking boots").map(_.docId) == List("DOC-201"))
    assert(scalaObj.judgmentsFor("unknown").isEmpty)

    val roundTripJava = scalaObj.toJava
    assert(roundTripJava == javaObj)
    assert(roundTripJava.toScala == scalaObj)

  test("round-trips SearchResult between Java and Scala"):
    val javaObj = new jmodel.SearchResult("DOC-303", 1, 9.75)
    val scalaObj = javaObj.toScala

    assert(scalaObj.docId == "DOC-303")
    assert(scalaObj.rank == 1)
    assert(scalaObj.score == 9.75)

    val roundTripJava = scalaObj.toJava
    assert(roundTripJava == javaObj)
    assert(roundTripJava.toScala == scalaObj)

  test("round-trips SearchContext between Java and Scala"):
    val javaParams = java.util.Map.of[String, AnyRef](
      "boost", java.lang.Double.valueOf(1.5),
      "fuzziness", "AUTO"
    )
    val javaObj = new jmodel.SearchContext("products-index", 20, javaParams)
    val scalaObj = javaObj.toScala

    assert(scalaObj.indexName == "products-index")
    assert(scalaObj.size == 20)
    assert(scalaObj.parameters("boost") == 1.5)
    assert(scalaObj.parameters("fuzziness") == "AUTO")

    val roundTripJava = scalaObj.toJava
    assert(roundTripJava == javaObj)
    assert(roundTripJava.toScala == scalaObj)

  test("round-trips MetricResult between Java and Scala"):
    val javaPerQuery = java.util.Map.of(
      "waterproof jacket", java.lang.Double.valueOf(0.95),
      "hiking boots", java.lang.Double.valueOf(0.80)
    )
    val javaObj = new jmodel.MetricResult("NDCG@10", 0.875, javaPerQuery)
    val scalaObj = javaObj.toScala

    assert(scalaObj.metricName == "NDCG@10")
    assert(scalaObj.overallValue == 0.875)
    assert(scalaObj.perQueryValues == Map("waterproof jacket" -> 0.95, "hiking boots" -> 0.80))

    val roundTripJava = scalaObj.toJava
    assert(roundTripJava == javaObj)
    assert(roundTripJava.toScala == scalaObj)

  test("Judgment validates grade bounds (0 to 3)"):
    for grade <- 0 to 3 do
      val j = smodel.Judgment("query", "DOC-1", grade)
      assert(j.grade == grade)

    assertThrows[IllegalArgumentException]:
      smodel.Judgment("query", "DOC-1", -1)

    assertThrows[IllegalArgumentException]:
      smodel.Judgment("query", "DOC-1", 4)

  test("Judgment validates non-blank query and docId"):
    assertThrows[IllegalArgumentException]:
      smodel.Judgment("", "DOC-1", 2)
    assertThrows[IllegalArgumentException]:
      smodel.Judgment("   ", "DOC-1", 2)
    assertThrows[IllegalArgumentException]:
      smodel.Judgment(null, "DOC-1", 2)
    assertThrows[IllegalArgumentException]:
      smodel.Judgment("query", "", 2)
    assertThrows[IllegalArgumentException]:
      smodel.Judgment("query", "   ", 2)
    assertThrows[IllegalArgumentException]:
      smodel.Judgment("query", null, 2)

  test("SearchResult validates positive rank"):
    val r1 = smodel.SearchResult("DOC-1", 1, 1.0)
    assert(r1.rank == 1)
    val r2 = smodel.SearchResult("DOC-1", 100, 0.5)
    assert(r2.rank == 100)

    assertThrows[IllegalArgumentException]:
      smodel.SearchResult("DOC-1", 0, 1.0)

    assertThrows[IllegalArgumentException]:
      smodel.SearchResult("DOC-1", -1, 1.0)

    assertThrows[IllegalArgumentException]:
      smodel.SearchResult("", 1, 1.0)
    assertThrows[IllegalArgumentException]:
      smodel.SearchResult("   ", 1, 1.0)

  test("SearchContext validates arguments"):
    assertThrows[IllegalArgumentException]:
      smodel.SearchContext("", 10, Map.empty)
    assertThrows[IllegalArgumentException]:
      smodel.SearchContext("   ", 10, Map.empty)
    assertThrows[IllegalArgumentException]:
      smodel.SearchContext("idx", -1, Map.empty)

  test("MetricResult validates arguments"):
    assertThrows[IllegalArgumentException]:
      smodel.MetricResult("", 0.5, Map.empty)
    assertThrows[IllegalArgumentException]:
      smodel.MetricResult("   ", 0.5, Map.empty)
