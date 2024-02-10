package narrative.analytics

import cats.effect.IO
import doobie.{Read, Transactor}
import doobie.implicits.*
import doobie.postgres.implicits.*
import io.circe.KeyDecoder
import narrative.analytics.EventMetricsReader.EventMetrics
import narrative.analytics.models.Event

import java.time.Instant
import java.time.temporal.ChronoUnit

trait EventMetricsReader {
  def getMetrics(at: Instant): IO[EventMetrics]
}

object EventMetricsReader {
  final case class EventMetrics(uniqueUsers: Long, eventsCount: Map[Event, Long])

  object EventMetrics {
    def empty: EventMetrics = EventMetrics(0, Event.values.map(_ -> 0L).toMap)
  }

  private given KeyDecoder[Event] = KeyDecoder.decodeKeyString.map(Event.valueOf)
  private given Read[EventMetrics] = Read[(Long, String)]
    .map { case (count, jsonMap) =>
      val countMap = io.circe.parser
        .decode[Map[Event, Long]](jsonMap)
        .getOrElse(throw new IllegalStateException(s"Json is not valid map of event -> long counts. Got $jsonMap"))

      EventMetrics(count, countMap)
    }

  def live(xa: Transactor[IO]): EventMetricsReader = new EventMetricsReader:
    override def getMetrics(at: Instant): IO[EventMetrics] = {
      val timestampFor = at.truncatedTo(ChronoUnit.HOURS)
      val query = sql"""SELECT user_count, events_count FROM event_metrics WHERE timestamp = $timestampFor"""

      query
        .query[EventMetrics]
        .option
        .transact(xa)
        .map(_.getOrElse(EventMetrics.empty))
    }
}
