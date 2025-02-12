package dev.contramap.weather

import cats.effect.IO
import io.chrisdavenport.mules.*
import cats.effect.Resource
import scala.concurrent.duration.FiniteDuration

trait Cache[K, V] {
  def put(key: K, value: V): IO[Unit]
  def get(key: K): IO[Option[V]]
}
object Cache {

  type Mule[K, V] = MemoryCache[IO, K, V]

  def noop[K, V]: Resource[IO, Cache[K, V]] =
    Resource.pure(new Cache[K, V] {
      override def put(key: K, value: V): IO[Unit] = IO.unit
      override def get(key: K): IO[Option[V]] = IO.none
    })

  def live[K, V](expiration: FiniteDuration): Resource[IO, Cache[K, V]] =
    TimeSpec.fromDuration(expiration) match
      case None =>
        Resource.raiseError[IO, Cache[K, V], Throwable](RuntimeException("Cache expiration duration cannot be negative!"))
      case ts => Resource.eval(MemoryCache.ofSingleImmutableMap[IO, K, V](ts).map(Live(_)))

  private final class Live[K, V](cache: Mule[K, V]) extends Cache[K, V] {
    override def get(key: K): IO[Option[V]] =
      cache.lookup(key)

    override def put(key: K, value: V): IO[Unit] =
      cache.insert(key, value)
  }

}
