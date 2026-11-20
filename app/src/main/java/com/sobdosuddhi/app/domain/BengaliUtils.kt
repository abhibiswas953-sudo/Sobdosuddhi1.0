package com.sobdosuddhi.app.domain

import com.sobdosuddhi.app.model.DiffOp
import com.sobdosuddhi.app.model.DiffSegment
import com.sobdosuddhi.app.model.TextMetrics
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object BengaliUtils {
  private val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

  fun toBengaliDigits(input: Number): String = toBengaliDigits(input.toString())

  fun toBengaliDigits(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
      if (ch in '0'..'9') {
        sb.append(bengaliDigits[ch - '0'])
      } else {
        sb.append(ch)
      }
    }
    return sb.toString()
  }

  fun greetingForCurrentTime(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
      in 5..11 -> "শুভ সকাল"
      in 12..16 -> "শুভ অপরাহ্ণ"
      in 17..20 -> "শুভ সন্ধ্যা"
      else -> "শুভ রাত্রি"
    }
  }

  fun formatBengaliDate(timestamp: Long): String {
    val months = arrayOf(
      "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
      "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val day = toBengaliDigits(cal.get(Calendar.DAY_OF_MONTH))
    val month = months[cal.get(Calendar.MONTH)]
    val year = toBengaliDigits(cal.get(Calendar.YEAR))
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val time = toBengaliDigits(timeFormat.format(Date(timestamp)))
    return "$day $month $year, $time"
  }

  fun formatDuration(durationMs: Long): String {
    if (durationMs < 1000L) return "${toBengaliDigits(durationMs)} মি.সে."
    val seconds = durationMs / 1000.0
    val formatted = String.format(Locale.US, "%.1f", seconds)
    return "${toBengaliDigits(formatted)} সেকেন্ড"
  }

  fun sha256(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun calculateMetrics(text: String): TextMetrics {
    if (text.isBlank()) {
      return TextMetrics(0, 0, 0, 0, 0, 0)
    }
    val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    val characters = text.length
    val charactersNoSpaces = text.count { !it.isWhitespace() }
    val paragraphs = text.split(Regex("\\n\\s*\\n+")).count { it.isNotBlank() }
    val lines = text.lines().count { it.isNotBlank() }
    val chapters = BengaliLocalAnalyzer.extractTableOfContents(text).count { it.level == 1 }
    return TextMetrics(
      words = words,
      characters = characters,
      charactersNoSpaces = charactersNoSpaces,
      paragraphs = paragraphs,
      lines = lines,
      chapters = chapters
    )
  }

  /**
   * Computes a word-preserving diff between original and revised Bengali text using LCS.
   * Falls back to line-level diff for very large manuscripts to guarantee O(1) responsiveness.
   */
  fun computeDiff(original: String, revised: String): List<DiffSegment> {
    if (original == revised) {
      return if (original.isEmpty()) emptyList() else listOf(DiffSegment(DiffOp.EQUAL, original))
    }

    val tokenRegex = Regex("\\S+|\\s+")
    val origTokens = tokenRegex.findAll(original).map { it.value }.toList()
    val revTokens = tokenRegex.findAll(revised).map { it.value }.toList()

    // For very long texts, run line-level LCS using Long arithmetic to prevent 32-bit Int overflow & OOM
    if (origTokens.size.toLong() * revTokens.size.toLong() > 350_000L) {
      return computeLineDiff(original, revised)
    }

    return runLcsDiff(origTokens, revTokens)
  }

  private fun computeLineDiff(original: String, revised: String): List<DiffSegment> {
    val origLines = original.split("\n").map { "$it\n" }
    val revLines = revised.split("\n").map { "$it\n" }
    if (origLines.size.toLong() * revLines.size.toLong() > 250_000L) {
      return listOf(
        DiffSegment(DiffOp.DELETE, original),
        DiffSegment(DiffOp.INSERT, revised)
      )
    }
    return runLcsDiff(origLines, revLines)
  }

  private fun runLcsDiff(a: List<String>, b: List<String>): List<DiffSegment> {
    val m = a.size
    val n = b.size
    val dp = Array(m + 1) { IntArray(n + 1) }

    for (i in m - 1 downTo 0) {
      for (j in n - 1 downTo 0) {
        dp[i][j] = if (a[i] == b[j]) {
          dp[i + 1][j + 1] + 1
        } else {
          maxOf(dp[i + 1][j], dp[i][j + 1])
        }
      }
    }

    val raw = mutableListOf<DiffSegment>()
    var i = 0
    var j = 0
    while (i < m && j < n) {
      when {
        a[i] == b[j] -> {
          raw.add(DiffSegment(DiffOp.EQUAL, a[i]))
          i++
          j++
        }
        dp[i + 1][j] >= dp[i][j + 1] -> {
          raw.add(DiffSegment(DiffOp.DELETE, a[i]))
          i++
        }
        else -> {
          raw.add(DiffSegment(DiffOp.INSERT, b[j]))
          j++
        }
      }
    }
    while (i < m) {
      raw.add(DiffSegment(DiffOp.DELETE, a[i++]))
    }
    while (j < n) {
      raw.add(DiffSegment(DiffOp.INSERT, b[j++]))
    }

    // Merge adjacent segments of same operation
    val merged = mutableListOf<DiffSegment>()
    for (seg in raw) {
      val last = merged.lastOrNull()
      if (last != null && last.op == seg.op) {
        merged[merged.lastIndex] = DiffSegment(last.op, last.text + seg.text)
      } else {
        merged.add(seg)
      }
    }
    return merged
  }
}
