package narrative.analytics

import cats.effect.{IO, Ref}
import doobie.util.transactor.Transactor
import narrative.analytics.models.Event
import cats.implicits.*
import io.circe.syntax.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*
import scala.collection.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.ConnectionIO

trait AnalyticsProcessor {
  def run: IO[Unit]
}

object AnalyticsProcessor {

  final private case class Data(order: Long, at: Instant)
  final private case class Metrics(uniqueUsers: Long, eventCounts: Map[Event, Long])

  def live(xa: Transactor[IO]): IO[AnalyticsProcessor] = {
    // Ref is used as a memory of last processed event as it is something like projection, in real life - database table
    // would be required. For the sake of simplicity I used Ref as everything is in memory
    Ref[IO]
      .of(0L)
      .map(make(xa, _))
  }

  private def make(xa: Transactor[IO], lastProcessed: Ref[IO, Long]) = new AnalyticsProcessor {
    override def run: IO[Unit] = {
      fs2.Stream
        .awakeEvery[IO](1.second)
        .evalMap(_ => lastProcessed.get)
        .flatMap(getNextBatch(_).transact(xa))
        .chunkMin(1)
        .map(_.foldLeft(mutable.Map[Instant, Long]()) { case (acc, data) =>
          acc.update(data.at.truncatedTo(ChronoUnit.HOURS), data.order)
          acc
        })
        .evalMap { map =>
          map.toList
            .parTraverse { case (from, to) =>
              getMetrics(from, to)
                .transact(xa)
                // if ref would be a proper table that should go in one transaction as well as insertion logic
                .flatTap(_ => lastProcessed.update { prev => if (prev > to) prev else to })
                .flatMap(insertMetrics(from, _).transact(xa))
            }
        }
        .compile
        .drain
    }
  }

  private def getNextBatch(fromExclusive: Long, count: Long = 10_000): fs2.Stream[ConnectionIO, Data] = {
    val query =
      sql"""SELECT ordering, at FROM events WHERE ordering > $fromExclusive LIMIT $count """.stripMargin

    query
      .query[(Long, Instant)]
      .map { case (order, at) => Data(order, at) }
      .stream
  }

  private def getMetrics(from: Instant, toInclusive: Long): ConnectionIO[Metrics] = {
    val query =
      sql"""SELECT COUNT(DISTINCT user_id),
           |  COUNT(event) filter ( where event = ${Event.CLICK.value}),
           |  COUNT(event) filter ( where event =  ${Event.IMPRESSION.value})
           | FROM events 
           | WHERE at >= $from AND ordering <= $toInclusive """.stripMargin

    query
      .query[(Long, Long, Long)]
      .map { case (l1, l2, l3) => Metrics(l1, Map(Event.CLICK -> l2, Event.IMPRESSION -> l3)) }
      .option
      .map(_.getOrElse(Metrics(0, Map.empty)))
  }

  private def insertMetrics(forTime: Instant, metrics: Metrics): ConnectionIO[Unit] = {
    val jsonStr = metrics.eventCounts.map { case (k, v) => k.value -> v }.toMap.asJson.noSpaces
    val query =
      sql"""INSERT INTO event_metrics(timestamp, user_count, events_count)
           | VALUES ($forTime, ${metrics.uniqueUsers}, $jsonStr)
           | ON CONFLICT (timestamp) DO UPDATE
           |  SET user_count = ${metrics.uniqueUsers}, events_count = ${jsonStr}""".stripMargin

    query.update.run.void
  }
}
