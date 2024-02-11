ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

val http4sVersion = "1.0.0-M40"
val circeVersion = "0.14.5"
val doobieVersion = "1.0.0-RC4"

val dependencies: Seq[ModuleID] = Seq(
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.typelevel" %% "cats-effect" % "3.5.3",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "org.typelevel" %% "log4cats-slf4j" % "2.6.0",
  "ch.qos.logback" % "logback-classic" % "1.4.7",
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion,
  "org.flywaydb" % "flyway-core" % "10.7.2",
  "org.postgresql" % "postgresql" % "42.7.1",
  "org.flywaydb" % "flyway-database-postgresql" % "10.7.2",
  "org.typelevel" %% "munit-cats-effect" % "2.0.0-M4" % Test,
  "com.dimafeng" %% "testcontainers-scala-munit" % "0.40.17" % Test,
  "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.40.17" % Test,
  "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.10.3" % Test,
  "io.gatling" % "gatling-test-framework" % "3.10.3" % Test
)

lazy val root = (project in file("."))
  .settings(
    name := "narrative",
    libraryDependencies ++= dependencies
  )
  .enablePlugins(GatlingPlugin)
