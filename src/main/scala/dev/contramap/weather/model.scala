package dev.contramap.weather

import cats.syntax.either.*
import io.circe.{Codec, Decoder, Encoder}
import java.time.Instant
import sttp.tapir
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.Schema

enum Temperature:
  case Cold, Hot, Moderate
  val name = productPrefix
object Temperature:
  def apply(t: Fahrenheit): Temperature =
    if (t.value > 77) Temperature.Hot
    else if (t.value < 50) Temperature.Cold
    else Temperature.Moderate

  inline given Schema[Temperature] =
    Schema.derivedEnumeration.defaultStringBased

  given Codec[Temperature] = {
    val enc = Encoder.encodeString.contramap[Temperature](_.name)
    val dec = Decoder.decodeString.emap(s =>
      Temperature.values
        .find(_.name.toLowerCase == s.toLowerCase())
        .toRight("Not a valid Temp")
    )
    Codec.from(dec, enc)
  }

opaque type Fahrenheit = Int
object Fahrenheit:
  def apply(x: Int): Fahrenheit = x

  extension (x: Fahrenheit) def value: Int = x

  given Codec[Fahrenheit] = Codec.from(Decoder.decodeInt, Encoder.encodeInt)

opaque type Latitude = Double
object Latitude:
  def apply(x: Double): Latitude = x

  given Codec[Latitude]                          = Codec.from(Decoder.decodeDouble, Encoder.encodeDouble)
  given tapir.Codec[String, Latitude, TextPlain] = tapir.Codec.double
  given tapir.Schema[Latitude]                   = tapir.Schema.schemaForDouble

opaque type Longitude = Double
object Longitude:
  def apply(x: Double): Longitude = x

  given Codec[Longitude]                          = Codec.from(Decoder.decodeDouble, Encoder.encodeDouble)
  given tapir.Codec[String, Longitude, TextPlain] = tapir.Codec.double
  given tapir.Schema[Longitude]                   = tapir.Schema.schemaForDouble

final case class Geo(lat: Latitude, long: Longitude)
final case class WeatherPoint(
    timestamp: Instant,
    temperature: Temperature,
    weather: String
) derives Codec.AsObject,
      Schema
