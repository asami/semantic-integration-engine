package org.simplemodeling.rag.server

import cats.effect.*
import org.simplemodeling.rag.config.*
import org.simplemodeling.rag.fuseki.*
import org.simplemodeling.rag.chroma.*
import org.simplemodeling.rag.service.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 20, 2025
 * @author  ASAMI, Tomoharu
 */
object RagServerMain extends IOApp.Simple:

  def run: IO[Unit] =
    val cfg = AppConfig.load()

    val fuseki = FusekiClient(cfg.fuseki.endpoint)
    val chroma = ChromaClient(cfg.chroma.endpoint)
    val service = RagService(fuseki, chroma)

    HttpRagServer(service, cfg.server.host, cfg.server.port).start
