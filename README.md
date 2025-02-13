# Weather app

The app can be run with `sbt run`.

## Endpoints

The forecast endpoint works like following:
```
curl -X GET -i "http://localhost:8080/forecast?lat={latitude}&long={longitude}"
```
example:
```
curl -X GET -i "http://localhost:8080/forecast?lat=39.7456&long=-97.0892"
```
Please note that the first query may take some time. This is due to cache getting initialized the first time it's queried.

The swagger documentation is available [here](http://localhost:8080/docs/#/default)

## TODO

Add integration tests for the NW service.
