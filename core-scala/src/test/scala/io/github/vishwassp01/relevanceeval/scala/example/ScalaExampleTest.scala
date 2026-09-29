package io.github.vishwassp01.relevanceeval.scala.example

import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite

class ScalaExampleTest extends AnyFunSuite:

  test("ScalaExample runs successfully and evaluates sample judgments"):
    ScalaExample.run.unsafeRunSync()
