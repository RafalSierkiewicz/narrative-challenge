package narrative

import cats.effect.{IO, Resource}
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.{Transactor, Update}
import doobie.implicits.*
import org.flywaydb.core.Flyway
import org.testcontainers.utility.DockerImageName

object TestUtils {

  def containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    DockerImageName.parse("postgres:15-alpine"),
    databaseName = "postgres",
    username = "postgres",
    password = "postgres"
  )
  
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
