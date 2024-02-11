package narrative.analytics

import cats.effect.{IO, Resource}
import munit.CatsEffectSuite
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import doobie.util.transactor.Transactor
import doobie.util.update.Update
import org.flywaydb.core.Flyway
import org.testcontainers.utility.DockerImageName
import cats.implicits.*
import fs2.concurrent.SignallingRef
import narrative.analytics.models.{Event, UserId}
import narrative.analytics.MetricsStore.EventMetrics
import narrative.db.Stores
import scala.concurrent.duration.*
import java.time.Instant
import java.time.temporal.ChronoUnit

class AnalyticsProcessorSpec extends CatsEffectSuite with TestContainerForAll {

  private val transactor = ResourceFunFixture {
    Resource
      .eval(IO {
        withContainers { postgres =>
          makeTransactor(postgres)
        }
      })
      .flatten
  }

  override val containerDef: PostgreSQLContainer.Def = {
    PostgreSQLContainer.Def(
      DockerImageName.parse("postgres:15-alpine"),
      databaseName = "postgres",
      username = "postgres",
      password = "postgres"
    )
  }

  transactor.test("should calculate metrics") { xa =>
    val stores = Stores.make(xa)
    val start = Instant.parse("2022-01-01T12:01:00Z")
    val data0 = List.tabulate(30)(num => ("user_id_1", if (num % 2 == 0) Event.CLICK else Event.IMPRESSION, start.plus(num, ChronoUnit.HOURS)))
    val data1 = List.tabulate(30)(num =>
      ("user_id_2", if (num % 2 == 0) Event.CLICK else Event.IMPRESSION, start.plus(10, ChronoUnit.MINUTES).plus(num, ChronoUnit.HOURS))
    )
    val data2 = List.tabulate(30)(num =>
      ("user_id_3", if (num % 2 == 0) Event.CLICK else Event.IMPRESSION, start.plus(30, ChronoUnit.MINUTES).plus(num, ChronoUnit.HOURS))
    )

    val insertF = (data0 ++ data1 ++ data2).traverse { case (user, event, at) => stores.analytics.store(UserId.apply(user), event, at) }

    val assertF = for {
      ref <- SignallingRef.of[IO, Boolean](false)
      processor <- AnalyticsProcessor.live(stores.analytics, stores.metrics, AnalyticsProcessor.ProcessorConfig(10.millis, 10))
      _ <- processor.stream.interruptWhen(ref).compile.drain.start
      // IO.sleep to give time to processor to process. Ideally in real world would be good to have some monitoring on processor
      // to know for example amount of entries processed. For the simplicity of test I assumed that sleep is enough
      _ <- insertF >> IO.sleep(500.millis) >> ref.set(true)
      metrics <- (
        stores.metrics.find(Instant.parse("2022-01-01T12:00:00Z")),
        stores.metrics.find(Instant.parse("2022-01-01T15:00:00Z")),
        stores.metrics.find(Instant.parse("2022-01-02T02:00:00Z")),
        stores.metrics.find(Instant.parse("2022-01-03T10:00:00Z"))
      ).tupled
    } yield metrics

    assertIO(
      assertF,
      (
        EventMetrics(3, Map(Event.CLICK -> 3, Event.IMPRESSION -> 0)),
        EventMetrics(3, Map(Event.CLICK -> 0, Event.IMPRESSION -> 3)),
        EventMetrics(3, Map(Event.CLICK -> 3, Event.IMPRESSION -> 0)),
        EventMetrics(0, Map(Event.CLICK -> 0, Event.IMPRESSION -> 0))
      )
    )
  }

  def makeTransactor(container: PostgreSQLContainer): Resource[IO, Transactor[IO]] = {
    import doobie.implicits.*
    val dbHost = container.host
    val dbPort = container.mappedPort(container.exposedPorts.head)
    val url = s"jdbc:postgresql://$dbHost:$dbPort/postgres?tcpKeepAlive=true&targetServerType=primary&currentSchema=test"

    for {
      xa <- Resource.pure(
        Transactor.fromDriverManager[IO](
          driver = "org.postgresql.Driver",
          url = url,
          user = "postgres",
          password = "postgres",
          logHandler = None
        )
      )
      _ <- Resource.eval(Update[Unit](s"DROP SCHEMA IF EXISTS test CASCADE").run(()).transact(xa))
      _ <- Resource.eval(runMigrations(url))
    } yield xa
  }

  private def runMigrations(url: String) = {
    IO(
      Flyway
        .configure()
        .loggers("slf4j")
        .dataSource(url, "postgres", "postgres")
    )
      .map { flywayConfig =>
        val flyway = flywayConfig.load()
        flyway.migrate()
        flyway.validate()
      }
  }
}
