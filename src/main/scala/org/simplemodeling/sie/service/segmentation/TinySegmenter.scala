package org.simplemodeling.sie.service.segmentation

/**
 * Pure-Scala TinySegmenter
 * 
 * Japanese tokenizer used for lightweight NLP tasks.
 * No external dependencies.
 *
 * @since   Dec.  6, 2025
 * @version Dec.  6, 2025
 * @author  ASAMI, Tomoharu
 */
final case class TinySegmenter():

  private val patterns = TinySegmenterPatterns.patterns

  def segment(text: String): List[String] =
    if (text == null || text.isEmpty) return Nil

    val result = scala.collection.mutable.ListBuffer[String]()
    val chars  = text.toCharArray.map(_.toString)

    var seg    = new StringBuilder
    var i      = 0

    while i < chars.length do
      seg.append(chars(i))

      // Score segmentation boundary
      val score = computeScore(chars, i)

      if score > 0 then
        result += seg.toString
        seg = new StringBuilder

      i += 1

    if seg.nonEmpty then result += seg.toString
    result.toList

  private def computeScore(chars: Array[String], index: Int): Int =
    var score = 0
    val tests = patterns

    for (p <- tests)
      if p.matches(chars, index) then score += p.weight

    score


/**
 * Internal rule definitions.
 * (Converted from original TinySegmenter JS)
 */
object TinySegmenterPatterns:

  // Rule for segmentation scoring
  final case class Rule(
    left:  String,
    right: String,
    weight: Int
  ):
    def matches(chars: Array[String], i: Int): Boolean =
      val l = if i > 0 then chars(i - 1) else ""
      val r = chars(i)

      (left.isEmpty || left == l) &&
      (right.isEmpty || right == r)

  val patterns: List[Rule] = List(
    Rule("、", "", 1),
    Rule("。", "", 1),
    Rule("」", "", 1),
    Rule("」", "」", 1),
    Rule("」", "。", 1),
    Rule("」", "、", 1),
    Rule("）", "", 1),
    Rule("）", "）", 1),
    Rule("）", "。", 1),
    Rule("）", "、", 1),
    Rule("」", "）", 1),
    Rule("）", "」", 1),
    // Minimal rule set — more can be added later
  )
