package com.sobdosuddhi.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sobdosuddhi.app.data.ai.GeminiApiClient
import com.sobdosuddhi.app.data.ai.SobdosuddhiAiEngine
import com.sobdosuddhi.app.data.local.ManuscriptEntity
import com.sobdosuddhi.app.data.local.SobdosuddhiDatabase
import com.sobdosuddhi.app.domain.BengaliLocalAnalyzer
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.domain.DocumentImportExportManager
import com.sobdosuddhi.app.domain.ManuscriptChunker
import com.sobdosuddhi.app.model.AiDetectionLabel
import com.sobdosuddhi.app.model.DiffOp
import com.sobdosuddhi.app.model.ManuscriptCategory
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SobdosuddhiEngineTests {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `main activity launches past splash screen and navigates all 5 Bengali tabs`() {
    composeRule.mainClock.advanceTimeBy(1500L)
    composeRule.waitForIdle()
    composeRule.onNodeWithText("নতুন লেখা").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("আমার পান্ডুলিপি").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("ইতিহাস").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("সেটিংস").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("হোম").performClick()
    composeRule.waitForIdle()
  }

  @Test
  fun `app name string is Bengali Sobdosuddhi`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("শব্দশুদ্ধি ১.০", context.getString(R.string.app_name))
  }

  @Test
  fun `bengali numerals and text metrics calculate accurately`() {
    assertEquals("১২৩৪৫৬৭৮৯০", BengaliUtils.toBengaliDigits("1234567890"))
    val sample = "অধ্যায় ১: সূচনা\n\nবৃষ্টি পড়ছে শহরের রাস্তায়।\n\nনতুন সকাল এল।"
    val metrics = BengaliUtils.calculateMetrics(sample)
    assertEquals(3, metrics.paragraphs)
    assertEquals(1, metrics.chapters)
    assertTrue(metrics.words >= 9)
  }

  @Test
  fun `local proofreading catches Bengali spelling, kar-fola, and ASCII period without breaking poem stanzas`() {
    val poem = "তুমি বলেছিলে ফিরবে শ্রাবন শেষে\nদুরত্ব কখনো মুছে দেয় না টান\nশুধু মাত্র নিরবতা ভাসে."
    val issues = BengaliLocalAnalyzer.runLocalProofreadingChecks(poem, ManuscriptCategory.POETRY)

    assertTrue("Should catch শ্রাবন -> শ্রাবণ", issues.any { it.originalText == "শ্রাবন" && it.suggestedText == "শ্রাবণ" })
    assertTrue("Should catch দুরত্ব -> দূরত্ব", issues.any { it.originalText == "দুরত্ব" && it.suggestedText == "দূরত্ব" })
    assertTrue("Should catch শুধু মাত্র -> শুধুমাত্র", issues.any { it.originalText == "শুধু মাত্র" && it.suggestedText == "শুধুমাত্র" })
    assertTrue("Should catch নিরবতা -> নীরবতা", issues.any { it.originalText == "নিরবতা" && it.suggestedText == "নীরবতা" })
    assertTrue("Should catch ASCII period after ভাসে.", issues.any { it.originalText == "ভাসে." && it.suggestedText == "ভাসে।" })
    assertTrue("Poetry preservation note should be attached", issues.any { it.poetryPreservationNote.isNotBlank() })
  }

  @Test
  fun `local proofreading preserves intentional Bengali poetic reduplication`() {
    val poem = "ধীরে ধীরে নামে সন্ধ্যা\nবার বার ডাকে নদী"
    val issues = BengaliLocalAnalyzer.runLocalProofreadingChecks(poem, ManuscriptCategory.POETRY)
    assertTrue("Should not flag poetic reduplication", issues.isEmpty())
  }

  @Test
  fun `smart manuscript chunker preserves stanzas and loses zero text`() {
    val stanzas = (1..12).map { i ->
      "স্তবক $i\nআকাশের মেঘ ভেসে যায় দূর পাহাড়ের পানে\nবাতাসে বাজে চেনা সুর অচেনা এক গানে।\nসন্ধ্যার আলো নিভে আসে শান্ত নদীর তীরে\nপুরোনো দিনের স্মৃতিগুলো আবার ফিরে ফিরে।"
    }
    val fullPoem = stanzas.joinToString("\n\n")
    val chunks = ManuscriptChunker.chunk(fullPoem, ManuscriptCategory.POETRY, maxCharsPerChunk = 500)

    assertTrue("Long poem should split into multiple chunks", chunks.size > 1)
    val reassembled = chunks.joinToString("\n\n") { it.text }
    assertEquals("Chunking and reassembly must lose zero characters", fullPoem, reassembled)
  }

  @Test
  fun `duplicate paragraph and table of contents detection works deterministically`() {
    val text = """
      অধ্যায় ১: যাত্রার শুরু

      সকালের ট্রেনটি ধীরে ধীরে স্টেশন ছেড়ে বেরিয়ে গেল এবং জানালার বাইরে সবুজ মাঠ চোখে পড়ল।

      অধ্যায় ২: পুরোনো শহর

      সকালের ট্রেনটি ধীরে ধীরে স্টেশন ছেড়ে বেরিয়ে গেল এবং জানালার বাইরে সবুজ মাঠ চোখে পড়ল।
    """.trimIndent()

    val toc = BengaliLocalAnalyzer.extractTableOfContents(text)
    assertEquals(2, toc.size)
    assertEquals("অধ্যায় ১: যাত্রার শুরু", toc[0].title)
    assertEquals("অধ্যায় ২: পুরোনো শহর", toc[1].title)

    val repeated = BengaliLocalAnalyzer.findRepeatedSegments(text)
    assertTrue("Should detect duplicate paragraph", repeated.any { it.type == "পুনরাবৃত্ত অনুচ্ছেদ" && it.occurrences == 2 })
  }

  @Test
  fun `diff engine identifies insertions and deletions for version control`() {
    val v1 = "শরতের সকালে বৃষ্টি নামে।"
    val v2 = "বর্ষার সন্ধ্যায় অঝোরে বৃষ্টি নামে।"
    val diff = BengaliUtils.computeDiff(v1, v2)

    assertTrue(diff.any { it.op == DiffOp.DELETE && it.text.contains("শরতের") })
    assertTrue(diff.any { it.op == DiffOp.INSERT && it.text.contains("বর্ষার") })
    assertTrue(diff.any { it.op == DiffOp.EQUAL && it.text.contains("বৃষ্টি নামে।") })
  }

  @Test
  fun `docx export and import preserves Bengali Unicode and headings round-trip`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DocumentImportExportManager(context, GeminiApiClient())
    val manuscript = ManuscriptEntity(
      title = "শ্রাবণ সন্ধ্যা",
      authorName = "অনির্বাণ",
      category = ManuscriptCategory.POETRY.name,
      content = "# প্রথম পর্ব\n\nমেঘের আড়ালে আলো-ছায়া কাঁদে।\nনীরব শহরে বৃষ্টি নামে।",
      originalContent = "# প্রথম পর্ব\n\nমেঘের আড়ালে আলো-ছায়া কাঁদে।"
    )

    val baos = ByteArrayOutputStream()
    manager.writeDocxStream(manuscript, baos)
    val bytes = baos.toByteArray()
    assertTrue("DOCX output should be non-empty", bytes.size > 200)

    val importedText = manager.readDocxStream(ByteArrayInputStream(bytes))
    assertTrue("Imported DOCX must retain Bengali title", importedText.contains("শ্রাবণ সন্ধ্যা"))
    assertTrue("Imported DOCX must retain Bengali conjuncts", importedText.contains("বৃষ্টি"))
    assertTrue("Imported DOCX must retain Bengali heading", importedText.contains("প্রথম পর্ব"))
  }

  @Test
  fun `ai detection parser enforces uncertainty disclaimer and verified authorship distinction`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SobdosuddhiDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val engine = SobdosuddhiAiEngine(GeminiApiClient(), db.analysisHistoryDao())

      val sampleJson = """
      {
        "classification": "MIXED",
        "detectionIndicatorLevel": "মাঝারি মিশ্র প্রবণতা",
        "aiLikelihoodRange": "মিশ্র লক্ষণ দৃশ্যমান",
        "humanLikelihoodRange": "আংশিক স্বাভাবিক মানবিক প্রবাহ",
        "confidenceLevel": "MEDIUM",
        "explanation": "কয়েকটি বাক্যে অনুমেয় সংযোগশব্দ থাকলেও সামগ্রিক আবেগে মানবিক বৈচিত্র্য আছে।",
        "overallCharacteristics": ["পরিমিত বাক্য গঠন"],
        "linguisticDimensions": [
          {"dimensionName": "বাক্য গঠনের পুনরাবৃত্তি", "observation": "মাঝারি", "tendency": "মিশ্র"}
        ],
        "suspiciousSentences": [
          {"sentence": "সর্বোপরি এটি বলা যায় যে বিষয়টি অত্যন্ত তাৎপর্যপূর্ণ।", "reason": "গতানুগতিক সংযোগশব্দ", "signalStrength": "মাঝারি"}
        ],
        "paragraphAnalysis": []
      }
      """.trimIndent()

      val report = engine.parseAiDetectionJson(sampleJson, "দ্রুত বিশ্লেষণ", 850L)
      assertEquals(AiDetectionLabel.MIXED.name, report.classification)
      assertTrue("Mandatory uncertainty warning must be present", report.uncertaintyWarning.contains("সম্ভাব্যতামূলক"))
      assertTrue("Verified authorship must explicitly state unverified", report.verifiedAuthorshipNote.contains("অযাচাইকৃত"))
      assertFalse("Must never claim 100% certainty", report.uncertaintyWarning.contains("১০০% নিশ্চিত প্রমাণ।"))
    } finally {
      db.close()
    }
  }
}
