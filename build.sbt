ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

val http4sVersion = "1.0.0-M40"

val dependencies: Seq[ModuleID] = Seq(
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.typelevel" %% "cats-effect" % "3.5.3",
  "io.circe" %% "circe-core" % "0.14.5",
  "org.typelevel" %% "log4cats-slf4j" % "2.6.0",
  "ch.qos.logback" % "logback-classic" % "1.4.7",
  "org.tpolecat" %% "doobie-core" % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-h2" % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC4",
  "org.flywaydb" % "flyway-core" % "10.7.2",
  "com.h2database" % "h2" % "2.2.220"
)

lazy val root = (project in file("."))
  .settings(
    name := "narrative",
    libraryDependencies ++= dependencies
  )
