import sbtbuildinfo.BuildInfoPlugin.autoImport._

ThisBuild / scalaVersion := "3.4.2"

enablePlugins(BuildInfoPlugin)

lazy val root = (project in file("."))
  .settings(
    name := "semantic-integration-engine",

    version := "0.0.3.7",

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
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % "3.9.7",

      // config
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.5",

      // HTML
      "org.jsoup" % "jsoup" % "1.18.1"
    ),

    // BuildInfo (dynamic version/name for MCP)
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey.map(homepage){ case (_, url) => "homepage" -> url.toString },
      BuildInfoKey.map(organizationName){ case (_, org) => "author" -> org }
    ),
    buildInfoPackage := "org.simplemodeling.sie",

    // assembly settings
    Compile / mainClass := Some("org.simplemodeling.sie.server.RagServerMain"),
    assembly / assemblyJarName := "semantic-integration-engine.jar",

    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") =>
        MergeStrategy.first
      case PathList("META-INF", xs @ _*) =>
        MergeStrategy.discard
      case PathList("META-INF", "services", xs @ _*) =>
        MergeStrategy.concat
      case "reference.conf" =>
        MergeStrategy.concat
      case x =>
        MergeStrategy.first
    },

    // Enable JVM remote debugging (attach from IntelliJ/Metals)
    run / fork := true,
    run / javaOptions ++= Seq(
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    ),
  )

addCommandAlias("deploy", ";clean;assembly;copyJar")
addCommandAlias("dev",  "set run / javaOptions += \"-Dconfig.file=src/main/resources/application.dev.conf\"; run")
addCommandAlias("devrs","set javaOptions += \"-Dconfig.file=src/main/resources/application.dev.conf\"; reStart")
addCommandAlias("rsk", "reStop")

lazy val copyJar = taskKey[Unit]("Copy fat jar to dist/")

copyJar := {
  val jar = (Compile / assembly).value
  val dist = baseDirectory.value / "dist"
  IO.createDirectory(dist)
  IO.copyFile(jar, dist / jar.getName)
  streams.value.log.info(s"Copied ${jar.getName} to dist/")
}

lazy val dockerBuild = taskKey[Unit]("Build Docker image for SIE")
lazy val dockerPush  = taskKey[Unit]("Push Docker image to GHCR")
lazy val dockerDeploy = taskKey[Unit]("Assembly → Docker build → GHCR push")

dockerBuild := {
  val log = streams.value.log
  log.info("Preparing jar and building Docker image...")

  // 1) Build fat jar
  val jar = (Compile / assembly).value

  // 2) Copy into dist with a stable name
  val distDir = baseDirectory.value / "dist"
  IO.createDirectory(distDir)
  val distJar = distDir / "semantic-integration-engine.jar"
  IO.copyFile(jar, distJar, sbt.io.CopyOptions(overwrite = true, preserveLastModified = true, preserveExecutable = true))

  // 3) Docker build using the Dockerfile (reads dist/)
  val latest = "ghcr.io/asami/sie:latest"
  val verTag = s"ghcr.io/asami/sie:${version.value}"

  val cmdBuild = s"docker build --no-cache -t $latest -t $verTag ."
  if (sys.process.Process(cmdBuild).! != 0)
    sys.error("Docker multi-tag build failed")

  log.info(s"Docker images built: $latest and $verTag")
}

dockerPush := {
  val log = streams.value.log
  val repo = "ghcr.io/asami/sie"

  log.info(s"Pushing all tags for $repo ...")

  val cmdPush = s"docker push $repo --all-tags"
  if (sys.process.Process(cmdPush).! != 0)
    sys.error("Push failed")

  log.info("Push completed.")
}

dockerDeploy := {
  val log = streams.value.log
  log.info("dockerDeploy: dockerBuild → dockerPush")

  // 1) Build image (includes assembly + dist copy)
  dockerBuild.value

  // 2) Push image
  dockerPush.value

  log.info("dockerDeploy completed.")
}
