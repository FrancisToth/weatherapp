import sbt.*

object Dependencies {
  object ciris {
    val core = "is.cir" %% "ciris" % "3.7.0"
  }

  object http4s {
    def apply(name: String, version: String = "0.23.30") = "org.http4s" %% s"http4s-$name" % "0.23.30"

    val server = http4s("ember-server")
  }

  val logback = "ch.qos.logback" % "logback-classic" % "1.5.16"
  val mules   = "io.chrisdavenport" %% "mules" % "0.7.0"

  object sttp {
    def apply(name: String) = "com.softwaremill.sttp.client3" %% name % "3.10.3"

    val cats = sttp("cats")
  }

  object tapir {
    def apply(name: String) = "com.softwaremill.sttp.tapir" %% s"tapir-$name" % "1.11.14"

    val circe        = tapir("json-circe")
    val http4s       = tapir("http4s-server")
    val openapi_docs = tapir("openapi-docs")
    val swaggerUI    = tapir("swagger-ui")

    val openapi_circe = "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.7"
  }

  object weaver {
    def apply(name: String) = "com.disneystreaming" %% s"weaver-$name" % "0.8.4"

    val core = weaver("cats")
  }
}
