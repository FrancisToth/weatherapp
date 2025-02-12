package dev.contramap.weather
package nws

import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.*
import io.circe.parser.*
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.Uri

import java.time.Instant
import cats.effect.Ref

trait Service:
  def points(geo: Geo): IO[List[Period]]
object Service:
  final case class Error(message: String) extends RuntimeException(message)

  val res: Resource[IO, Service] =
    HttpClientCatsBackend.resource[IO]().map(Live(_))

  def inMemory(init: Map[Geo, List[Period]] = Map.empty): Resource[IO, InMemory] =
    Resource
      .eval(Ref[IO].of(init))
      .map(InMemory(_))

  final class InMemory(ref: Ref[IO, Map[Geo, List[Period]]]) extends Service:
    def upsert(geo: Geo, periods: List[Period]): IO[Unit] =
      ref.update(_.updated(geo, periods))

    override def points(geo: Geo): IO[List[Period]] =
      ref.get.map(_.getOrElse(geo, List.empty))

  private final class Live(backend: SttpBackend[IO, WebSockets]) extends Service:
    def points(geo: Geo): IO[List[Period]] =
      getForecast(geo)
        .flatMap(getPeriods)

    private def getPeriods(uri: Uri) = {
      val req = basicRequest.get(uri)
      val res = req.send(backend)

      res.flatMap((response: Response[Either[String, String]]) =>
        response.body.flatMap(decode[Forecast]) match
          case Left(e: io.circe.Error) => IO.raiseError(Error(e.getMessage()))
          case Left(e: String)         => IO.raiseError(Error(e))
          case Right(forecast)         => IO.pure(forecast.properties.periods)
      )
    }

    private def getForecast(geo: Geo): IO[Uri] = {
      val Geo(lat, long) = geo

      val req = basicRequest.get(uri"https://api.weather.gov/points/$lat,$long")
      val res = req.send(backend)

      res.flatMap((response: Response[Either[String, String]]) =>
        response.body.flatMap(decode[GridForecast]) match
          case Left(e: io.circe.Error) => IO.raiseError(Error(e.getMessage()))
          case Left(e: String)         => IO.raiseError(Error(e))
          case Right(grid)             => IO.pure(grid.properties.forecastHourly)
      )
    }
