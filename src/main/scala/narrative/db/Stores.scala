package narrative.db

import cats.effect.IO
import doobie.util.transactor.Transactor
import narrative.analytics.{AnalyticsStore, MetricsStore}

final case class Stores(analytics: AnalyticsStore, metrics: MetricsStore)

object Stores {

  def make(xa: Transactor[IO]): Stores = Stores(AnalyticsDBStore(xa), MetricsDBStore(xa))

}
