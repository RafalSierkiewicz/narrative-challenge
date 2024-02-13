package narrative

import cats.effect.{ExitCode, IO, IOApp, Resource}
import doobie.Transactor
import narrative.http.Server
import narrative.analytics.{Analytics, AnalyticsProcessor, AnalyticsWriter, EventMetricsReader}
import narrative.db.Stores
import org.flywaydb.core.Flyway
import org.http4s.server.Server

object NarrativeChallenge extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    resource(s"jdbc:postgresql://localhost:5432/narrative?tcpKeepAlive=true&targetServerType=primary", "postgres", "postgres").useForever
      .as(ExitCode.Success)
  }

  def resource(url: String, user: String, password: String): Resource[IO, Server] = {
    for {
      xa <- makeXa(url, user, password)
      stores = Stores.make(xa)
      analytics <- Analytics.make(stores)
      server <- Server
        .make(analytics.writer, analytics.reader)
        .onFinalize(analytics.processorRef.cancel)
    } yield server
  }

  private def makeXa(url: String, user: String, password: String) = {
    val xa = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = url,
      user = user,
      password = password,
      logHandler = None
    )
    Resource
      .eval(runMigrations(url, user, password))
      .map(_ => xa)
  }

  private def runMigrations(url: String, user: String, password: String) = {
    IO(
      Flyway.configure().loggers("slf4j").dataSource(url, user, password)
    )
      .map { flywayConfig =>
        val flyway = flywayConfig.load()
        flyway.migrate()
        flyway.validate()
      }
  }
}
