package dev.contramap.weather

import sttp.tapir.*
import sttp.tapir.json.circe.jsonBody

final class DailyForecast(service: ForecastService):
  val route = {
    val input = query[Latitude]("latitude")
      .and(query[Longitude]("longitude"))
      .mapTo[Geo]

    endpoint.get
      .in("forecast")
      .in(input)
      .out(jsonBody[List[WeatherPoint]])
      .serverLogicSuccess(service.daily)
  }
