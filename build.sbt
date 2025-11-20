ThisBuild / scalaVersion := "3.4.2"

lazy val root = (project in file("."))
  .settings(
    name := "rag-system",
    libraryDependencies ++= Seq(
      // http4s Server/Client
      "org.http4s" %% "http4s-ember-server" % "0.23.26",
      "org.http4s" %% "http4s-ember-client" % "0.23.26",
      "org.http4s" %% "http4s-circe"        % "0.23.26",
      "org.http4s" %% "http4s-dsl"          % "0.23.26",

      // JSON
      "io.circe"   %% "circe-core"          % "0.14.7",
      "io.circe"   %% "circe-generic"       % "0.14.7",
      "io.circe"   %% "circe-parser"        % "0.14.7",

      // sttp
      "com.softwaremill.sttp.client3" %% "core"  % "3.9.7",
      "com.softwaremill.sttp.client3" %% "circe" % "3.9.7",

      "com.github.pureconfig" %% "pureconfig-core" % "0.17.6"
    )
  )
