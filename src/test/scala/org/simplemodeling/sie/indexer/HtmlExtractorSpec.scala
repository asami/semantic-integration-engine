package org.simplemodeling.sie.indexer

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Dec. 23, 2025
 * @author  ASAMI, Tomoharu
 */
class HtmlExtractorSpec extends AnyWordSpec with Matchers {

  "HtmlExtractor.extractMainText" should {

    "prefer article content when present" in { pending }

    "fallback to body text when no main element exists" in { pending }
  }
}
