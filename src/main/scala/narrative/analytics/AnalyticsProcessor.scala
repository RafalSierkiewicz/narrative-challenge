package narrative.analytics

import cats.effect.{IO, Ref}
import doobie.util.transactor.Transactor
import narrative.analytics.models.Event
import cats.implicits.*
import org.typelevel.log4cats.{Logger, LoggerFactory}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*
import scala.collection.*

trait AnalyticsProcessor {
  def run: IO[Unit]
  def stream: fs2.Stream[IO, Unit]
}

object AnalyticsProcessor {
  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  final case class ProcessorConfig(interval: FiniteDuration = 1.second, batchSize: Int = 10_000)

  def live(analyticsStore: AnalyticsStore, metricsStore: MetricsStore, config: ProcessorConfig = ProcessorConfig()): IO[AnalyticsProcessor] = {
    // Ref is used as a memory of last processed event as it is something like projection, in real life - database table
    // would be required. For the sake of simplicity I used Ref as everything is in memory
    Ref[IO]
      .of(0L)
      .map(make(analyticsStore, metricsStore, _, config))
  }

  private def make(analyticsStore: AnalyticsStore, metricsStore: MetricsStore, lastProcessed: Ref[IO, Long], config: ProcessorConfig = ProcessorConfig()) =
    new AnalyticsProcessor {
      override def run: IO[Unit] =
        stream.compile.drain

      override def stream: fs2.Stream[IO, Unit] = {
        fs2.Stream
          .awakeEvery[IO](config.interval)
          .evalMap(_ => lastProcessed.get)
          .flatMap { last =>
            getNextBatch(last, config.batchSize)
              .chunkMin(config.batchSize)
              .map(_.foldLeft(mutable.Map[Instant, Long]()) { case (acc, data) =>
                acc.update(data.at.truncatedTo(ChronoUnit.HOURS), data.order)
                acc
              })
              .evalMap { map =>
                map.toList
                  .traverse { case (from, to) =>
                    getMetrics(from)
                      // if ref would be a proper table that should go in one transaction as well as insertion logic
                      .flatTap(_ => lastProcessed.update { prev => if (prev > to) prev else to })
                      .flatMap(insertMetrics(from, _))
                  }
              }
          }
          .void
      }

      private def getNextBatch(fromExclusive: Long, count: Long = 10_000): fs2.Stream[IO, AnalyticsStore.AnalyticData] =
        analyticsStore.getAnalytics(fromExclusive, count)

      private def getMetrics(from: Instant): IO[AnalyticsStore.AnalyticsMetrics] =
        analyticsStore.getMetrics(from, from.plus(1, ChronoUnit.HOURS))

      private def insertMetrics(forTime: Instant, metrics: AnalyticsStore.AnalyticsMetrics): IO[Unit] =
        metricsStore.insert(forTime, metrics.uniqueUsers, metrics.eventCounts.toMap)
    }
}
