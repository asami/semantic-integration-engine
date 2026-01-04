package org.simplemodeling.sie.adapter

import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Request, Response}
import org.goldenport.cncf.service.Service

final class SieCncfAdapter(
  service: Service
) {

  /**
   * Semantic Query
   * HTTP POST でも domain semantics は Query
   */
  def query(
    query: String,
    limit: Option[Int] = None
  ): Consequence[Response] = {
    val request = _build_request_(query, limit)
    service.invokeRequest(request)
  }

  private def _build_request_(
    query: String,
    limit: Option[Int]
  ): Request = {
    val args =
      Argument("query", query, None) +:
        limit.map(v => Argument("limit", v.toString, None)).toList

    Request(
      service = None,
      operation = "query",
      arguments = args,
      switches = Nil,
      properties = Nil
    )
  }
}
