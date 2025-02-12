package dev.contramap.weather

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.either.*
import cats.syntax.option.*
import cats.syntax.parallel.*
import dev.contramap.weather.nws.Period
import dev.contramap.weather.nws.Service as NWService
import io.circe.*
import io.circe.parser.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import sttp.client3.*
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.tapir.Schema.annotations.default

import java.time.Instant
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.FiniteDuration

trait ForecastService:
  def daily(geo: Geo): IO[List[WeatherPoint]]
object ForecastService:
  type GeoCache = dev.contramap.weather.Cache[Geo, List[WeatherPoint]]

  def res(service: NWService, cache: GeoCache): Resource[IO, ForecastService] =
    Resource.pure(Live(service, cache))

  def live(cacheTimeout: FiniteDuration): Resource[IO, ForecastService] = {
    (NWService.res, Cache.live(cacheTimeout)).parMapN(Live(_, _))
  }

  private final class Live(
      nws: NWService,
      cache: GeoCache
  ) extends ForecastService:
    def daily(geo: Geo): IO[List[WeatherPoint]] = {
      cache.get(geo).flatMap {
        case Some(wps) => IO.pure(wps)
        case None =>
          nws.points(geo).map { periods =>
            periods.sortBy(_.startTime) match
              case h :: t =>
                val day: Instant => Instant = _.truncatedTo(ChronoUnit.DAYS)
                // We keep today's points only
                (h :: t.takeWhile(p => day(p.startTime) == day(h.startTime)))
                  .foldRight(List.empty[WeatherPoint])((p, wps) =>
                    val wp = WeatherPoint(p.startTime, Temperature(p.temperature), p.shortForecast)
                    wp :: wps
                  )
              case Nil => List.empty
          }
      }
    }
