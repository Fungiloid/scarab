ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.10"

val http4sVersion = "0.23.19-RC3"
val skunkVersion = "0.6.0-RC2"

lazy val root = (project in file("."))
  .settings(
    name := "scarab",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.6.1",
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      // Optional for auto-derivation of JSON codecs
      "io.circe" %% "circe-generic" % "0.14.5",
      // Optional for string interpolation to JSON model
      "io.circe" %% "circe-literal" % "0.14.5",
      "io.circe" %% "circe-parser" % "0.14.5",
      "com.github.jwt-scala" %% "jwt-circe" % "9.3.0",
      "org.tpolecat" %% "skunk-core" % skunkVersion,
      "co.fs2" %% "fs2-core" % "3.7.0",
      "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0"
    ),
    scalacOptions += "-Ymacro-annotations"
  )
