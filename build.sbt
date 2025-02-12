import Dependencies.*

ThisBuild / scalaVersion := "3.6.2"

lazy val root = (project in file("."))
  .settings(
    fork := true,
    libraryDependencies := Seq(
      cats.effect,
      tapir.core,
      tapir.http4s,
      tapir.circe,
      fs2.core,
      fs2.io,
      circe.core,
      sttp.core,
      http4s.server,
      sttp.cats,
      iron.core,
      logback,
      ciris.core,
      mules,
      weaver.core % Test
    )
  )
