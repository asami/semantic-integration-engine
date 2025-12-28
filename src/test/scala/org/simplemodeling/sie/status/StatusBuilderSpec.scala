package org.simplemodeling.sie.status

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Dec. 23, 2025
 * @author  ASAMI, Tomoharu
 */
class StatusBuilderSpec extends AnyWordSpec with Matchers {

  "StatusBuilder.build" should {

    "report healthy when all subsystems are ready" in { pending }

    "report unavailable when graph is not ready" in { pending }
  }
}
