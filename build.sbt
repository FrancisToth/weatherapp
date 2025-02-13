import Dependencies.*

ThisBuild / scalaVersion := "3.6.2"

lazy val root = (project in file("."))
  .settings(
    fork := true,
    libraryDependencies := Seq(
      tapir.http4s,
      tapir.circe,
      tapir.swaggerUI,
      tapir.openapi_circe,
      tapir.openapi_docs,
      http4s.server,
      sttp.cats,
      logback,
      ciris.core,
      mules,
      weaver.core % Test
    )
  )
