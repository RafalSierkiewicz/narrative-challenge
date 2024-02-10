import cats.effect.{ExitCode, IO, IOApp, Resource, Sync}
import doobie.Transactor
import narrative.http.Server
import narrative.analytics.{AnalyticsProcessor, AnalyticsWriter, EventMetricsReader}
import narrative.db.Stores
import org.flywaydb.core.Flyway

object NarrativeChallenge extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    (for {
      xa <- makeXa
      stores = Stores.make(xa)
      processor <- Resource.eval(AnalyticsProcessor.live(stores.analytics, stores.metrics).flatMap(_.run.start))
      server <- Server
        .make(AnalyticsWriter.live(stores.analytics), EventMetricsReader.live(stores.metrics))
        .onFinalize(processor.cancel)
    } yield server).useForever
      .as(ExitCode.Success)
  }

  private def makeXa = {
    val xa = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = s"jdbc:postgresql://localhost:5432/narrative?tcpKeepAlive=true&targetServerType=primary",
      user = "postgres",
      password = "postgres",
      logHandler = None
    )
    Resource
      .eval(runMigrations)
      .map(_ => xa)
  }

  private def runMigrations[IO] = {
    IO(
      Flyway
        .configure()
        .loggers("slf4j")
        .dataSource("jdbc:postgresql://localhost:5432/narrative", "postgres", "postgres")
    )
      .map { flywayConfig =>
        val flyway = flywayConfig.load()
        flyway.migrate()
        flyway.validate()
      }
  }
}
