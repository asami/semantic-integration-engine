package org.simplemodeling.rag.mcp

import cats.effect.*
import org.http4s.ember.client.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 20, 2025
 * @author  ASAMI, Tomoharu
 */
object McpClientMain extends IOApp.Simple:

  def run: IO[Unit] =
    val rest = sys.env.getOrElse("RAG_REST_URL", "http://localhost:8080")

    EmberClientBuilder.default[IO].build.use { client =>
      IO.blocking {
        val mcp = new McpClient(rest)(using client)
        mcp.start()
      }
    }
