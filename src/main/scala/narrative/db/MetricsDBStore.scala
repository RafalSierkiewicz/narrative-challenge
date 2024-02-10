package narrative.db

import cats.effect.IO
import doobie.{Read, Transactor}
import doobie.implicits.*
import doobie.postgres.implicits.*
import io.circe.KeyDecoder
import io.circe.syntax.*
import narrative.analytics.MetricsStore
import narrative.analytics.models.Event
import narrative.analytics.MetricsStore.EventMetrics

import java.time.Instant
import java.time.temporal.ChronoUnit

class MetricsDBStore(xa: Transactor[IO]) extends MetricsStore {
  private given KeyDecoder[Event] = KeyDecoder.decodeKeyString.map(str => Event.valueOf(str.toUpperCase))
  private given Read[EventMetrics] = Read[(Long, String)]
    .map { case (count, jsonMap) =>
      val countMap = io.circe.parser
        .decode[Map[Event, Long]](jsonMap)
        .getOrElse(throw new IllegalStateException(s"Json is not valid map of event -> long counts. Got $jsonMap"))

      EventMetrics(count, countMap)
    }
  override def find(at: Instant): IO[MetricsStore.EventMetrics] = {
    val query = sql"""SELECT user_count, events_count FROM event_metrics WHERE timestamp = $at"""

    query
      .query[EventMetrics]
      .option
      .transact(xa)
      .map(_.getOrElse(EventMetrics.empty))
  }

  override def insert(forTime: Instant, userCount: Long, eventsCount: Map[Event, Long]): IO[Unit] = {
    val jsonStr = eventsCount.map { case (k, v) => k.value -> v }.asJson.noSpaces
    val query =
      sql"""INSERT INTO event_metrics(timestamp, user_count, events_count)
             | VALUES ($forTime, ${userCount}, $jsonStr)
             | ON CONFLICT (timestamp) DO UPDATE
             |  SET user_count = ${userCount}, events_count = ${jsonStr}""".stripMargin

    query.update.run.transact(xa).void
  }
}
