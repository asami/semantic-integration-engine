ThisBuild / scalaVersion := "3.4.2"

lazy val root = (project in file("."))
  .settings(
    name := "semantic-integration-engine",

    version := "0.0.2-SNAPSHOT",

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

      // config
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.5"
    ),

    // assembly settings
    Compile / mainClass := Some("org.simplemodeling.sie.server.RagServerMain"),
    assembly / assemblyJarName := "semantic-integration-engine.jar"
  )

// sbt-assembly plugin (必要)
addCommandAlias("deploy", ";clean;assembly;copyJar")

lazy val copyJar = taskKey[Unit]("Copy fat jar to dist/")

copyJar := {
  val jar = (Compile / assembly).value
  val dist = baseDirectory.value / "dist"
  IO.createDirectory(dist)
  IO.copyFile(jar, dist / jar.getName)
  streams.value.log.info(s"Copied ${jar.getName} to dist/")
}
