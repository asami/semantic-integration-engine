package org.simplemodeling.rag.config

import pureconfig.*
import pureconfig.generic.derivation.default.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 20, 2025
 * @author  ASAMI, Tomoharu
 */
case class FusekiConfig(endpoint: String) derives ConfigReader
case class ChromaConfig(endpoint: String) derives ConfigReader
case class ServerConfig(host: String, port: Int) derives ConfigReader

case class AppConfig(
  fuseki: FusekiConfig,
  chroma: ChromaConfig,
  server: ServerConfig
) derives ConfigReader

object AppConfig:
  def load(): AppConfig =
    ConfigSource.default.loadOrThrow[AppConfig]
