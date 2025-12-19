package org.simplemodeling.sie.mcp

/*
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec. 19, 2025
 * @author  ASAMI, Tomoharu
 */
object McpClientMain:

  def main(args: Array[String]): Unit =
    val mcp = new McpClient()
    mcp.start()
