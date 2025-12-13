package org.simplemodeling.sie.build

/**
 * Minimal BuildInfo replacement for runtime metadata.
 *
 * Prefer values supplied via environment variables so container
 * builds can inject accurate information without sbt plugins.
 */
object BuildInfo {
  val version: String =
    sys.env.getOrElse("SIE_VERSION", "dev")

  val gitCommit: String =
    sys.env.getOrElse("SIE_GIT_COMMIT", "unknown")
}
