package narrative.analytics

import cats.effect.{FiberIO, Resource}
import narrative.db.Stores

final case class Analytics(processorRef: FiberIO[Unit], writer: AnalyticsWriter, reader: EventMetricsReader)
object Analytics {
  def make(stores: Stores) = {
    for {
      processor <- Resource.eval(AnalyticsProcessor.live(stores.analytics, stores.metrics).flatMap(_.run.start))
      (writer, reader) = (AnalyticsWriter.live(stores.analytics), EventMetricsReader.live(stores.metrics))
    } yield Analytics(processor, writer, reader)
  }
}
