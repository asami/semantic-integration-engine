package org.simplemodeling.sie.interaction

import io.circe.Json
import org.scalatest.Assertions.fail
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Dec. 23, 2025
 * @author  ASAMI, Tomoharu
 */
class ProtocolHandlerSpec extends AnyWordSpec with Matchers {

  "ProtocolHandler.ChatGpt.ChatGptIngress" should {

    "decode tools/list tool calls" in { pending }

    "decode query tool calls with limit" in { pending }

    "reject query tool calls without a query" in { pending }
  }

  "ProtocolHandler.Mcp.McpJsonRpcIngress" should {

    "decode initialize requests" in { pending }
  }
}
