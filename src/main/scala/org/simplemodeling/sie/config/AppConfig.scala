package org.simplemodeling.sie.config

import pureconfig.*
import pureconfig.error.*
import pureconfig.generic.derivation.default.*

/*
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec.  6, 2025
 * @author  ASAMI, Tomoharu
 */
enum ServerMode:
  case Dev, Prod

given ConfigReader[ServerMode] =
  ConfigReader[String].map {
    case s if s.equalsIgnoreCase("dev")  => ServerMode.Dev
    case s if s.equalsIgnoreCase("prod") => ServerMode.Prod
    case other => throw new Exception(s"Invalid server mode: $other")
  }

case class FusekiConfig(endpoint: String) derives ConfigReader
case class ChromaConfig(endpoint: String) derives ConfigReader
case class ServerConfig(host: String, siePort: Int, mode: ServerMode) derives ConfigReader

case class AppConfig(
  fuseki: FusekiConfig,
  chroma: ChromaConfig,
  server: ServerConfig
) derives ConfigReader

object AppConfig:
  def load(): AppConfig =
    ConfigSource.default.loadOrThrow[AppConfig]
