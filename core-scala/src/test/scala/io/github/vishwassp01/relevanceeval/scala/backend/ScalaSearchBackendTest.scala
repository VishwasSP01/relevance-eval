package io.github.vishwassp01.relevanceeval.scala.backend

import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite
import io.github.vishwassp01.relevanceeval.backend as jbackend
import io.github.vishwassp01.relevanceeval.model as jmodel
import io.github.vishwassp01.relevanceeval.scala.model.{SearchContext, SearchResult}

class ScalaSearchBackendTest extends AnyFunSuite:

  test("JavaSearchBackendAdapter returns expected search results converted to Scala"):
    val javaBackend = new jbackend.InMemorySearchBackend(java.util.Map.of(
      "running shoes", java.util.List.of("DOC-1", "DOC-2", "DOC-3")
    ))
    val adapter = new JavaSearchBackendAdapter(javaBackend)

    assert(adapter.name == "in-memory")

    val context = SearchContext("products", 2, Map.empty)
    val results = adapter.search("running shoes", context).unsafeRunSync()

    assert(results.length == 2)
    assert(results(0) == SearchResult("DOC-1", 1, 3.0))
    assert(results(1) == SearchResult("DOC-2", 2, 2.0))

  test("JavaSearchBackendAdapter produces failed IO when underlying backend throws SearchBackendException"):
    val failingBackend = new jbackend.SearchBackend:
      override def search(query: String, context: jmodel.SearchContext): java.util.List[jmodel.SearchResult] =
        throw new jbackend.SearchBackendException("Simulated Elasticsearch cluster connection failure")

      override def name(): String = "failing-backend"

    val adapter = new JavaSearchBackendAdapter(failingBackend)
    val context = SearchContext("products", 10, Map.empty)

    val thrown = intercept[jbackend.SearchBackendException]:
      adapter.search("query", context).unsafeRunSync()

    assert(thrown.getMessage.contains("Simulated Elasticsearch cluster connection failure"))

    // Also verify directly that IO.attempt captures it as a Left without throwing out of IO
    val attempted = adapter.search("query", context).attempt.unsafeRunSync()
    assert(attempted.isLeft)
    assert(attempted.left.toOption.get.isInstanceOf[jbackend.SearchBackendException])

  test("InMemorySearchBackend executes queries and assigns ranks and descending scores"):
    val backend = InMemorySearchBackend(Map(
      "hiking boots" -> List("DOC-A", "DOC-B", "DOC-C"),
      "waterproof jacket" -> List("DOC-X")
    ))

    assert(backend.name == "in-memory")

    val ctxLimited = SearchContext("catalog", 2, Map.empty)
    val bootsResults = backend.search("hiking boots", ctxLimited).unsafeRunSync()

    assert(bootsResults == List(
      SearchResult("DOC-A", 1, 3.0),
      SearchResult("DOC-B", 2, 2.0)
    ))

    val ctxUnlimited = SearchContext("catalog", 10, Map.empty)
    val jacketResults = backend.search("waterproof jacket", ctxUnlimited).unsafeRunSync()

    assert(jacketResults == List(
      SearchResult("DOC-X", 1, 1.0)
    ))

  test("InMemorySearchBackend returns empty list for unknown query or empty results"):
    val backend = InMemorySearchBackend(
      "custom-backend",
      Map("known" -> List("DOC-1"))
    )

    assert(backend.name == "custom-backend")

    val ctx = SearchContext("catalog", 5, Map.empty)
    assert(backend.search("unknown", ctx).unsafeRunSync().isEmpty)
    assert(backend.search(null, ctx).unsafeRunSync().isEmpty)
