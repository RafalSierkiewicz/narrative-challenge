package narrative.analytics

import cats.effect.IO
import narrative.analytics.AnalyticsStore.{AnalyticData, AnalyticsMetrics}
import narrative.analytics.models.Event
import narrative.analytics.models.UserId.UserId

import java.time.Instant
import scala.collection.Map

trait AnalyticsStore {
  def store(userId: UserId, event: Event, at: Instant): IO[Unit]
  def getAnalytics(fromExclusive: Long, count: Long): fs2.Stream[IO, AnalyticData]
  def getMetrics(fromInclusive: Instant, toExclusive: Instant): IO[AnalyticsMetrics]
}

object AnalyticsStore {
  final case class AnalyticsMetrics(uniqueUsers: Long, eventCounts: Map[Event, Long])
  final case class AnalyticData(order: Long, at: Instant)
}
