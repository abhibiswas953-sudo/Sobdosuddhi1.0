package com.sobdosuddhi.app.domain

import com.sobdosuddhi.app.model.ManuscriptCategory

data class ManuscriptChunk(
  val index: Int,
  val text: String,
  val startParagraphIndex: Int,
  val paragraphCount: Int,
  val precedingContext: String
)

/**
 * Smart Manuscript Chunker:
 * - Preserves poetic stanzas and line breaks intact (never splits mid-line).
 * - Preserves prose paragraphs & chapter boundaries intact.
 * - Safely splits oversized single-block poetry on line boundaries and oversized prose on sentence/word boundaries
 *   so 100,000+ character manuscripts never exceed memory or API chunk limits.
 * - Keeps zero text loss across chunking.
 */
object ManuscriptChunker {

  fun chunk(
    text: String,
    category: ManuscriptCategory,
    maxCharsPerChunk: Int = if (category == ManuscriptCategory.POETRY) 1600 else 2200
  ): List<ManuscriptChunk> {
    val normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim()
    if (normalized.isEmpty()) return emptyList()

    if (normalized.length <= maxCharsPerChunk) {
      val paraCount = normalized.split(Regex("\\n\\s*\\n+")).count { it.isNotBlank() }
      return listOf(
        ManuscriptChunk(
          index = 0,
          text = normalized,
          startParagraphIndex = 0,
          paragraphCount = paraCount.coerceAtLeast(1),
          precedingContext = ""
        )
      )
    }

    val blocks = normalized.split(Regex("\\n\\s*\\n+")).filter { it.isNotBlank() }
    val chunks = mutableListOf<ManuscriptChunk>()
    val currentBlocks = mutableListOf<String>()
    var currentLen = 0
    var chunkStartPara = 0
    var previousTail = ""

    fun flushChunk() {
      if (currentBlocks.isEmpty()) return
      val joined = currentBlocks.joinToString("\n\n")
      chunks.add(
        ManuscriptChunk(
          index = chunks.size,
          text = joined,
          startParagraphIndex = chunkStartPara,
          paragraphCount = currentBlocks.size,
          precedingContext = previousTail
        )
      )
      previousTail = currentBlocks.last().takeLast(180)
      chunkStartPara += currentBlocks.size
      currentBlocks.clear()
      currentLen = 0
    }

    val oversizedThreshold = (maxCharsPerChunk * 1.5).toInt()

    for (block in blocks) {
      if (block.length > oversizedThreshold) {
        flushChunk()
        val parts = if (category == ManuscriptCategory.POETRY) {
          splitLongPoetryBlockByLines(block, maxCharsPerChunk)
        } else {
          splitLongProseParagraph(block, maxCharsPerChunk)
        }
        for (part in parts) {
          chunks.add(
            ManuscriptChunk(
              index = chunks.size,
              text = part,
              startParagraphIndex = chunkStartPara,
              paragraphCount = 1,
              precedingContext = previousTail
            )
          )
          previousTail = part.takeLast(180)
        }
        chunkStartPara += 1
        continue
      }

      if (currentBlocks.isNotEmpty() && currentLen + block.length + 2 > maxCharsPerChunk) {
        flushChunk()
      }
      currentBlocks.add(block)
      currentLen += block.length + 2
    }
    flushChunk()

    return chunks
  }

  /**
   * Splits an oversized continuous poetry block strictly on newline (\n) boundaries
   * so individual poetic lines, meter, and end-rhymes are never broken mid-line.
   */
  private fun splitLongPoetryBlockByLines(block: String, maxChars: Int): List<String> {
    val lines = block.split("\n")
    val parts = mutableListOf<String>()
    val sb = StringBuilder()
    for (line in lines) {
      if (sb.isNotEmpty() && sb.length + line.length + 1 > maxChars) {
        parts.add(sb.toString())
        sb.clear()
      }
      if (sb.isNotEmpty()) sb.append('\n')
      sb.append(line)
    }
    if (sb.isNotEmpty()) {
      parts.add(sb.toString())
    }
    return if (parts.isEmpty()) listOf(block) else parts
  }

  private fun splitLongProseParagraph(paragraph: String, maxChars: Int): List<String> {
    val sentences = paragraph.split(Regex("(?<=[।?!])\\s+"))
    val parts = mutableListOf<String>()
    val sb = StringBuilder()
    for (sentence in sentences) {
      if (sentence.length > maxChars) {
        if (sb.isNotEmpty()) {
          parts.add(sb.toString().trim())
          sb.clear()
        }
        parts.addAll(splitByWords(sentence, maxChars))
        continue
      }
      if (sb.isNotEmpty() && sb.length + sentence.length + 1 > maxChars) {
        parts.add(sb.toString().trim())
        sb.clear()
      }
      if (sb.isNotEmpty()) sb.append(' ')
      sb.append(sentence)
    }
    if (sb.isNotEmpty()) {
      parts.add(sb.toString().trim())
    }
    return if (parts.isEmpty()) listOf(paragraph) else parts
  }

  private fun splitByWords(segment: String, maxChars: Int): List<String> {
    val words = segment.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val parts = mutableListOf<String>()
    val sb = StringBuilder()
    for (word in words) {
      if (sb.isNotEmpty() && sb.length + word.length + 1 > maxChars) {
        parts.add(sb.toString().trim())
        sb.clear()
      }
      if (sb.isNotEmpty()) sb.append(' ')
      sb.append(word)
    }
    if (sb.isNotEmpty()) {
      parts.add(sb.toString().trim())
    }
    return if (parts.isEmpty()) listOf(segment) else parts
  }
}
