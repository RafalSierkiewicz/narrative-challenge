package narrative.db

import cats.effect.IO
import doobie.Transactor
import narrative.analytics.AnalyticsStore
import narrative.analytics.models.Event
import narrative.analytics.models.UserId.UserId
import doobie.implicits.*
import doobie.postgres.implicits.*
import narrative.analytics.AnalyticsStore.{AnalyticData, AnalyticsMetrics}

import java.time.Instant

class AnalyticsDBStore(xa: Transactor[IO]) extends AnalyticsStore {
  override def store(userId: UserId, event: Event, at: Instant): IO[Unit] = {
    val updateQuery = sql"""INSERT INTO events(user_id, event, at)
           | VALUES (${userId.value}, ${event.value}, $at)
           """.stripMargin

    updateQuery.update.run.transact(xa).void
  }

  override def getMetrics(from: Instant, toOrderingInclusive: Long): IO[AnalyticsMetrics] = {
    val query =
      sql"""SELECT COUNT(DISTINCT user_id),
           |  COUNT(event) filter ( where event = ${Event.CLICK.value}),
           |  COUNT(event) filter ( where event =  ${Event.IMPRESSION.value})
           | FROM events 
           | WHERE at >= $from AND ordering <= $toOrderingInclusive """.stripMargin

    query
      .query[(Long, Long, Long)]
      .map { case (l1, l2, l3) => AnalyticsMetrics(l1, Map(Event.CLICK -> l2, Event.IMPRESSION -> l3)) }
      .option
      .map(_.getOrElse(AnalyticsMetrics(0, Map.empty)))
      .transact(xa)
  }

  override def getAnalytics(fromExclusive: Long, count: Long ): fs2.Stream[IO, AnalyticData] = {
    val query =
      sql"""SELECT ordering, at FROM events WHERE ordering > $fromExclusive LIMIT $count """.stripMargin

    query
      .query[(Long, Instant)]
      .map { case (order, at) => AnalyticData(order, at) }
      .stream
      .transact(xa)
  }
}
