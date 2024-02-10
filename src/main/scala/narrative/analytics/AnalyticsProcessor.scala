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

  def live(analyticsStore: AnalyticsStore, metricsStore: MetricsStore): IO[AnalyticsProcessor] = {
    // Ref is used as a memory of last processed event as it is something like projection, in real life - database table
    // would be required. For the sake of simplicity I used Ref as everything is in memory
    Ref[IO]
      .of(0L)
      .map(make(analyticsStore, metricsStore, _))
  }

  private def make(analyticsStore: AnalyticsStore, metricsStore: MetricsStore, lastProcessed: Ref[IO, Long]) = new AnalyticsProcessor {
    override def run: IO[Unit] = {
      fs2.Stream
        .awakeEvery[IO](1.second)
        .evalMap(_ => lastProcessed.get)
        .flatMap(getNextBatch(_))
        .chunkMin(1)
        .map(_.foldLeft(mutable.Map[Instant, Long]()) { case (acc, data) =>
          acc.update(data.at.truncatedTo(ChronoUnit.HOURS), data.order)
          acc
        })
        .evalMap { map =>
          map.toList
            .parTraverse { case (from, to) =>
              getMetrics(from, to)
                // if ref would be a proper table that should go in one transaction as well as insertion logic
                .flatTap(_ => lastProcessed.update { prev => if (prev > to) prev else to })
                .flatMap(insertMetrics(from, _))
            }
        }
        .compile
        .drain
    }

    private def getNextBatch(fromExclusive: Long, count: Long = 10_000): fs2.Stream[IO, AnalyticsStore.AnalyticData] =
      analyticsStore.getAnalytics(fromExclusive, count)

    private def getMetrics(from: Instant, toInclusive: Long): IO[AnalyticsStore.AnalyticsMetrics] =
      analyticsStore.getMetrics(from, toInclusive)

    private def insertMetrics(forTime: Instant, metrics: AnalyticsStore.AnalyticsMetrics): IO[Unit] =
      metricsStore.insert(forTime, metrics.uniqueUsers, metrics.eventCounts.toMap)
  }
}
