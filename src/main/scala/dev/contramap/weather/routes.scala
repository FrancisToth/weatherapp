package dev.contramap.weather

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.jsonBody

final class DailyForecast(service: ForecastService):
  val route = {
    val input = query[Latitude]("lat")
      .and(query[Longitude]("long"))
      .mapTo[Geo]

    endpoint.get
      .in("forecast")
      .in(input)
      .out(
        oneOf[ForecastService.Result](
          oneOfVariantValueMatcher(statusCode(StatusCode.NotFound).and(jsonBody[ForecastService.Result.Error])) {
            case ForecastService.Result.Error(_) => true
          },
          oneOfVariantValueMatcher(statusCode(StatusCode.Ok).and(jsonBody[ForecastService.Result.Success])) {
            case ForecastService.Result.Success(_) => true
          }
        )
      )
      .errorOut(statusCode(StatusCode.BadRequest).and(jsonBody[ErrorDescription]))
      .serverLogicSuccess(service.daily)
  }
