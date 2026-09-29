package io.github.vishwassp01.relevanceeval.scala.model

case class Judgment(query: String, docId: String, grade: Int):
  require(query != null && !query.isBlank, "query must not be null or blank")
  require(docId != null && !docId.isBlank, "docId must not be null or blank")
  require(grade >= 0 && grade <= 3, s"grade must be between 0 and 3, but was: $grade")

case class JudgmentSet(name: String, judgments: List[Judgment]):
  require(name != null && !name.isBlank, "name must not be null or blank")
  require(judgments != null, "judgments must not be null")

  def queries: Set[String] =
    judgments.map(_.query).toSet

  def judgmentsFor(query: String): List[Judgment] =
    require(query != null, "query must not be null")
    judgments.filter(_.query == query)

case class SearchResult(docId: String, rank: Int, score: Double):
  require(docId != null && !docId.isBlank, "docId must not be null or blank")
  require(rank > 0, s"rank must be positive (>= 1), but was: $rank")

case class SearchContext(indexName: String, size: Int, parameters: Map[String, Any]):
  require(indexName != null && !indexName.isBlank, "indexName must not be null or blank")
  require(size >= 0, s"size must not be negative, but was: $size")
  require(parameters != null, "parameters must not be null")

case class MetricResult(metricName: String, overallValue: Double, perQueryValues: Map[String, Double]):
  require(metricName != null && !metricName.isBlank, "metricName must not be null or blank")
  require(perQueryValues != null, "perQueryValues must not be null")
