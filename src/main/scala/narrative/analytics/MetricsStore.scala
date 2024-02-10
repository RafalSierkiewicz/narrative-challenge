package narrative.analytics

import cats.effect.IO
import narrative.analytics.models.Event
import narrative.analytics.MetricsStore.EventMetrics

import java.time.Instant

trait MetricsStore {
  def find(at: Instant): IO[EventMetrics]
  def insert(forTime: Instant, userCount: Long, eventsCount: Map[Event, Long]): IO[Unit]
}

object MetricsStore {
  final case class EventMetrics(uniqueUsers: Long, eventsCount: Map[Event, Long])

  object EventMetrics {
    def empty: EventMetrics = EventMetrics(0, Event.values.map(_ -> 0L).toMap)
  }
}
