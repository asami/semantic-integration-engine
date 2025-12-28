package org.simplemodeling.sie.server

import io.circe.Json
import org.http4s.Method
import org.http4s.Status
import org.http4s.Uri
import org.simplemodeling.sie.interaction.*
import org.scalatest.Assertions.fail
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Dec. 23, 2025
 * @author  ASAMI, Tomoharu
 */
class RestProtocolSpec extends AnyWordSpec with Matchers {

  "RestIngress" should {

    "decode query requests" in { pending }
  }

  "RestAdapter" should {

    "encode successful query results" in { pending }
  }
}
