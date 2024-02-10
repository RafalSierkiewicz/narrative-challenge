import cats.effect.{ExitCode, IO, IOApp, Resource, Sync}
import narrative.http.Server
import doobie.h2.*
import doobie.ExecutionContexts
import narrative.analytics.{AnalyticsWriter, EventMetricsReader}
import org.flywaydb.core.Flyway

object NarrativeChallenge extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    (for {
      xa <- makeXa
      server <- Server.make(AnalyticsWriter.live(xa), EventMetricsReader.live(xa))
    } yield server).useForever
      .as(ExitCode.Success)
  }

  private def makeXa = {
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      xa <- H2Transactor.newH2Transactor[IO]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "", ce)
      _ <- Resource.eval(runMigrations)
    } yield xa
  }

  def runMigrations[IO] = {
    IO(Flyway.configure().loggers("slf4j").dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", ""))
      .map { flywayConfig =>
        val flyway = flywayConfig.load()
        flyway.migrate()
        flyway.validate()
      }
  }
}
