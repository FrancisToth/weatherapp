package dev.contramap.weather

import dev.contramap.weather.nws.Service as NWService
import cats.syntax.apply.*

import weaver.*
import cats.effect.IO
import cats.data.ReaderT
import cats.data.Kleisli
import org.http4s.*
import io.circe.*
import io.circe.parser.*
import dev.contramap.weather.nws.Period
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

object ApiSpec2 extends SimpleIOSuite {
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

  test("should return the list of period returned by the NW service when cache is disabled") {
    (NWService.inMemory(Map(geo -> List(period))), Cache.noop[Geo, List[WeatherPoint]])
      .flatMapN(App.res(_, _))
      .useKleisli {
        App
          .dailyForecast(geo)
          .map { periods => expect(periods == wps) }
      }
  }

  test("should return the list of period returned by the NW service when cache is enbled") {
    (NWService.inMemory(Map(geo -> List(period))), Cache.live[Geo, List[WeatherPoint]](1.minute))
      .flatMapN(App.res(_, _))
      .useKleisli {
        App
          .dailyForecast(geo)
          .map { periods => expect(periods == wps) }
      }
  }

  test("should not hit the NW service if there is a list of points cached for a specific geo") {
    (NWService.inMemory(), Cache.live[Geo, List[WeatherPoint]](1.minute).evalTap(_.put(geo, wps)))
      .flatMapN(App.res(_, _))
      .useKleisli {
        App
          .dailyForecast(geo)
          .map { periods => expect(periods == wps) }
      }
  }

  final class App(
      routes: Kleisli[IO, Request[IO], Response[IO]],
      nwService: NWService.InMemory
  ) {
    def dailyForecast(geo: Geo) = {
      val Geo(lat, long) = geo
      val uri = Uri.unsafeFromString(s"http://localhost:8080/forecast?latitude=$lat&longitude=$long")
      val req = Request[IO](method = Method.GET, uri = uri)
      routes(req)
        .flatMap(
          _.bodyText.compile.lastOrError
            .flatMap(wps =>
              decode[List[WeatherPoint]](wps) match
                case Left(e)       => IO.raiseError(RuntimeException(e))
                case Right(points) => IO.pure(points)
            )
        )
    }
    def upsert(geo: Geo, periods: List[Period]) =
      nwService.upsert(geo, periods)
  }
  object App {
    def res(nwService: NWService.InMemory, cache: ForecastService.GeoCache) =
      ForecastService
        .res(nwService, cache)
        .flatMap(Main.routes(_))
        .map(App(_, nwService))

    def dailyForecast(geo: Geo): ReaderT[IO, App, List[WeatherPoint]] =
      ReaderT.ask[IO, App].flatMapF { _.dailyForecast(geo) }
  }
}
