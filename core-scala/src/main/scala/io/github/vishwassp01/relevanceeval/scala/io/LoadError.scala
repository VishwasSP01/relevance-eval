package io.github.vishwassp01.relevanceeval.scala.io

enum LoadError:
  case FileNotFound(path: String)
  case MalformedYaml(path: String, detail: String)
  case InvalidGrade(path: String, detail: String)
  case Unknown(path: String, detail: String)

  def message: String = this match
    case FileNotFound(path) =>
      s"File not found: $path"
    case MalformedYaml(path, detail) =>
      s"Malformed YAML in '$path': $detail"
    case InvalidGrade(path, detail) =>
      s"Invalid relevance grade in '$path': $detail"
    case Unknown(path, detail) =>
      s"Error loading judgment set from '$path': $detail"
