package dev.contramap.weather

import cats.syntax.either.*
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Interval
import sttp.tapir
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.Schema

import java.time.Instant
import java.time.temporal.ChronoField

enum Temperature derives Schema:
  case Cold, Hot, Moderate
  val name = productPrefix
object Temperature:
  def apply(t: Fahrenheit): Temperature =
    if (t.value > 77) Temperature.Hot
    else if (t.value < 50) Temperature.Cold
    else Temperature.Moderate

  given Codec[Temperature] = {
    val enc = Encoder.encodeString.contramap[Temperature](_.name)
    val dec = Decoder.decodeString.emap(s =>
      Temperature.values
        .find(_.name.toLowerCase == s.toLowerCase())
        .toRight(s"Not a valid Temp")
    )
    Codec.from(dec, enc)
  }

opaque type Fahrenheit = Int
object Fahrenheit:
  extension (x: Fahrenheit) def value: Int = x
  given Codec[Fahrenheit] = Codec.from(Decoder.decodeInt, Encoder.encodeInt)
  def apply(x: Int): Fahrenheit = x

opaque type Latitude = Double
object Latitude:
  given Codec[Latitude] = Codec.from(Decoder.decodeDouble, Encoder.encodeDouble)
  given tapir.Codec[String, Latitude, TextPlain] = tapir.Codec.double
  given tapir.Schema[Latitude] = tapir.Schema.schemaForDouble
  def apply(x: Double): Latitude = x

opaque type Longitude = Double
object Longitude:
  given Codec[Longitude] = Codec.from(Decoder.decodeDouble, Encoder.encodeDouble)
  given tapir.Codec[String, Longitude, TextPlain] = tapir.Codec.double
  given tapir.Schema[Longitude] = tapir.Schema.schemaForDouble
  def apply(x: Double): Longitude = x

final case class Geo(lat: Latitude, long: Longitude)
final case class WeatherPoint(
    timestamp: Instant,
    temperature: Temperature,
    weather: String
) derives Codec.AsObject,
      Schema
