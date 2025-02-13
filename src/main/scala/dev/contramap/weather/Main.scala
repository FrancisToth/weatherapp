package dev.contramap.weather

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.parallel.*
import ciris.*
import com.comcast.ip4s.*
import io.circe as JS
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.{HttpApp, Response}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import scala.concurrent.duration.*
import sttp.apispec.openapi.circe.yaml.*
import sttp.apispec.openapi.{Info, OpenAPI}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.FailureMessages
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.swagger.SwaggerUI

object Main extends IOApp:
  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  val decodeFailureHandler = DecodeFailureHandler[IO] { ctx =>
    IO.pure(
      DefaultDecodeFailureHandler
        .respond(ctx)
        .map { case (sc, hs) =>
          (ctx.failingInput, ctx.failure) match {
            case (EndpointInput.Query(queryParamName, _, _, _), DecodeResult.Error(value, error)) =>
              ValuedEndpointOutput(
                statusCode.and(jsonBody[ErrorDescription]),
                (
                  StatusCode.BadRequest,
                  ErrorDescription.invalidQueryParameter(queryParamName, value, error.getLocalizedMessage())
                )
              )
            case _ =>
              ValuedEndpointOutput(
                statusCode.and(jsonBody[ErrorDescription]),
                (StatusCode.BadRequest, ErrorDescription(FailureMessages.failureMessage(ctx)))
              )
          }
        }
    )
  }

  def routes(service: ForecastService) = {
    val endpoints = List(DailyForecast(service).route)

    val openApi: OpenAPI =
      OpenAPIDocsInterpreter().serverEndpointsToOpenAPI(
        endpoints.sortBy(_.showShort).sortBy(_.method.map(_.method)),
        Info("weatherapp", "v1")
      )
    Resource
      .eval(IO(SwaggerUI[IO](openApi.toYaml) ++ endpoints))
      .map(
        Http4sServerInterpreter[IO](
          serverOptions = Http4sServerOptions
            .customiseInterceptors[IO]
            .decodeFailureHandler(decodeFailureHandler)
            .options
        ).toRoutes(_).orNotFound
      )
  }

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
  val port            = env("WEATHER_APP_PORT").as[Port].default(port"8080")
  val host            = env("WEATHER_APP_HOST").as[Ipv4Address].default(ipv4"0.0.0.0")
  val cacheExpiration = env("WEATHER_CACHE_EXPIRATION").as[FiniteDuration].default(1.hour)

  val res = Resource.eval((port, host, cacheExpiration).parMapN(Config.apply).load[IO])

  given ConfigDecoder[String, Ipv4Address] =
    ConfigDecoder.lift(s => Ipv4Address.fromString(s).toRight(ConfigError("is not an IPv4.")))

  given ConfigDecoder[String, Port] =
    ConfigDecoder.lift(s => Port.fromString(s).toRight(ConfigError("is not a valid port.")))

final case class ErrorDescription(message: String) derives JS.Codec.AsObject, Schema
object ErrorDescription {
  def invalidQueryParameter(param: String, value: String, reason: String): ErrorDescription =
    ErrorDescription(
      s"""Invalid query parameter (name: [$param],
         |value: [$value],
         |reason: $reason)""".stripMargin.replaceAll("\n", " ")
    )
}
