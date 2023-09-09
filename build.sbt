val scala3Version = "3.3.0"
val http4sVersion = "0.23.19"
val circeVersion = "0.14.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ak4-lambda",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.7.3",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % "1.7.3",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.7.3",
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "com.monovore" %% "decline" % "2.4.1",
      "com.monovore" %% "decline-effect" % "2.4.1",
      "com.github.nscala-time" %% "nscala-time" % "2.32.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.2",
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.5.1",
      "org.apache.logging.log4j" % "log4j-core" % "2.17.1",
      "org.apache.logging.log4j" % "log4j-api" % "2.17.1"
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}
