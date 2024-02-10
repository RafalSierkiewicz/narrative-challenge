package narrative.analytics

import cats.effect.IO
import doobie.{Read, Transactor}
import doobie.implicits.*
import doobie.postgres.implicits.*
import io.circe.KeyDecoder
import narrative.analytics.models.Event
import narrative.analytics.MetricsStore.EventMetrics

import java.time.Instant
import java.time.temporal.ChronoUnit

trait EventMetricsReader {
  def getMetrics(at: Instant): IO[EventMetrics]
}

object EventMetricsReader {
  def live(store: MetricsStore): EventMetricsReader = new EventMetricsReader:
    override def getMetrics(at: Instant): IO[EventMetrics] = {
      val timestampFor = at.truncatedTo(ChronoUnit.HOURS)

      store.find(timestampFor)
    }
}
