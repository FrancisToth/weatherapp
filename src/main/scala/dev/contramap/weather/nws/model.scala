package dev.contramap.weather
package nws

import io.circe.*
import java.time.Instant
import sttp.model.Uri

final case class Period(
    startTime: Instant,
    endTime: Instant,
    temperature: Fahrenheit,
    shortForecast: String
) derives Codec.AsObject

final case class Forecast(properties: Forecast.Properties) derives Codec.AsObject
object Forecast:
  final case class Properties(periods: List[Period]) derives Codec.AsObject

final case class GridForecast(properties: GridForecast.Properties) derives Codec.AsObject
object GridForecast:
  final case class Properties(forecastHourly: Uri) derives Codec.AsObject
  given Codec[Uri] = Codec.from(
    Decoder.decodeString.emap(Uri.parse),
    Encoder.encodeString.contramap[Uri](_.toString)
  )
