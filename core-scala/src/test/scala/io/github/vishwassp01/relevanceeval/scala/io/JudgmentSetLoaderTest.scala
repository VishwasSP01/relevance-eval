package io.github.vishwassp01.relevanceeval.scala.io

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}

class JudgmentSetLoaderTest extends AnyFunSuite:

  private def resolveCoreFixture(filename: String): Path =
    val candidates = List(
      Path.of("core/src/test/resources", filename),
      Path.of("../core/src/test/resources", filename),
      Path.of("src/test/resources", filename)
    )
    candidates.find(Files.exists(_)).getOrElse(
      throw new IllegalArgumentException(s"Could not locate fixture '$filename' in: ${candidates.mkString(", ")}")
    )

  test("a valid file returns Right"):
    val validPath = resolveCoreFixture("valid-judgments.yaml")
    val result = load(validPath)

    assert(result.isRight)
    val set = result.toOption.get
    assert(set.name == "product-search-baseline")
    assert(set.queries == Set("waterproof jacket", "running shoes"))
    assert(set.judgmentsFor("waterproof jacket").length == 2)
    assert(set.judgmentsFor("running shoes").length == 1)

  test("a missing file returns Left(FileNotFound)"):
    val missingPath = Path.of("core/src/test/resources/does-not-exist.yaml")
    load(missingPath) match
      case Left(LoadError.FileNotFound(p)) =>
        assert(p.contains("does-not-exist.yaml"))
      case other =>
        fail(s"Expected Left(FileNotFound), but got $other")

  test("a malformed file returns Left(MalformedYaml)"):
    val malformedPath = resolveCoreFixture("malformed-judgments.yaml")
    load(malformedPath) match
      case Left(LoadError.MalformedYaml(p, detail)) =>
        assert(p.contains("malformed-judgments.yaml"))
        assert(detail.contains("Malformed YAML"))
      case other =>
        fail(s"Expected Left(MalformedYaml), but got $other")

  test("an invalid grade returns Left(InvalidGrade)"):
    val invalidGradePath = resolveCoreFixture("invalid-grade-judgments.yaml")
    load(invalidGradePath) match
      case Left(LoadError.InvalidGrade(p, detail)) =>
        assert(p.contains("invalid-grade-judgments.yaml"))
        assert(detail.contains("out of range") || detail.toLowerCase.contains("grade"))
      case other =>
        fail(s"Expected Left(InvalidGrade), but got $other")

  test("an empty judgment set fails loadNonEmpty"):
    val emptyFile = Files.createTempFile("empty-judgments", ".yaml")
    try
      Files.writeString(emptyFile, "name: empty-set\nqueries: []\n")
      // load should succeed with empty queries
      val loadResult = load(emptyFile)
      assert(loadResult.isRight)
      assert(loadResult.toOption.get.queries.isEmpty)

      // loadNonEmpty should fail
      val nonEmptyResult = loadNonEmpty(emptyFile)
      assert(nonEmptyResult.isLeft)
      nonEmptyResult match
        case Left(error) =>
          assert(error.message.nonEmpty)
        case Right(_) =>
          fail("Expected loadNonEmpty to fail for empty judgment set")
    finally
      Files.deleteIfExists(emptyFile)

  test("loadNonEmpty succeeds for non-empty valid file"):
    val validPath = resolveCoreFixture("valid-judgments.yaml")
    val result = loadNonEmpty(validPath)
    assert(result.isRight)
    assert(result.toOption.get.queries.nonEmpty)

  test("LoadError.message provides human-readable descriptions"):
    val fnf = LoadError.FileNotFound("/tmp/missing.yaml")
    assert(fnf.message == "File not found: /tmp/missing.yaml")

    val my = LoadError.MalformedYaml("/tmp/bad.yaml", "syntax error at line 3")
    assert(my.message == "Malformed YAML in '/tmp/bad.yaml': syntax error at line 3")

    val ig = LoadError.InvalidGrade("/tmp/grades.yaml", "Grade 5 is out of range")
    assert(ig.message == "Invalid relevance grade in '/tmp/grades.yaml': Grade 5 is out of range")

    val unk = LoadError.Unknown("/tmp/other.yaml", "I/O error")
    assert(unk.message == "Error loading judgment set from '/tmp/other.yaml': I/O error")

  test("null path does not throw"):
    val loadResult = load(null)
    assert(loadResult.isLeft)

    val nonEmptyResult = loadNonEmpty(null)
    assert(nonEmptyResult.isLeft)
