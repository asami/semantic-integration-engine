ThisBuild / scalaVersion := "3.4.2"

lazy val root = (project in file("."))
  .settings(
    name := "semantic-integration-engine",

    version := "0.0.1"

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

      // config loader
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.7"
    ),

    // Assembly settings
    assembly / mainClass := Some("org.simplemodeling.sie.server.RagServerMain"),
    assembly / assemblyJarName := "semantic-integration-engine.jar"
  )

lazy val deploy = taskKey[Unit]("Build assembly and copy to dist/")

deploy := {
  val log = streams.value.log

  // Run assembly
  val jar = (assembly in Compile).value

  val distDir = baseDirectory.value / "dist"
  IO.createDirectory(distDir)

  val targetJar = distDir / "semantic-integration-engine.jar"

  log.info(s"Copying ${jar.getName} to ${targetJar.getPath}")
  IO.copyFile(jar, targetJar)

  log.success("Deploy completed.")
}
