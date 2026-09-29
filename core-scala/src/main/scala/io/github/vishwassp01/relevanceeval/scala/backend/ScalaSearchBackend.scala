package io.github.vishwassp01.relevanceeval.scala.backend

import cats.effect.IO
import io.github.vishwassp01.relevanceeval.scala.model.{SearchContext, SearchResult}

trait ScalaSearchBackend:
  def search(query: String, context: SearchContext): IO[List[SearchResult]]
  def name: String
