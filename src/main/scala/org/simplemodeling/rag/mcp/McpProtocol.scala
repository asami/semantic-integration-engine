package org.simplemodeling.rag.mcp

import io.circe.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 20, 2025
 * @author  ASAMI, Tomoharu
 */
case class McpRequest(
  jsonrpc: String,
  id: Option[String],
  method: String,
  params: Option[Json]
) derives Decoder

case class McpResponse(
  jsonrpc: String = "2.0",
  id: Option[String] = None,
  result: Option[Json] = None,
  error: Option[McpError] = None
) derives Encoder

case class McpError(code: Int, message: String) derives Encoder

