package com.sobdosuddhi.app.domain

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.sobdosuddhi.app.data.ai.GeminiApiClient
import com.sobdosuddhi.app.data.local.ManuscriptEntity
import com.sobdosuddhi.app.model.ManuscriptCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedDocument(
  val title: String,
  val content: String,
  val format: String,
  val usedOcr: Boolean = false,
  val statusNote: String = ""
)

enum class ExportFormat(val extension: String, val mimeType: String, val bengaliLabel: String) {
  DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "DOCX (ওয়ার্ড ডকুমেন্ট)"),
  PDF("pdf", "application/pdf", "PDF (প্রিন্ট ও শেয়ারযোগ্য)"),
  TXT("txt", "text/plain", "TXT (সাধারণ ইউনিকোড ফাইল)")
}

/**
 * Native Android Document Import, Export & Sharing Manager.
 * Supports DOCX (OpenXML ZIP), PDF (Android PdfDocument + PdfRenderer/OCR), TXT, and DOC fallback.
 * Preserves Bengali Unicode, paragraph spacing, headings, and poetic line breaks.
 */
class DocumentImportExportManager(
  private val context: Context,
  private val geminiClient: GeminiApiClient
) {

  suspend fun importFromUri(
    uri: Uri,
    onStatusUpdate: (String) -> Unit = {}
  ): Result<ImportedDocument> = withContext(Dispatchers.IO) {
    runCatching {
      val displayName = resolveDisplayName(uri)
      val extension = displayName.substringAfterLast('.', "").lowercase()
      val baseTitle = displayName.substringBeforeLast('.').ifBlank { "আমদানিকৃত পান্ডুলিপি" }
      val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()

      when {
        extension == "docx" || mime.contains("wordprocessingml") -> {
          onStatusUpdate("DOCX ফাইল থেকে বাংলা পাঠ্য ও কাঠামো পড়া হচ্ছে…")
          val text = context.contentResolver.openInputStream(uri)?.use { readDocxStream(it) }
            ?: throw IllegalStateException("DOCX ফাইল খোলা সম্ভব হয়নি।")
          if (text.isBlank()) throw IllegalStateException("DOCX ফাইলে কোনো পাঠ্য পাওয়া যায়নি।")
          ImportedDocument(
            title = baseTitle,
            content = text,
            format = "DOCX",
            statusNote = "DOCX কাঠামো ও অনুচ্ছেদ সফলভাবে সংরক্ষিত হয়েছে।"
          )
        }

        extension == "pdf" || mime == "application/pdf" -> {
          onStatusUpdate("PDF ডকুমেন্ট বিশ্লেষণ করা হচ্ছে…")
          importPdf(uri, baseTitle, onStatusUpdate)
        }

        extension == "doc" || mime == "application/msword" -> {
          onStatusUpdate("পুরোনো DOC ফাইল থেকে পাঠ্য উদ্ধারের চেষ্টা চলছে…")
          val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("DOC ফাইল পড়া যায়নি।")
          val extracted = extractTextFromLegacyDoc(rawBytes)
          if (extracted.isBlank()) {
            throw IllegalStateException(
              "এই পুরোনো বাইনারি DOC ফাইলটি সরাসরি পড়া সম্ভব হয়নি। অনুগ্রহ করে ফাইলটি DOCX বা PDF হিসেবে সংরক্ষণ করে আমদানি করুন।"
            )
          }
          ImportedDocument(
            title = baseTitle,
            content = extracted,
            format = "DOC",
            statusNote = "পুরোনো DOC ফাইল থেকে বাংলা পাঠ্য উদ্ধার করা হয়েছে; দয়া করে বিন্যাস যাচাই করুন।"
          )
        }

        else -> {
          onStatusUpdate("TXT ইউনিকোড ফাইল পড়া হচ্ছে…")
          val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IllegalStateException("ফাইলটি পড়া সম্ভব হয়নি।")
          if (text.isBlank()) throw IllegalStateException("ফাইলে কোনো পাঠ্য পাওয়া যায়নি।")
          ImportedDocument(
            title = baseTitle,
            content = text.replace("\r\n", "\n"),
            format = "TXT",
            statusNote = "ইউনিকোড TXT ফাইল সফলভাবে আমদানি হয়েছে।"
          )
        }
      }
    }
  }

  private suspend fun importPdf(
    uri: Uri,
    baseTitle: String,
    onStatusUpdate: (String) -> Unit
  ): ImportedDocument {
    // 1. Copy PDF to temporary private cache for PdfRenderer access
    val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.pdf")
    try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
      } ?: throw IllegalStateException("PDF ফাইল পড়া যায়নি।")

      // 2. Render pages via Android native PdfRenderer and run Gemini Bengali OCR when required
      val pfd = android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
      pfd.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
          val pageCount = renderer.pageCount
          if (pageCount == 0) throw IllegalStateException("PDF ফাইলে কোনো পৃষ্ঠা নেই।")

          val maxPagesToOcr = minOf(pageCount, 12)
          val extractedPages = mutableListOf<String>()

          for (pageIdx in 0 until maxPagesToOcr) {
            onStatusUpdate(
              "PDF OCR প্রক্রিয়াকরণ চলছে (পৃষ্ঠা ${BengaliUtils.toBengaliDigits(pageIdx + 1)} / ${BengaliUtils.toBengaliDigits(maxPagesToOcr)})…"
            )
            renderer.openPage(pageIdx).use { page ->
              val scale = 2.0f
              val width = (page.width * scale).toInt().coerceIn(800, 1800)
              val height = (page.height * scale).toInt().coerceIn(1000, 2400)
              val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
              bitmap.eraseColor(Color.WHITE)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

              val baos = ByteArrayOutputStream()
              bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
              bitmap.recycle()
              val pngBytes = baos.toByteArray()

              val pageText = try {
                geminiClient.extractBengaliTextFromPageImage(pngBytes)
              } catch (e: com.sobdosuddhi.app.data.ai.AiServiceException) {
                if (e.isServerUnavailable || e.isNetworkError) {
                  throw IllegalStateException(
                    "স্ক্যানকৃত PDF OCR-এর জন্য অনলাইন AI সার্ভার প্রয়োজন (সেটিংস → অনলাইন AI সার্ভার সংযোগ)। DOCX এবং TXT ফাইল সম্পূর্ণ অফলাইনে আমদানি করা যায়।"
                  )
                }
                throw e
              }
              if (pageText.isNotBlank()) {
                extractedPages.add(pageText.trim())
              }
            }
          }

          val fullText = extractedPages.joinToString("\n\n")
          if (fullText.isBlank()) {
            throw IllegalStateException("PDF পৃষ্ঠা থেকে কোনো পাঠ্য উদ্ধার করা যায়নি।")
          }
          val note = if (pageCount > maxPagesToOcr) {
            "OCR ব্যবহার করে প্রথম ${BengaliUtils.toBengaliDigits(maxPagesToOcr)}টি পৃষ্ঠা আমদানি করা হয়েছে (মোট ${BengaliUtils.toBengaliDigits(pageCount)} পৃষ্ঠা)।"
          } else {
            "PDF OCR সফলভাবে সম্পন্ন হয়েছে (${BengaliUtils.toBengaliDigits(pageCount)} পৃষ্ঠা)।"
          }
          return ImportedDocument(
            title = baseTitle,
            content = fullText,
            format = "PDF",
            usedOcr = true,
            statusNote = note
          )
        }
      }
    } finally {
      tempFile.delete()
    }
  }

  /**
   * Parses word/document.xml inside a DOCX archive preserving Bengali Unicode, headings, and paragraph breaks.
   */
  fun readDocxStream(inputStream: InputStream): String {
    ZipInputStream(inputStream).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        if (entry.name == "word/document.xml") {
          val xmlBytes = zis.readBytes()
          return parseWordDocumentXml(xmlBytes)
        }
        entry = zis.nextEntry
      }
    }
    return ""
  }

  private fun parseWordDocumentXml(xmlBytes: ByteArray): String {
    val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
    val parser = factory.newPullParser()
    parser.setInput(xmlBytes.inputStream(), "UTF-8")

    val paragraphs = mutableListOf<String>()
    val currentPara = StringBuilder()
    var inParagraph = false
    var isHeading = false

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "p" -> {
              inParagraph = true
              isHeading = false
              currentPara.clear()
            }
            "pStyle" -> {
              val styleVal = parser.getAttributeValue(null, "val")
                ?: (0 until parser.attributeCount).firstOrNull { parser.getAttributeName(it) == "val" }
                  ?.let { parser.getAttributeValue(it) }
              if (styleVal != null && styleVal.contains("Heading", ignoreCase = true)) {
                isHeading = true
              }
            }
            "t" -> {
              if (inParagraph) {
                currentPara.append(parser.nextText())
              }
            }
            "br" -> {
              if (inParagraph) currentPara.append("\n")
            }
            "tab" -> {
              if (inParagraph) currentPara.append("    ")
            }
          }
        }
        XmlPullParser.END_TAG -> {
          if (parser.name == "p" && inParagraph) {
            val line = currentPara.toString()
            if (isHeading && line.isNotBlank() && !line.startsWith("#")) {
              paragraphs.add("# ${line.trim()}")
            } else {
              paragraphs.add(line)
            }
            inParagraph = false
          }
        }
      }
      eventType = parser.next()
    }
    return paragraphs.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()
  }

  private fun extractTextFromLegacyDoc(bytes: ByteArray): String {
    // Attempt to extract UTF-16LE Bengali Unicode runs (0x0980..0x09FF + punctuation/whitespace)
    val sb = StringBuilder()
    var i = 0
    while (i + 1 < bytes.size) {
      val code = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
      val ch = code.toChar()
      if (code in 0x0980..0x09FF || ch == ' ' || ch == '\n' || ch == '\r' || ch in ",.?!;:\"'()-") {
        sb.append(if (ch == '\r') '\n' else ch)
      }
      i += 2
    }
    val candidate = sb.toString().replace(Regex("[ ]{2,}"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    val bengaliCount = candidate.count { it.code in 0x0980..0x09FF }
    return if (bengaliCount >= 15) candidate else ""
  }

  /**
   * Exports a manuscript to a File in private exports cache for sharing or copying to a SAF URI.
   */
  suspend fun exportToFile(
    manuscript: ManuscriptEntity,
    format: ExportFormat,
    fontSizeSp: Float = 15f
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
      val safeFileName = manuscript.title
        .replace(Regex("[^\\u0980-\\u09FFa-zA-Z0-9_\\- ]"), "")
        .trim()
        .ifBlank { "sobdosuddhi_manuscript" }
        .take(50)
      val outFile = File(exportDir, "${safeFileName}.${format.extension}")

      when (format) {
        ExportFormat.TXT -> {
          outFile.writeText(buildPlainTextExport(manuscript), Charsets.UTF_8)
        }
        ExportFormat.DOCX -> {
          FileOutputStream(outFile).use { writeDocxStream(manuscript, it) }
        }
        ExportFormat.PDF -> {
          FileOutputStream(outFile).use { writePdfStream(manuscript, fontSizeSp, it) }
        }
      }
      outFile
    }
  }

  suspend fun exportToUri(
    manuscript: ManuscriptEntity,
    format: ExportFormat,
    destinationUri: Uri,
    fontSizeSp: Float = 15f
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val tempFile = exportToFile(manuscript, format, fontSizeSp).getOrThrow()
      context.contentResolver.openOutputStream(destinationUri)?.use { out ->
        tempFile.inputStream().use { it.copyTo(out) }
      } ?: throw IllegalStateException("নির্বাচিত স্থানে ফাইল সংরক্ষণ করা যায়নি।")
      Unit
    }
  }

  fun shareExportedFile(file: File, format: ExportFormat) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = format.mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, "পান্ডুলিপি শেয়ার করুন").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }

  private fun buildPlainTextExport(manuscript: ManuscriptEntity): String {
    val sb = StringBuilder()
    sb.appendLine(manuscript.title)
    if (manuscript.authorName.isNotBlank()) {
      sb.appendLine("লেখক: ${manuscript.authorName}")
    }
    sb.appendLine("=".repeat(32))
    sb.appendLine()
    if (manuscript.dedication.isNotBlank()) {
      sb.appendLine("উৎসর্গ:")
      sb.appendLine(manuscript.dedication)
      sb.appendLine()
    }
    if (manuscript.preface.isNotBlank()) {
      sb.appendLine("ভূমিকা:")
      sb.appendLine(manuscript.preface)
      sb.appendLine()
    }
    sb.appendLine(manuscript.content)
    if (manuscript.epilogue.isNotBlank()) {
      sb.appendLine()
      sb.appendLine("শেষাংশ:")
      sb.appendLine(manuscript.epilogue)
    }
    return sb.toString()
  }

  /**
   * Generates a valid OpenXML DOCX package preserving Bengali Unicode, chapter headings,
   * poem line breaks, and paragraph spacing.
   */
  fun writeDocxStream(manuscript: ManuscriptEntity, outputStream: java.io.OutputStream) {
    val isPoetry = ManuscriptCategory.fromName(manuscript.category) == ManuscriptCategory.POETRY
    val contentTypesXml = """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
        <Default Extension="xml" ContentType="application/xml"/>
        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
      </Types>
    """.trimIndent()

    val rootRelsXml = """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
      </Relationships>
    """.trimIndent()

    val docRelsXml = """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
      </Relationships>
    """.trimIndent()

    val stylesXml = """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
          <w:name w:val="Normal"/>
          <w:rPr><w:rFonts w:ascii="Noto Serif Bengali" w:hAnsi="Noto Serif Bengali" w:cs="Noto Serif Bengali"/><w:sz w:val="26"/></w:rPr>
        </w:style>
        <w:style w:type="paragraph" w:styleId="Heading1">
          <w:name w:val="heading 1"/>
          <w:pPr><w:spacing w:before="280" w:after="140"/></w:pPr>
          <w:rPr><w:b/><w:sz w:val="36"/></w:rPr>
        </w:style>
        <w:style w:type="paragraph" w:styleId="Heading2">
          <w:name w:val="heading 2"/>
          <w:pPr><w:spacing w:before="220" w:after="100"/></w:pPr>
          <w:rPr><w:b/><w:sz w:val="30"/></w:rPr>
        </w:style>
      </w:styles>
    """.trimIndent()

    val bodyXml = StringBuilder()
    bodyXml.append(docxParagraph(manuscript.title, "Heading1"))
    if (manuscript.authorName.isNotBlank()) {
      bodyXml.append(docxParagraph("লেখক: ${manuscript.authorName}", "Normal", italic = true))
    }
    if (manuscript.dedication.isNotBlank()) {
      bodyXml.append(docxParagraph("উৎসর্গ", "Heading2"))
      bodyXml.append(docxParagraph(manuscript.dedication, "Normal", italic = true))
    }
    if (manuscript.preface.isNotBlank()) {
      bodyXml.append(docxParagraph("ভূমিকা", "Heading2"))
      bodyXml.append(docxParagraph(manuscript.preface, "Normal"))
    }

    val blocks = manuscript.content.replace("\r\n", "\n").split(Regex("\\n\\s*\\n+"))
    for (block in blocks) {
      val trimmed = block.trim()
      if (trimmed.isEmpty()) continue
      val firstLine = trimmed.lines().first().trim()
      when {
        firstLine.startsWith("##") -> {
          bodyXml.append(docxParagraph(firstLine.removePrefix("##").trim(), "Heading2"))
          val rest = trimmed.lines().drop(1).joinToString("\n").trim()
          if (rest.isNotEmpty()) bodyXml.append(docxParagraphWithLineBreaks(rest, isPoetry))
        }
        firstLine.startsWith("#") || firstLine.startsWith("অধ্যায়") || firstLine.startsWith("পরিচ্ছেদ") -> {
          bodyXml.append(docxParagraph(firstLine.removePrefix("#").trim(), "Heading1"))
          val rest = trimmed.lines().drop(1).joinToString("\n").trim()
          if (rest.isNotEmpty()) bodyXml.append(docxParagraphWithLineBreaks(rest, isPoetry))
        }
        else -> {
          bodyXml.append(docxParagraphWithLineBreaks(trimmed, isPoetry))
        }
      }
    }

    if (manuscript.epilogue.isNotBlank()) {
      bodyXml.append(docxParagraph("শেষাংশ", "Heading2"))
      bodyXml.append(docxParagraph(manuscript.epilogue, "Normal"))
    }

    val documentXml = """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:body>
          $bodyXml
          <w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
        </w:body>
      </w:document>
    """.trimIndent()

    ZipOutputStream(outputStream).use { zos ->
      addZipEntry(zos, "[Content_Types].xml", contentTypesXml)
      addZipEntry(zos, "_rels/.rels", rootRelsXml)
      addZipEntry(zos, "word/_rels/document.xml.rels", docRelsXml)
      addZipEntry(zos, "word/styles.xml", stylesXml)
      addZipEntry(zos, "word/document.xml", documentXml)
    }
  }

  private fun docxParagraph(text: String, styleId: String, italic: Boolean = false): String {
    val italicTag = if (italic) "<w:i/>" else ""
    return "<w:p><w:pPr><w:pStyle w:val=\"$styleId\"/></w:pPr><w:r><w:rPr>$italicTag</w:rPr><w:t xml:space=\"preserve\">${escapeXml(text)}</w:t></w:r></w:p>"
  }

  private fun docxParagraphWithLineBreaks(block: String, isPoetry: Boolean): String {
    val lines = block.lines()
    val runs = StringBuilder()
    lines.forEachIndexed { idx, line ->
      runs.append("<w:r><w:t xml:space=\"preserve\">${escapeXml(line)}</w:t></w:r>")
      if (idx < lines.lastIndex) {
        runs.append("<w:r><w:br/></w:r>")
      }
    }
    val afterSpacing = if (isPoetry) "220" else "180"
    return "<w:p><w:pPr><w:spacing w:after=\"$afterSpacing\" w:line=\"340\" w:lineRule=\"auto\"/></w:pPr>$runs</w:p>"
  }

  private fun addZipEntry(zos: ZipOutputStream, name: String, content: String) {
    zos.putNextEntry(ZipEntry(name))
    zos.write(content.toByteArray(Charsets.UTF_8))
    zos.closeEntry()
  }

  private fun escapeXml(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  /**
   * Native Android PdfDocument renderer supporting full Bengali Unicode conjunct shaping via StaticLayout.
   */
  private fun writePdfStream(
    manuscript: ManuscriptEntity,
    fontSizeSp: Float,
    outputStream: java.io.OutputStream
  ) {
    val pdf = PdfDocument()
    try {
      val pageWidth = 595 // A4 width in pt
      val pageHeight = 842 // A4 height in pt
      val marginX = 52f
      val marginTop = 58f
      val marginBottom = 58f
      val contentWidth = (pageWidth - marginX * 2).toInt()
      val safeTitle = manuscript.title.ifBlank { "শব্দশুদ্ধি পান্ডুলিপি" }

      val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(27, 67, 50)
        textSize = (fontSizeSp + 6f).coerceIn(18f, 26f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
      }
      val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(29, 53, 87)
        textSize = (fontSizeSp + 2f).coerceIn(15f, 22f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
      }
      val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 27, 24)
        textSize = fontSizeSp.coerceIn(11f, 18f)
        typeface = Typeface.SERIF
      }
      val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 100, 96)
        textSize = 10f
        typeface = Typeface.SANS_SERIF
      }

      var pageNumber = 1
      var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
      var canvas = currentPage.canvas
      var cursorY = marginTop

      fun drawFooter() {
        val footerText = "${safeTitle.take(36)}  •  পৃষ্ঠা ${BengaliUtils.toBengaliDigits(pageNumber)}"
        canvas.drawText(footerText, marginX, pageHeight - 26f, metaPaint)
      }

      fun startNewPage() {
        drawFooter()
        pdf.finishPage(currentPage)
        pageNumber++
        currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = currentPage.canvas
        cursorY = marginTop
      }

      fun drawBlock(text: String, paint: TextPaint, spacingAfter: Float) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
          .setAlignment(Layout.Alignment.ALIGN_NORMAL)
          .setLineSpacing(4f, 1.25f)
          .build()

        if (cursorY + layout.height > pageHeight - marginBottom && cursorY > marginTop + 20f) {
          startNewPage()
        }

        // If single block taller than 1 page, draw line-by-line
        if (layout.height > pageHeight - marginTop - marginBottom) {
          for (lineIdx in 0 until layout.lineCount) {
            val lineStart = layout.getLineStart(lineIdx)
            val lineEnd = layout.getLineEnd(lineIdx)
            val lineText = text.substring(lineStart, lineEnd).trimEnd('\n')
            val lineH = (layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)).toFloat()
            if (cursorY + lineH > pageHeight - marginBottom) {
              startNewPage()
            }
            canvas.drawText(lineText, marginX, cursorY - paint.ascent(), paint)
            cursorY += lineH
          }
          cursorY += spacingAfter
        } else {
          canvas.save()
          canvas.translate(marginX, cursorY)
          layout.draw(canvas)
          canvas.restore()
          cursorY += layout.height + spacingAfter
        }
      }

      drawBlock(safeTitle, titlePaint, 8f)
      if (manuscript.authorName.isNotBlank()) {
        drawBlock("লেখক: ${manuscript.authorName}", metaPaint, 18f)
      } else {
        cursorY += 10f
      }

      if (manuscript.dedication.isNotBlank()) {
        drawBlock("উৎসর্গ", headingPaint, 4f)
        drawBlock(manuscript.dedication, bodyPaint, 16f)
      }
      if (manuscript.preface.isNotBlank()) {
        drawBlock("ভূমিকা", headingPaint, 4f)
        drawBlock(manuscript.preface, bodyPaint, 16f)
      }

      val blocks = manuscript.content.replace("\r\n", "\n").split(Regex("\\n\\s*\\n+"))
      for (block in blocks) {
        val trimmed = block.trim()
        if (trimmed.isEmpty()) continue
        val firstLine = trimmed.lines().first().trim()
        if (firstLine.startsWith("#") || firstLine.startsWith("অধ্যায়") || firstLine.startsWith("পরিচ্ছেদ")) {
          drawBlock(firstLine.replace(Regex("^#+\\s*"), ""), headingPaint, 8f)
          val rest = trimmed.lines().drop(1).joinToString("\n").trim()
          if (rest.isNotEmpty()) drawBlock(rest, bodyPaint, 14f)
        } else {
          drawBlock(trimmed, bodyPaint, 14f)
        }
      }

      if (manuscript.epilogue.isNotBlank()) {
        drawBlock("শেষাংশ", headingPaint, 6f)
        drawBlock(manuscript.epilogue, bodyPaint, 12f)
      }

      drawFooter()
      pdf.finishPage(currentPage)
      pdf.writeTo(outputStream)
    } finally {
      pdf.close()
    }
  }

  private fun resolveDisplayName(uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) return cursor.getString(idx) ?: "manuscript.txt"
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "manuscript.txt"
  }
}
