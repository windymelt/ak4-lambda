val scala3Version = "3.4.2"
val circeVersion = "0.14.9"

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "ak4-lambda",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %%% "munit" % "0.7.29" % Test,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %%% "tapir-core" % "1.7.5",
      "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % "1.11.0",
      "com.softwaremill.sttp.tapir" %%% "tapir-json-circe" % "1.4.0",
      // "org.http4s" %%% "http4s-ember-client" % http4sVersion,
      "com.softwaremill.sttp.client4" %%% "core" % "4.0.0-M16",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "com.monovore" %%% "decline" % "2.4.1",
      "com.monovore" %%% "decline-effect" % "2.4.1",
      "com.outr" %%% "scribe" % "3.15.0",
      // "com.github.nscala-time" %%% "nscala-time" % "2.32.0",
      // "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
      // "com.amazonaws" % "aws-lambda-java-events" % "3.11.4",
      // "com.amazonaws" % "aws-lambda-java-log4j2" % "1.5.1",
      // "org.apache.logging.log4j" % "log4j-core" % "2.20.0",
      // "org.apache.logging.log4j" % "log4j-api" % "2.20.0",
      // "com.amazonaws.secretsmanager" % "aws-secretsmanager-caching-java" % "1.0.2"
    ),
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % circeVersion),
    //scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
    },
  )

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}
