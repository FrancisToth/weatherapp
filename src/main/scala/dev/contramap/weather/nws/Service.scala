package dev.contramap.weather
package nws

import cats.effect.kernel.Resource
import cats.effect.{IO, Ref}
import cats.syntax.either.*
import io.circe.*
import io.circe.parser.*
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.Uri

trait Service:
  def points(geo: Geo): IO[List[Period]]
object Service:
  final case class Error(message: String) extends RuntimeException(message)
  object Error {
    def apply(e: io.circe.Error): Error =
      Error(e.getLocalizedMessage())

    def apply(e: NWError): Error =
      Error((e.title))
  }

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

  final case class NWError(title: String) derives Codec.AsObject

  private final class Live(backend: SttpBackend[IO, WebSockets]) extends Service:
    def points(geo: Geo): IO[List[Period]] =
      getForecast(geo)
        .flatMap(getPeriods)
        .handleError { case Error("Data Unavailable For Requested Point") =>
          List.empty
        }

    private def getPeriods(uri: Uri) =
      val req = basicRequest.get(uri)
      val res = req.send(backend)
      res.flatMap(deserialize[Forecast]).map(_.properties.periods)

    private def getForecast(geo: Geo): IO[Uri] =
      val Geo(lat, long) = geo

      val req = basicRequest.get(uri"https://api.weather.gov/points/$lat,$long")
      val res = req.send(backend)
      res.flatMap(deserialize[GridForecast]).map(_.properties.forecastHourly)

    private def deserialize[A: Decoder](r: Response[Either[String, String]]) =
      r.body.flatMap(decode[A]) match
        case Right(a)                => IO.pure(a)
        case Left(e: io.circe.Error) => IO.raiseError(Error(e))
        case Left(e: String) =>
          decode[NWError](e)
            .bimap(Error(_), Error(_))
            .fold(IO.raiseError(_), IO.raiseError(_))
