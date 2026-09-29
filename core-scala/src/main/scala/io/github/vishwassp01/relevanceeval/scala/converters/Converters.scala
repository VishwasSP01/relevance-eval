package io.github.vishwassp01.relevanceeval.scala.converters

import scala.jdk.CollectionConverters.*
import io.github.vishwassp01.relevanceeval.model as jmodel
import io.github.vishwassp01.relevanceeval.scala.model as smodel

// Java to Scala conversions (.toScala)
extension (j: jmodel.Judgment)
  def toScala: smodel.Judgment =
    smodel.Judgment(j.query(), j.docId(), j.grade())

extension (js: jmodel.JudgmentSet)
  def toScala: smodel.JudgmentSet =
    smodel.JudgmentSet(
      js.name(),
      js.judgments().asScala.map(_.toScala).toList
    )

extension (r: jmodel.SearchResult)
  def toScala: smodel.SearchResult =
    smodel.SearchResult(r.docId(), r.rank(), r.score())

extension (ctx: jmodel.SearchContext)
  def toScala: smodel.SearchContext =
    smodel.SearchContext(
      ctx.indexName(),
      ctx.size(),
      ctx.parameters().asScala.toMap
    )

extension (mr: jmodel.MetricResult)
  def toScala: smodel.MetricResult =
    smodel.MetricResult(
      mr.metricName(),
      mr.overallValue(),
      mr.perQueryValues().asScala.map((k, v) => (k, v.doubleValue())).toMap
    )

// Scala to Java conversions (.toJava)
extension (j: smodel.Judgment)
  def toJava: jmodel.Judgment =
    new jmodel.Judgment(j.query, j.docId, j.grade)

extension (js: smodel.JudgmentSet)
  def toJava: jmodel.JudgmentSet =
    new jmodel.JudgmentSet(
      js.name,
      js.judgments.map(_.toJava).asJava
    )

extension (r: smodel.SearchResult)
  def toJava: jmodel.SearchResult =
    new jmodel.SearchResult(r.docId, r.rank, r.score)

extension (ctx: smodel.SearchContext)
  def toJava: jmodel.SearchContext =
    new jmodel.SearchContext(
      ctx.indexName,
      ctx.size,
      ctx.parameters.map((k, v) => (k, v.asInstanceOf[AnyRef])).asJava
    )

extension (mr: smodel.MetricResult)
  def toJava: jmodel.MetricResult =
    new jmodel.MetricResult(
      mr.metricName,
      mr.overallValue,
      mr.perQueryValues.map((k, v) => (k, java.lang.Double.valueOf(v))).asJava
    )
