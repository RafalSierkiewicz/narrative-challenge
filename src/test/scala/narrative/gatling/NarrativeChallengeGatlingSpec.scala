package narrative.gatling

import io.gatling.commons.util.TypeCaster
import io.gatling.commons.util.TypeCaster.given
import io.gatling.core.Predef.*
import io.gatling.core.session.Expression
import io.gatling.http.Predef.*
import narrative.{NarrativeChallenge, TestUtils}
import cats.effect.unsafe.implicits.global
import cats.effect.IO
import org.http4s.server.Server

import scala.reflect.ClassTag
import scala.concurrent.duration.*

class NarrativeChallengeGatlingSpec extends Simulation {

  private var server: Server = _
  private var closeF: IO[Unit] = _
  private val container = TestUtils.containerDef.createContainer()

  before {
    container.start()
    val url =
      s"jdbc:postgresql://${container.host}:${container.mappedPort(container.exposedPorts.head)}/postgres?tcpKeepAlive=true&targetServerType=primary&currentSchema=test"
    val app = NarrativeChallenge.resource(url, "postgres", "postgres").allocated.unsafeRunSync()
    server = app._1
    closeF = app._2
  }

  after {
    container.stop()
    closeF.unsafeRunSync()
  }

  given toExpr(using typeCaster: TypeCaster[String], classTag: ClassTag[String]): Conversion[String, Expression[String]] with
    override def apply(x: String): Expression[String] = {
      val tag = summon[ClassTag[String]]
      val typeCaster = summon[ClassTag[String]]
      stringToExpression(x)
    }

  private def analyticsFeeder = csv("analytics.csv").shuffle

  private val insert = exec(
    http("Insert")
      .post("/analytics?timestamp=#{timestamp}&user=#{user_id}&event=#{event}")
      .check(status.is(204))
  )

  private val retrieve = exec(
    http("Select")
      .get("/analytics?timestamp=#{timestamp}")
      .check(status.is(200))
      .check(
        bodyString.transform(_.trim).is("unique_users,#{user_count}\nclicks,#{clicks}\nimpressions,#{impressions}")
      )
  )

  private val retrieveEmpty = exec(
    http("Select")
      .get("/analytics?timestamp=1641042060000")
      .check(status.is(200))
      .check(
        bodyString.transform(_.trim).is("unique_users,0\nclicks,0\nimpressions,0")
      )
  )

  private val httpProtocol = http.baseUrl("http://localhost:8080")

  private val analyticsInsert = scenario("Analytics Insert")
    .repeat(2000) {
      feed(analyticsFeeder).exec(insert)
    }
    .pause(1.second)

  private val analyticsRetrieve = scenario("Analytics Retrieve").repeat(2000) {
    feed(analyticsFeeder).exec(retrieve)
  }

  private val analyticsEmptyRetrieve = scenario("Analytics Empty Retrieve").exec(retrieveEmpty)

  setUp(
    analyticsInsert
      .inject(atOnceUsers(1))
      .andThen(analyticsRetrieve.inject(atOnceUsers(1)))
      .andThen(analyticsEmptyRetrieve.inject(atOnceUsers(1)))
  ).protocols(httpProtocol)
}
