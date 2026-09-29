package io.github.vishwassp01.relevanceeval.scala.backend

import cats.effect.IO
import io.github.vishwassp01.relevanceeval.scala.model.{SearchContext, SearchResult}

final class InMemorySearchBackend(
  val name: String,
  queryToDocIds: Map[String, List[String]]
) extends ScalaSearchBackend:
  require(name != null && !name.isBlank, "name must not be null or blank")
  require(queryToDocIds != null, "queryToDocIds must not be null")

  def this(queryToDocIds: Map[String, List[String]]) =
    this("in-memory", queryToDocIds)

  override def search(query: String, context: SearchContext): IO[List[SearchResult]] =
    IO.pure {
      if query == null then Nil
      else
        queryToDocIds.get(query) match
          case None => Nil
          case Some(docIds) if docIds.isEmpty => Nil
          case Some(docIds) =>
            val limit =
              if context != null && context.size > 0 then math.min(docIds.size, context.size)
              else docIds.size

            (0 until limit).toList.map { i =>
              val rank = i + 1
              val score = (docIds.size - i).toDouble
              SearchResult(docIds(i), rank, score)
            }
    }

object InMemorySearchBackend:
  def apply(queryToDocIds: Map[String, List[String]]): InMemorySearchBackend =
    new InMemorySearchBackend(queryToDocIds)

  def apply(name: String, queryToDocIds: Map[String, List[String]]): InMemorySearchBackend =
    new InMemorySearchBackend(name, queryToDocIds)

type ScalaInMemorySearchBackend = InMemorySearchBackend
val ScalaInMemorySearchBackend: InMemorySearchBackend.type = InMemorySearchBackend
