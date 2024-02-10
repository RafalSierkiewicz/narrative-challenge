package narrative.analytics

import cats.effect.IO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import narrative.analytics.models.{Event, UserId}
import narrative.analytics.models.UserId.*

import java.time.Instant

trait AnalyticsWriter {
  def write(userId: UserId, event: Event, at: Instant): IO[Unit]
}

object AnalyticsWriter {
  def live(store: AnalyticsStore): AnalyticsWriter = new AnalyticsWriter {
    override def write(userId: UserId, event: Event, at: Instant): IO[Unit] =
      store.store(userId, event, at)
  }
}
