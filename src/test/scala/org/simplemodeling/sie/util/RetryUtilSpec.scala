package org.simplemodeling.sie.util

import cats.effect.IO
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Dec. 23, 2025
 * @author  ASAMI, Tomoharu
 */
class RetryUtilSpec extends AnyWordSpec with Matchers {

  "RetryUtil.retryIO" should {

    "return the original value on the first attempt" in { pending }

    "retry until a later attempt succeeds" in { pending }
  }
}
