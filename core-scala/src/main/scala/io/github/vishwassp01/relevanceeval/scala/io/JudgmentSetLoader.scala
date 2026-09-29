package io.github.vishwassp01.relevanceeval.scala.io

import java.nio.file.Path
import io.github.vishwassp01.relevanceeval.io as jio
import io.github.vishwassp01.relevanceeval.scala.converters.*
import io.github.vishwassp01.relevanceeval.scala.model.JudgmentSet

object JudgmentSetLoader:

  def load(path: Path): Either[LoadError, JudgmentSet] =
    try
      if path == null then
        Left(LoadError.Unknown("<null>", "Path must not be null"))
      else
        val javaLoader = new jio.JudgmentSetLoader()
        val javaSet = javaLoader.load(path)
        Right(javaSet.toScala)
    catch
      case ex: jio.JudgmentSetException =>
        Left(mapJudgmentSetException(path, ex))
      case ex: Throwable =>
        val pathStr = if path != null then path.toString else "<unknown>"
        Left(LoadError.Unknown(pathStr, Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName)))

  def loadNonEmpty(path: Path): Either[LoadError, JudgmentSet] =
    for
      set <- load(path)
      _   <- if set.queries.nonEmpty then Right(())
             else Left(LoadError.MalformedYaml(
               if path != null then path.toString else "<unknown>",
               "Judgment set must contain at least one query"
             ))
    yield set

  private def mapJudgmentSetException(path: Path, ex: jio.JudgmentSetException): LoadError =
    val pathStr = Option(ex.getPath).orElse(Option(path)).map(_.toString).getOrElse("<unknown>")
    val msg = Option(ex.getMessage).getOrElse("")
    val cause = ex.getCause

    if msg.contains("File does not exist") || msg.contains("not exist") || (cause != null && cause.isInstanceOf[java.io.FileNotFoundException]) then
      LoadError.FileNotFound(pathStr)
    else if msg.toLowerCase.contains("grade") || msg.contains("out of range") then
      LoadError.InvalidGrade(pathStr, msg)
    else if msg.contains("Malformed YAML") || msg.contains("Missing") || msg.contains("Encountered") || msg.contains("File is empty") || (cause != null && cause.getClass.getName.contains("JsonProcessingException")) then
      LoadError.MalformedYaml(pathStr, msg)
    else
      LoadError.Unknown(pathStr, msg)

def load(path: Path): Either[LoadError, JudgmentSet] = JudgmentSetLoader.load(path)

def loadNonEmpty(path: Path): Either[LoadError, JudgmentSet] = JudgmentSetLoader.loadNonEmpty(path)
