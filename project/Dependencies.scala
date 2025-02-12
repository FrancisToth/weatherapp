import sbt.*

object Dependencies {
  object circe {
    def apply(name: String) = "io.circe" %% s"circe-$name" % "0.14.10"

    val core = circe("core")
    val literal = circe("literal")
    val generic = circe("generic")
    val parser = circe("parser")

    val all = Seq(core, literal, generic, parser)
  }

  object cats {
    def apply(name: String) = "org.typelevel" %% s"cats-$name" % "2.13.0"

    val core = cats("core")
    val effect = "org.typelevel" %% "cats-effect" % "3.5.7"
  }

  object ciris {
    val version             = "3.7.0"
    def apply(name: String) = "is.cir" %% s"ciris-$name" % version

    val core = "is.cir" %% "ciris" % version
  }

  object iron {
    def apply(name: String) = mk(s"iron-$name")
    def mk(name: String) = "io.github.iltotore" %% name % "2.6.0"

    val core = "io.github.iltotore" %% "iron" % "2.6.0"
    // val scalacheck = iron("scalacheck")

    // val all  = Seq(core)
    // val test = Seq(scalacheck)
  }

  object fs2 {
    def apply(name: String) = "co.fs2" %% s"fs2-$name" % "3.11.0"

    val core = fs2("core")
    val io = fs2("io")
  }

  object http4s {
    def apply(name: String, version: String = "0.23.30") = "org.http4s" %% s"http4s-$name" % "0.23.30"

    val circe = http4s("circe")
    val client = http4s("ember-client")
    val dsl = http4s("dsl")
    val server = http4s("ember-server")
  }

  val logback = "ch.qos.logback" % "logback-classic" % "1.5.16"

  // object monocle {
  //   def apply(name: String) = "dev.optics" %% s"monocle-$name" % "3.3.0"

  //   val core    = monocle("core")
  //   val `macro` = monocle("macro")

  //   val all = Seq(core, `macro`)
  // }

  val mules = "io.chrisdavenport" %% "mules" % "0.7.0"

  object sttp {
    def apply(name: String) = "com.softwaremill.sttp.client3" %% name % "3.10.3"

    val cats = sttp("cats")
    val core = sttp("core")

    val all = Seq(cats, core)
  }

  object tapir {
    def apply(name: String) = "com.softwaremill.sttp.tapir" %% s"tapir-$name" % "1.11.14"
    val cats = tapir("cats")
    val circe = tapir("json-circe")
    val core = tapir("core")
    val http4s = tapir("http4s-server")
    val openapi_docs = tapir("openapi-docs")
    val swaggerUI = tapir("swagger-ui")

    val openapi_circe = "com.softwaremill.sttp.apispec" %% "openapi-circe" % "0.11.7"
  }

  object weaver {
    def apply(name: String) = "com.disneystreaming" %% s"weaver-$name" % "0.8.4"

    val core = weaver("cats")
    val scalacheck = weaver("scalacheck")

    val all = Seq(core, scalacheck)
  }
}
