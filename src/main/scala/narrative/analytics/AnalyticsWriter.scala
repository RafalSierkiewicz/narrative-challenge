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
  def live(xa: Transactor[IO]): AnalyticsWriter = new AnalyticsWriter {
    override def write(userId: UserId, event: Event, at: Instant): IO[Unit] = {
      val updateQuery =
        sql"""INSERT INTO events(user_id, event, at)
          VALUES (${userId.value}, ${event.value}, $at)
           """.stripMargin

      updateQuery.update.run.transact(xa).void
    }
  }
}
