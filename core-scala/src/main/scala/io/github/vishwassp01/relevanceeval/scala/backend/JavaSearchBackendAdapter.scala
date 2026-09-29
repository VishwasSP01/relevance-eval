package io.github.vishwassp01.relevanceeval.scala.backend

import cats.effect.IO
import scala.jdk.CollectionConverters.*
import io.github.vishwassp01.relevanceeval.backend.SearchBackend as JavaSearchBackend
import io.github.vishwassp01.relevanceeval.scala.converters.*
import io.github.vishwassp01.relevanceeval.scala.model.{SearchContext, SearchResult}

final class JavaSearchBackendAdapter(underlying: JavaSearchBackend) extends ScalaSearchBackend:
  require(underlying != null, "underlying Java SearchBackend must not be null")

  override def name: String = underlying.name()

  override def search(query: String, context: SearchContext): IO[List[SearchResult]] =
    // The underlying Java search method is blocking — it executes network I/O
    // against remote search engines (such as Elasticsearch or OpenSearch).
    // We wrap the invocation in IO.blocking rather than IO.delay or IO.pure
    // so that Cats Effect executes it on the dedicated, dynamically sized
    // blocking thread pool rather than starving the fixed-size compute thread pool.
    IO.blocking {
      val javaContext = if context != null then context.toJava else null
      val results = underlying.search(query, javaContext)
      if results == null then Nil
      else results.asScala.map(_.toScala).toList
    }

object JavaSearchBackendAdapter:
  def apply(underlying: JavaSearchBackend): JavaSearchBackendAdapter =
    new JavaSearchBackendAdapter(underlying)

  extension (backend: JavaSearchBackend)
    def toScala: ScalaSearchBackend = JavaSearchBackendAdapter(backend)
