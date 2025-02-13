package dev.contramap.weather

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.parallel.*
import dev.contramap.weather.nws.{Period, Service as NWService}
import io.circe.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.FiniteDuration
import sttp.tapir.Schema

trait ForecastService:
  def daily(geo: Geo): IO[ForecastService.Result]

object ForecastService:
  type GeoCache = dev.contramap.weather.Cache[Geo, List[WeatherPoint]]

  enum Result {
    case Success(wps: List[WeatherPoint])
    case Error(message: String)
  }
  object Result {
    val noData = Error("No data available for this location. Please try a location in the US.")

    given Codec[Result.Error] = Codec.derived
    given Codec[Success] = Codec.from(
      Decoder.decodeList[WeatherPoint].map(Success(_)),
      Encoder.encodeList[WeatherPoint].contramap(_.wps)
    )

    given Schema[Success] = Schema.schemaForIterable[WeatherPoint, List].as[Success]
    given Schema[Error]   = Schema.derived
  }

  def res(service: NWService, cache: GeoCache): Resource[IO, ForecastService] =
    Resource.pure(Live(service, cache))

  def live(cacheTimeout: FiniteDuration): Resource[IO, ForecastService] =
    (NWService.res, Cache.live(cacheTimeout)).parMapN(Live(_, _))

  private final class Live(
      nws: NWService,
      cache: GeoCache
  ) extends ForecastService:
    def daily(geo: Geo): IO[Result] = {
      cache.get(geo).flatMap {
        case Some(Nil) => IO.pure(Result.noData)
        case Some(wps) => IO.pure(Result.Success(wps))
        case None =>
          nws.points(geo).map { periods =>
            periods.sortBy(_.startTime) match
              case h :: t =>
                val day: Instant => Instant = _.truncatedTo(ChronoUnit.DAYS)
                // We keep today's points only
                Result.Success(
                  (h :: t.takeWhile(p => day(p.startTime) == day(h.startTime)))
                    .foldRight(List.empty[WeatherPoint])((p, wps) =>
                      val wp = WeatherPoint(p.startTime, Temperature(p.temperature), p.shortForecast)
                      wp :: wps
                    )
                )
              case Nil => Result.noData
          }
      }
    }
