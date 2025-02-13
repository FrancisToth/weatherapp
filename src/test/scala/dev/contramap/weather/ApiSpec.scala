package dev.contramap.weather

import cats.data.{Kleisli, ReaderT}
import cats.effect.IO
import cats.syntax.apply.*
import dev.contramap.weather.nws.{Period, Service as NWService}
import io.circe.*
import io.circe.parser.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.http4s.*
import scala.concurrent.duration.*
import weaver.*

object ApiSpec extends SimpleIOSuite {
  val geo = Geo(Latitude(39.7456), Longitude(-97.0892))
  val period = Period(
    startTime = Instant.now,
    endTime = Instant.now.plus(1, ChronoUnit.DAYS),
    temperature = Fahrenheit(42),
    shortForecast = "Cloudy"
  )
  val wps = List(
    WeatherPoint(period.startTime, Temperature(period.temperature), period.shortForecast)
  )

  val liveCacheRes = Cache.live[Geo, List[WeatherPoint]](1.minute)

  test("should return the list of period returned by the NW service when cache is disabled") {
    (NWService.inMemory(Map(geo -> List(period))), Cache.noop[Geo, List[WeatherPoint]])
      .flatMapN(App.res(_, _))
      .useKleisli {
        App.success(geo).map { (status, periods) => expect(status == Status.Ok) && expect(periods == wps) }
      }
  }

  test("should return the list of period returned by the NW service when cache is enabled") {
    (NWService.inMemory(Map(geo -> List(period))), liveCacheRes)
      .flatMapN(App.res(_, _))
      .useKleisli {
        App.success(geo).map { (status, periods) => expect(status == Status.Ok) && expect(periods == wps) }
      }
  }

  test("should not hit the NW service if there is a list of points cached for a specific geo") {
    (NWService.inMemory(), liveCacheRes.evalTap(_.put(geo, wps)))
      .flatMapN(App.res(_, _))
      .useKleisli {
        App.success(geo).map { (status, periods) => expect(status == Status.Ok) && expect(periods == wps) }
      }
  }

  test("should return 404 if no data could be found") {
    (NWService.inMemory(), liveCacheRes)
      .flatMapN(App.res(_, _))
      .useKleisli {
        App.error(geo).map { (status, error) =>
          expect(status == Status.NotFound) &&
            expect(error.message == "No data available for this location. Please try a location in the US.")
        }
      }
  }

  test("should return 400 if query parameters are not valid") {
    (NWService.inMemory(), liveCacheRes)
      .flatMapN(App.res(_, _))
      .useKleisli {
        App.query("lat=xxx").map { (status, error) =>
          expect(status == Status.BadRequest) &&
            expect(
              error.message == """Invalid query parameter (name: [lat], value: [xxx], reason: For input string: "xxx")"""
            )
        }
      }
  }

  final class App(routes: Kleisli[IO, Request[IO], Response[IO]]) {

    def send[A: Decoder](geo: Geo): IO[(Status, A)] =
      val Geo(lat, long) = geo
      send[A](s"lat=$lat&long=$long")

    def send[A: Decoder](query: String): IO[(Status, A)] =
      val uri = Uri.unsafeFromString(s"http://localhost:8080/forecast?$query")
      val req = Request[IO](method = Method.GET, uri = uri)

      routes(req).flatMap { res =>
        res.bodyText.compile.lastOrError
          .flatMap(raw =>
            decode[A](raw) match
              case Left(e)  => IO.raiseError(RuntimeException(e))
              case Right(a) => IO.pure((res.status, a))
          )
      }
  }
  object App {
    def res(nwService: NWService.InMemory, cache: ForecastService.GeoCache) =
      ForecastService
        .res(nwService, cache)
        .flatMap(Main.routes(_))
        .map(App(_))

    def success(geo: Geo): ReaderT[IO, App, (Status, List[WeatherPoint])] =
      ReaderT.ask[IO, App].flatMapF { _.send[List[WeatherPoint]](geo) }

    def error(geo: Geo): ReaderT[IO, App, (Status, ForecastService.Result.Error)] =
      ReaderT.ask[IO, App].flatMapF { _.send[ForecastService.Result.Error](geo) }

    def query(q: String): ReaderT[IO, App, (Status, ErrorDescription)] =
      ReaderT.ask[IO, App].flatMapF { _.send[ErrorDescription](q) }
  }
}
