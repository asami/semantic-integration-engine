package org.simplemodeling.sie.server

import cats.effect.*
import org.simplemodeling.sie.config.*
import org.simplemodeling.sie.fuseki.*
import org.simplemodeling.sie.chroma.*
import org.simplemodeling.sie.service.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 25, 2025
 * @author  ASAMI, Tomoharu
 */
object RagServerMain extends IOApp.Simple:

  def run: IO[Unit] =
    val cfg = AppConfig.load()

    val fuseki = FusekiClient(cfg.fuseki.endpoint)
    val chroma = ChromaClient(cfg.chroma.endpoint)
    val service = RagService(fuseki, chroma)

    HttpRagServer(service, cfg.server.host, cfg.server.siePort).start
