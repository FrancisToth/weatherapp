package dev.contramap.weather

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.kernel.Clock
import cats.syntax.parallel.*
import ciris.*
import com.comcast.ip4s.*
import fs2.Stream
import io.circe as JS
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import scala.concurrent.duration.*

import java.time.Instant
import java.time.ZoneId

object Main extends IOApp:
  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def routes(service: ForecastService) =
    Resource
      .eval(IO(List(DailyForecast(service).route)))
      .map(Http4sServerInterpreter[IO]().toRoutes(_).orNotFound)

  val server: Resource[IO, Server] = {
    Config.res.flatMap { config =>
      ForecastService
        .live(config.cacheExpiration)
        .flatMap(routes(_))
        .map(logic => HttpApp[IO](logic(_)))
        .flatMap {
          EmberServerBuilder
            .default[IO]
            .withHost(config.host)
            .withPort(config.port)
            .withHttpApp(_)
            .build
        }
    }
  }

  override def run(args: List[String]): IO[ExitCode] =
    server.useForever
      .as(ExitCode.Success)

final case class Config(port: Port, host: Ipv4Address, cacheExpiration: FiniteDuration)
object Config:
  val port = env("WEATHER_APP_PORT").as[Port].default(port"8080")
  val host = env("WEATHER_APP_HOST").as[Ipv4Address].default(ipv4"0.0.0.0")
  val cacheExpiration = env("WEATHER_CACHE_EXPIRATION").as[FiniteDuration].default(1.hour)

  val res = Resource.eval((port, host, cacheExpiration).parMapN(Config.apply).load[IO])

  given ConfigDecoder[String, Ipv4Address] =
    ConfigDecoder.lift(s => Ipv4Address.fromString(s).toRight(ConfigError("is not an IPv4.")))

  given ConfigDecoder[String, Port] =
    ConfigDecoder.lift(s => Port.fromString(s).toRight(ConfigError("is not a valid port.")))
