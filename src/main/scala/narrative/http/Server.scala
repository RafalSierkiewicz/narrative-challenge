package narrative.http

import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.dsl.io.*

import java.time.Instant
import org.http4s.ember.server.*
import com.comcast.ip4s.*
import narrative.analytics.AnalyticsWriter
import narrative.analytics.models.{Event, UserId}
import narrative.analytics.models.UserId.UserId
import org.http4s.server.{Router, Server}
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.{Logger, LoggerFactory}

object Server {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger = Logger(LoggerFactory.getLoggerFromClass[IO](getClass))
  private given QueryParamDecoder[Instant] = QueryParamDecoder[Long].map(Instant.ofEpochMilli)
  private given QueryParamDecoder[UserId] = QueryParamDecoder[String].map(UserId.apply)
  private given QueryParamDecoder[Event] =
    QueryParamDecoder[String].emap(Event.fromString(_).left.map(error => ParseFailure(error, error)))

  private object InstantQueryMatcher extends QueryParamDecoderMatcher[Instant]("timestamp")
  private object UserIdQueryMatcher extends QueryParamDecoderMatcher[UserId]("user_id")
  private object EventQueryMatcher extends QueryParamDecoderMatcher[Event]("event")

  private def routes(writer: AnalyticsWriter): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case POST -> Root :? InstantQueryMatcher(timestamp) +& UserIdQueryMatcher(userId) +& EventQueryMatcher(event) =>
      writer
        .write(userId, event, timestamp)
        .flatMap(_ => NoContent())
        .onError(err => logger.error(err)("Errro"))
    case GET -> Root :? InstantQueryMatcher(timestamp) =>
      Ok()
  }

  def make(writer: AnalyticsWriter): Resource[IO, Server] = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8080")
    .withHttpApp(Router("/analytics" -> routes(writer)).orNotFound)
    .build

}
