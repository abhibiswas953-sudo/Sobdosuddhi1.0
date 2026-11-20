package com.sobdosuddhi.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sobdosuddhi.app.data.ai.GeminiApiClient
import com.sobdosuddhi.app.data.ai.ServerHealthStatus
import com.sobdosuddhi.app.data.ai.SobdosuddhiAiEngine
import com.sobdosuddhi.app.data.local.ManuscriptEntity
import com.sobdosuddhi.app.data.local.SobdosuddhiDatabase
import com.sobdosuddhi.app.domain.BengaliLocalAnalyzer
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.domain.ManuscriptChunker
import com.sobdosuddhi.app.model.AiDetectionLabel
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.ProofreadingMode
import com.sobdosuddhi.app.model.RefinementMode
import com.sobdosuddhi.app.ui.viewmodel.SobdosuddhiViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SobdosuddhiAuditTests {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  private var mockServer: HttpServer? = null

  @After
  fun tearDown() {
    mockServer?.stop(0)
    mockServer = null
    val app = ApplicationProvider.getApplicationContext<SobdosuddhiApplication>()
    app.connectivityMonitor.setTestOverride(null)
  }

  private fun startLocalProxyServer(
    handler: (path: String, headers: Map<String, List<String>>, body: String) -> Pair<Int, String>
  ): String {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      val reqBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
      val headerMap = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value }
      val (code, respBody) = handler(exchange.requestURI.path, headerMap, reqBody)
      val bytes = respBody.toByteArray(Charsets.UTF_8)
      exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
      exchange.sendResponseHeaders(code, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    mockServer = server
    return "http://127.0.0.1:${server.address.port}"
  }

  // =========================================================================
  // SECURITY AUDIT: Zero API Keys / Secrets in APK & BuildConfig
  // =========================================================================

  @Test
  fun `security audit confirms no API keys or secrets exist in BuildConfig`() {
    val fieldNames = BuildConfig::class.java.declaredFields.map { it.name.uppercase() }
    assertFalse("GEMINI_API_KEY must NEVER be in BuildConfig/APK", fieldNames.any { it.contains("GEMINI_API_KEY") })
    assertFalse("API_KEY must NEVER be in BuildConfig/APK", fieldNames.any { it == "API_KEY" || it.contains("SECRET") })
    assertTrue("API_PROXY_BASE_URL must be configured", BuildConfig.API_PROXY_BASE_URL.startsWith("https://"))
    assertFalse("Proxy URL must not embed any Google API key", BuildConfig.API_PROXY_BASE_URL.contains("AIza"))
  }

  // =========================================================================
  // AUDIT 1 & 2: Offline App Startup & Offline Bengali Spelling Check
  // =========================================================================

  @Test
  fun `audit 1 and 2 - app runs without internet and offline spelling check works completely`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<SobdosuddhiApplication>()
    app.connectivityMonitor.setTestOverride(false)

    // Verify MainActivity starts cleanly offline past splash screen
    composeRule.mainClock.advanceTimeBy(1500L)
    composeRule.waitForIdle()
    composeRule.onNodeWithText("শব্দশুদ্ধি ১.০").assertIsDisplayed()

    val db = Room.inMemoryDatabaseBuilder(app, SobdosuddhiDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      // Unreachable proxy ensures zero network dependency during offline check
      val client = GeminiApiClient(baseUrl = "http://127.0.0.1:1")
      val engine = SobdosuddhiAiEngine(client, db.analysisHistoryDao())

      val sampleText = "শ্রাবন মাসে দুরত্ব বেড়েছে। শুধু মাত্র পুরুষ্কার আর আবিস্কার নিয়ে কথা হলো."
      val report = engine.proofreadManuscript(
        manuscriptId = null,
        manuscriptTitle = "অফলাইন পরীক্ষা",
        text = sampleText,
        category = ManuscriptCategory.PROSE,
        mode = ProofreadingMode.STANDARD,
        isOnline = false,
        forceOffline = false,
        onProgress = {},
        onPartialResult = {}
      )

      assertTrue("Report must be marked as offline mode", report.isOfflineMode)
      assertTrue("Must detect শ্রাবন -> শ্রাবণ", report.issues.any { it.originalText == "শ্রাবন" && it.suggestedText == "শ্রাবণ" })
      assertTrue("Must detect দুরত্ব -> দূরত্ব", report.issues.any { it.originalText == "দুরত্ব" && it.suggestedText == "দূরত্ব" })
      assertTrue("Must detect শুধু মাত্র -> শুধুমাত্র", report.issues.any { it.originalText == "শুধু মাত্র" && it.suggestedText == "শুধুমাত্র" })
      assertTrue("Must detect পুরুষ্কার -> পুরস্কার", report.issues.any { it.originalText == "পুরুষ্কার" && it.suggestedText == "পুরস্কার" })
      assertTrue("Must detect আবিস্কার -> আবিষ্কার", report.issues.any { it.originalText == "আবিস্কার" && it.suggestedText == "আবিষ্কার" })
      assertTrue("Must detect ASCII period হলো. -> হলো।", report.issues.any { it.originalText == "হলো." && it.suggestedText == "হলো।" })
      assertTrue("Suggested text must contain corrected words", report.suggestedFullText.contains("শ্রাবণ") && report.suggestedFullText.contains("পুরস্কার"))
    } finally {
      db.close()
    }
  }

  // =========================================================================
  // AUDIT 3: Manuscript Save, Load & Draft Version Control
  // =========================================================================

  @Test
  fun `audit 3 - manuscript save load update and version restore persist accurately in Room`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SobdosuddhiDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val msDao = db.manuscriptDao()
      val verDao = db.draftVersionDao()

      val originalText = "অধ্যায় ১: নদীর তীরে\n\nসকালের আলো ছড়িয়ে পড়েছে পদ্মার বুকে।"
      val id = msDao.insert(
        ManuscriptEntity(
          title = "পদ্মা পাড়ের গল্প",
          authorName = "অভিজিৎ",
          category = ManuscriptCategory.PROSE.name,
          content = originalText,
          originalContent = originalText,
          wordCount = BengaliUtils.calculateMetrics(originalText).words
        )
      )
      assertTrue("Inserted manuscript ID should be > 0", id > 0)

      val loaded = msDao.getById(id)
      assertNotNull("Saved manuscript must load back from Room", loaded)
      assertEquals("পদ্মা পাড়ের গল্প", loaded!!.title)
      assertEquals(originalText, loaded.content)
      assertEquals("অভিজিৎ", loaded.authorName)

      // Update manuscript and verify updated text persists while originalContent remains intact
      val revisedText = "$originalText\n\nদূরে পালতোলা নৌকা ভেসে চলেছে।"
      msDao.update(loaded.copy(content = revisedText, wordCount = BengaliUtils.calculateMetrics(revisedText).words))

      val reloaded = msDao.getById(id)!!
      assertEquals(revisedText, reloaded.content)
      assertEquals(originalText, reloaded.originalContent)

      // Verify version snapshots
      verDao.insert(
        com.sobdosuddhi.app.data.local.DraftVersionEntity(
          manuscriptId = id,
          versionNumber = 1,
          label = "মূল পান্ডুলিপি",
          changeSummary = "প্রাথমিক সংরক্ষণ",
          content = originalText,
          wordCount = BengaliUtils.calculateMetrics(originalText).words,
          sourceType = "INITIAL"
        )
      )
      verDao.insert(
        com.sobdosuddhi.app.data.local.DraftVersionEntity(
          manuscriptId = id,
          versionNumber = 2,
          label = "২য় খসড়া",
          changeSummary = "নতুন অনুচ্ছেদ যুক্ত",
          content = revisedText,
          wordCount = BengaliUtils.calculateMetrics(revisedText).words,
          sourceType = "MANUAL_SAVE"
        )
      )
      assertEquals(2, verDao.getMaxVersionNumber(id))
      assertEquals("২য় খসড়া", verDao.getLatestVersion(id)?.label)
    } finally {
      db.close()
    }
  }

  // =========================================================================
  // AUDIT 4, 5, 6, 7: Offline->Online Recovery + Server-Side Proxy AI Engines
  // =========================================================================

  @Test
  fun `audit 4 5 6 7 - online recovery and AI proofreading detector refinement work via server proxy without client keys`() = runBlocking {
    val observedHeaders = CopyOnWriteArrayList<Map<String, List<String>>>()
    val observedPaths = CopyOnWriteArrayList<String>()
    val requestCount = AtomicInteger(0)

    val proxyUrl = startLocalProxyServer { path, headers, body ->
      observedPaths.add(path)
      observedHeaders.add(headers)
      requestCount.incrementAndGet()

      val payload = JSONObject(body)
      val sys = payload.optString("systemInstruction", "")
      val responseInnerJson = when {
        sys.contains("প্রুফরিডার") -> """
          {
            "summary": "পান্ডুলিপির বানান ও বাক্য গঠন বিশ্লেষণ সম্পন্ন হয়েছে।",
            "poetryNote": "লেখকের নিজস্ব ভঙ্গি সুরক্ষিত রাখা হয়েছে।",
            "correctedText": "শ্রাবণ মাসে আমাদের শহরে প্রবল বৃষ্টি নামে।",
            "issues": [
              {
                "originalText": "শ্রাবন",
                "suggestedText": "শ্রাবণ",
                "category": "বানান ভুল",
                "severity": "গুরুত্বপূর্ণ",
                "explanation": "ণত্ব বিধান অনুযায়ী ‘শ্রাবণ’ শুদ্ধ।",
                "relativeParagraphIndex": 0,
                "poetryPreservationNote": ""
              }
            ]
          }
        """.trimIndent()

        sys.contains("স্টাইলোমেট্রি") -> """
          {
            "classification": "MIXED",
            "detectionIndicatorLevel": "মাঝারি মিশ্র প্রবণতা",
            "aiLikelihoodRange": "মাঝারি প্রবণতা (কয়েকটি গতানুগতিক সংযোগশব্দ)",
            "humanLikelihoodRange": "আংশিক মানবিক অভিব্যক্তি স্পষ্ট",
            "confidenceLevel": "MEDIUM",
            "explanation": "পাঠ্যে কিছু অনুমেয় কাঠামো থাকলেও ব্যক্তিগত অনুভূতির ছাপ রয়েছে।",
            "overallCharacteristics": ["পরিমিত বাক্য দৈর্ঘ্য", "স্বাভাবিক বাংলা শব্দচয়ন"],
            "linguisticDimensions": [
              {"dimensionName": "বাক্য গঠনের পুনরাবৃত্তি", "observation": "মাঝারি", "tendency": "মিশ্র"},
              {"dimensionName": "মানবিক লেখার স্বাভাবিক বৈচিত্র্য", "observation": "উপস্থিত", "tendency": "মানবিক"}
            ],
            "suspiciousSentences": [
              {"sentence": "সর্বোপরি বলা যায় বিষয়টি গুরুত্বপূর্ণ।", "reason": "গতানুগতিক সংযোগশব্দ", "signalStrength": "মাঝারি"}
            ],
            "paragraphAnalysis": [
              {"paragraphIndex": 0, "excerpt": "শ্রাবণ মাসে…", "assessment": "মিশ্র বৈশিষ্ট্য", "observation": "আংশিক যান্ত্রিক প্রবাহ"}
            ]
          }
        """.trimIndent()

        else -> """
          {
            "summary": "স্বাভাবিক ও সাবলীল বাংলায় পরিমার্জন সম্পন্ন হয়েছে।",
            "refinedText": "শ্রাবণের ঘন মেঘে আমাদের শহরে অঝোরে বৃষ্টি নামে।",
            "changes": [
              {
                "originalSegment": "শ্রাবণ মাসে আমাদের শহরে প্রবল বৃষ্টি নামে।",
                "refinedSegment": "শ্রাবণের ঘন মেঘে আমাদের শহরে অঝোরে বৃষ্টি নামে।",
                "changeReason": "আরও স্বাভাবিক ও প্রাঞ্জল বাংলা প্রবাহ তৈরির জন্য পরিমার্জিত।",
                "changeType": "ছন্দ ও প্রবাহ"
              }
            ]
          }
        """.trimIndent()
      }

      200 to JSONObject().put("text", responseInnerJson).toString()
    }

    val app = ApplicationProvider.getApplicationContext<SobdosuddhiApplication>()
    val testClient = GeminiApiClient(baseUrl = proxyUrl)
    app.overrideAiClientForTesting(testClient)

    // AUDIT 4: Start Offline -> verify offline guard -> switch to Online -> verify auto-recovery
    app.connectivityMonitor.setTestOverride(false)
    val vm = SobdosuddhiViewModel(app)
    vm.updateTitle("অনলাইন অডিট")
    vm.updateContent("শ্রাবন মাসে আমাদের শহরে প্রবল বৃষ্টি নামে।")
    vm.runAiDetection()
    assertNotNull("Offline AI Detection must show clear Bengali offline message", vm.analysis.value.errorMessage)
    assertTrue(vm.analysis.value.errorMessage!!.contains("অফলাইন"))

    // Internet returns!
    app.connectivityMonitor.setTestOverride(true)
    delay(150L)
    assertTrue("Connectivity must transition to Online", vm.isOnline.value)
    assertNull("Offline error message must clear automatically when internet returns", vm.analysis.value.errorMessage)

    // AUDIT 5: Online AI Proofreading + Caching via server proxy
    val db = Room.inMemoryDatabaseBuilder(app, SobdosuddhiDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val engine = SobdosuddhiAiEngine(testClient, db.analysisHistoryDao())
      val proofReport = engine.proofreadManuscript(
        manuscriptId = null,
        manuscriptTitle = "অনলাইন অডিট",
        text = "শ্রাবন মাসে আমাদের শহরে প্রবল বৃষ্টি নামে।",
        category = ManuscriptCategory.PROSE,
        mode = ProofreadingMode.STANDARD,
        isOnline = true,
        onProgress = {},
        onPartialResult = {}
      )
      assertFalse("Online proofreading must not be offline mode", proofReport.isOfflineMode)
      assertTrue("AI proofreading must report শ্রাবন -> শ্রাবণ", proofReport.issues.any { it.suggestedText == "শ্রাবণ" })

      // Second identical call should hit local Room cache without extra server request
      val callsAfterFirst = requestCount.get()
      val cachedReport = engine.proofreadManuscript(
        manuscriptId = null,
        manuscriptTitle = "অনলাইন অডিট",
        text = "শ্রাবন মাসে আমাদের শহরে প্রবল বৃষ্টি নামে।",
        category = ManuscriptCategory.PROSE,
        mode = ProofreadingMode.STANDARD,
        useCache = true,
        isOnline = true,
        onProgress = {},
        onPartialResult = {}
      )
      assertTrue("Second identical request must load from cache", cachedReport.fromCache)
      assertEquals("Cache hit must not send extra server call", callsAfterFirst, requestCount.get())

      // AUDIT 6: AI Writing Detector via server proxy
      val detectReport = engine.detectAiWriting(
        manuscriptId = null,
        manuscriptTitle = "অনলাইন অডিট",
        text = "সর্বোপরি বলা যায় বিষয়টি গুরুত্বপূর্ণ। শ্রাবণ মাসে আমাদের শহরে প্রবল বৃষ্টি নামে।",
        category = ManuscriptCategory.PROSE,
        useCache = false,
        onProgress = {}
      )
      assertEquals(AiDetectionLabel.MIXED.name, detectReport.classification)
      assertTrue("Must contain linguistic dimensions", detectReport.linguisticDimensions.isNotEmpty())
      assertTrue("Must contain suspicious sentences", detectReport.suspiciousSentences.isNotEmpty())
      assertTrue("Must contain mandatory uncertainty warning", detectReport.uncertaintyWarning.contains("সম্ভাব্যতামূলক"))

      // AUDIT 7: Human Writing Refinement via server proxy
      val refineReport = engine.refineHumanLanguage(
        manuscriptId = null,
        manuscriptTitle = "অনলাইন অডিট",
        text = "শ্রাবণ মাসে আমাদের শহরে প্রবল বৃষ্টি নামে।",
        category = ManuscriptCategory.PROSE,
        mode = RefinementMode.NATURAL,
        useCache = false,
        onProgress = {}
      )
      assertTrue("Refined text must reflect natural Bengali rewrite", refineReport.refinedFullText.contains("অঝোরে বৃষ্টি"))
      assertTrue("Refinement changes must include explanation", refineReport.changes.isNotEmpty())
      assertTrue("Editorial disclaimer must be included", refineReport.editorialDisclaimer.contains("সম্পাদকীয় নোট"))

      // SECURITY VERIFICATION ON ACTUAL WIRE REQUESTS
      assertTrue("All AI requests must target server proxy /api/gemini/generate", observedPaths.all { it == "/api/gemini/generate" })
      assertTrue("Client must NEVER send x-goog-api-key header", observedHeaders.none { it.containsKey("x-goog-api-key") })
    } finally {
      db.close()
    }
  }

  // =========================================================================
  // AUDIT 8: Large Manuscript Stress Test (120,000+ chars, Zero Crash / OOM)
  // =========================================================================

  @Test
  fun `audit 8 - large 120k+ character Bengali manuscript processes without crash or integer overflow`() {
    val basePara = "অধ্যায় ও গল্পের দীর্ঘ বিবরণ: শ্রাবন মাসের আকাশে ঘন কালো মেঘ জমেছে এবং শহরের প্রতিটি রাস্তায় শান্ত বাতাস বইছে। নদীর ওপারে দূরের গ্রামগুলো কুয়াশার চাদরে ঢাকা পড়ে গেছে আর মাঝিরা নৌকা বেঁধে ঘাটে ফিরে আসছে।"
    val hugeManuscript = buildString {
      for (chapter in 1..20) {
        append("অধ্যায় ").append(BengaliUtils.toBengaliDigits(chapter)).append(": দীর্ঘ যাত্রা\n\n")
        for (p in 1..28) {
          append(basePara).append(" অনুচ্ছেদ ").append(p).append("।\n\n")
        }
      }
    }.trim()

    assertTrue("Test manuscript must exceed 110,000 chars", hugeManuscript.length > 110_000)

    // 1. Metrics & TOC
    val metrics = BengaliUtils.calculateMetrics(hugeManuscript)
    assertTrue("Word count must be large", metrics.words > 15_000)
    assertEquals(20, metrics.chapters)

    // 2. Local Offline Proofreading on huge manuscript (capped safely, fast, no OOM)
    val localIssues = BengaliLocalAnalyzer.runLocalProofreadingChecks(hugeManuscript, ManuscriptCategory.PROSE)
    assertTrue("Should detect spelling errors in large manuscript", localIssues.isNotEmpty())
    assertTrue("Issue count must stay bounded to protect UI memory", localIssues.size <= 300)

    // 3. Smart Chunking with zero text loss
    val chunks = ManuscriptChunker.chunk(hugeManuscript, ManuscriptCategory.PROSE, maxCharsPerChunk = 2200)
    assertTrue("Huge manuscript must split into many chunks", chunks.size >= 40)
    val reassembled = chunks.joinToString("\n\n") { it.text }
    assertEquals("Chunking large manuscript must lose zero characters", hugeManuscript, reassembled)

    // 4. Diff Engine on >50,000 tokens (exercises the Long-arithmetic Int-overflow fix!)
    val modifiedHuge = hugeManuscript.replaceFirst("শ্রাবন", "শ্রাবণ")
    val diff = BengaliUtils.computeDiff(hugeManuscript, modifiedHuge)
    assertTrue("Diff on 120k+ char manuscript must succeed without OOM or overflow", diff.isNotEmpty())

    // 5. ViewModel undo stack memory budget cap test
    val app = ApplicationProvider.getApplicationContext<SobdosuddhiApplication>()
    val vm = SobdosuddhiViewModel(app)
    val fiftyK = "বাংলা ".repeat(9000) // ~54,000 chars
    for (i in 1..45) {
      vm.updateContent("$fiftyK$i", pushUndo = true)
    }
    val totalUndoChars = vm.editor.value.undoStack.sumOf { it.length }
    assertTrue("Undo stack total characters must stay bounded (~2MB) to prevent OOM", totalUndoChars <= 2_200_000)
  }

  // =========================================================================
  // AUDIT 9: Poetry Literary Preservation (Rhythm, Stanzas, Imagery, Line Breaks)
  // =========================================================================

  @Test
  fun `audit 9 - poetry literary structure rhythm stanzas and poetic diction are strictly preserved`() = runBlocking {
    val poem = """
      ধীরে ধীরে নামে জোছনা নদীর বুকে
      বার বার ডাকে বাতাস অচেনা সুরে
      শ্রাবন আকাশে ভাসে রূপালী মেঘ
      নিঃশব্দ পায়ে হাঁটে পুরোনো আবেগ

      ছায়ার আড়ালে জ্বলে স্মৃতির প্রদীপ
      অন্ত্যমিলের টানে কাঁপে রাতের দ্বীপ
      শব্দের ডানায় ওড়ে কবির ভাবনা
      হৃদয়ের গহীনে বাজে নীরব সাধনা
    """.trimIndent()

    // 1. Offline local analyzer preserves poetic reduplication ('ধীরে ধীরে', 'বার বার') and poetic 'জোছনা'
    val localIssues = BengaliLocalAnalyzer.runLocalProofreadingChecks(poem, ManuscriptCategory.POETRY)
    assertTrue("Should catch genuine spelling error শ্রাবন -> শ্রাবণ", localIssues.any { it.originalText == "শ্রাবন" && it.suggestedText == "শ্রাবণ" })
    assertFalse("Must NOT alter poetic diction 'জোছনা' in poetry mode", localIssues.any { it.originalText == "জোছনা" })
    assertFalse("Must NOT flag intentional poetic reduplication 'ধীরে ধীরে'", localIssues.any { it.originalText.contains("ধীরে") })
    assertFalse("Must NOT flag intentional poetic reduplication 'বার বার'", localIssues.any { it.originalText.contains("বার") })

    // 2. Even if an AI model mistakenly collapses poetic lines into 1 prose line, strict poetry safeguard rejects it!
    val proxyUrl = startLocalProxyServer { _, _, _ ->
      val collapsedAiJson = """
        {
          "summary": "বানান পরীক্ষা সম্পন্ন।",
          "poetryNote": "ছন্দ সুরক্ষিত।",
          "correctedText": "ধীরে ধীরে নামে জোছনা নদীর বুকে বার বার ডাকে বাতাস শ্রাবণ আকাশে।",
          "issues": [
            {
              "originalText": "শ্রাবন",
              "suggestedText": "শ্রাবণ",
              "category": "বানান ভুল",
              "severity": "গুরুত্বপূর্ণ",
              "explanation": "প্রমিত বানান ‘শ্রাবণ’।",
              "relativeParagraphIndex": 0,
              "poetryPreservationNote": "ছন্দ ও মাত্রা অক্ষুণ্ণ রাখা হয়েছে।"
            }
          ]
        }
      """.trimIndent()
      200 to JSONObject().put("text", collapsedAiJson).toString()
    }

    val app = ApplicationProvider.getApplicationContext<SobdosuddhiApplication>()
    val db = Room.inMemoryDatabaseBuilder(app, SobdosuddhiDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val engine = SobdosuddhiAiEngine(GeminiApiClient(baseUrl = proxyUrl), db.analysisHistoryDao())
      val report = engine.proofreadManuscript(
        manuscriptId = null,
        manuscriptTitle = "জোছনা রাতের কবিতা",
        text = poem,
        category = ManuscriptCategory.POETRY,
        mode = ProofreadingMode.STANDARD,
        useCache = false,
        isOnline = true,
        poetryProtectionStrict = true,
        onProgress = {},
        onPartialResult = {}
      )

      // Line count and stanzas MUST remain identical to original poem!
      assertEquals(
        "Strict poetry protection must preserve exact line count & stanza breaks",
        poem.lines().size,
        report.suggestedFullText.lines().size
      )
      assertTrue("Genuine spelling fix শ্রাবণ must still be applied", report.suggestedFullText.contains("শ্রাবণ আকাশে"))
      assertTrue("Poetic imagery & diction must remain untouched", report.suggestedFullText.contains("ধীরে ধীরে নামে জোছনা"))
    } finally {
      db.close()
    }

    // 3. Single giant continuous poem (no blank lines) must split ONLY on newline boundaries, never mid-line
    val longContinuousPoem = (1..80).joinToString("\n") { i -> "পঙ্ক্তি $i: রাতের আকাশে তারার মেলা জ্বলে নিরবধি" }
    val poemChunks = ManuscriptChunker.chunk(longContinuousPoem, ManuscriptCategory.POETRY, maxCharsPerChunk = 600)
    assertTrue("Long continuous poem must chunk safely", poemChunks.size > 2)
    assertTrue("Every chunk must preserve complete lines (zero mid-line splits)", poemChunks.all { chunk ->
      chunk.text.lines().all { line -> line.startsWith("পঙ্ক্তি ") && line.endsWith("নিরবধি") }
    })
  }

  // =========================================================================
  // AUDIT 10: Android Navigation & Full Bengali Compose UI Flow Test
  // =========================================================================

  @Test
  fun `audit 10 - complete Bengali Android UI navigation and tool screen transitions work smoothly`() {
    val app = ApplicationProvider.getApplicationContext<SobdosuddhiApplication>()
    app.connectivityMonitor.setTestOverride(false) // Verify full UI works offline too!

    // Advance past 900ms animated splash screen
    composeRule.mainClock.advanceTimeBy(1500L)
    composeRule.waitForIdle()

    // Home screen visible
    composeRule.onNodeWithText("নতুন পান্ডুলিপি তৈরি করুন").assertIsDisplayed()

    // Navigate all 5 bottom navigation tabs
    composeRule.onNodeWithText("নতুন লেখা").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("সংরক্ষণ").assertIsDisplayed()

    composeRule.onNodeWithText("আমার পান্ডুলিপি").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithText("ইতিহাস").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("ইতিহাস ও সংস্করণ").assertIsDisplayed()

    composeRule.onNodeWithText("সেটিংস").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("সেটিংস ও গোপনীয়তা").assertIsDisplayed()

    // Return to Editor and navigate through all 4 Intelligence Tool Screens + Back button
    composeRule.onNodeWithText("নতুন লেখা").performClick()
    composeRule.waitForIdle()

    // Open Proofreading Screen & check offline spelling button exists
    composeRule.onNodeWithText("প্রুফরিডিং").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("উন্নত বাংলা প্রুফরিডিং ইঞ্জিন").assertIsDisplayed()
    composeRule.onNodeWithText("অফলাইন বানান পরীক্ষা (তাৎক্ষণিক)").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithContentDescription("ফিরুন").performClick()
    composeRule.waitForIdle()

    // Open AI Detection Screen
    composeRule.onNodeWithText("AI শনাক্তকরণ").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("AI লেখা শনাক্তকরণ").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("ফিরুন").performClick()
    composeRule.waitForIdle()

    // Open Human Refinement Screen
    composeRule.onNodeWithText("মানবিক রূপান্তর").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("মানবিক ভাষায় রূপান্তর").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("ফিরুন").performClick()
    composeRule.waitForIdle()

    // Open Manuscript Organizer Screen
    composeRule.onNodeWithText("পান্ডুলিপি সাজান").performClick()
    composeRule.waitForIdle()
    assertTrue(composeRule.onAllNodesWithText("পান্ডুলিপি সাজান").fetchSemanticsNodes().isNotEmpty())
    composeRule.onNodeWithContentDescription("ফিরুন").performClick()
    composeRule.waitForIdle()

    // Return Home cleanly
    composeRule.onNodeWithText("হোম").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("শব্দশুদ্ধি ১.০").assertIsDisplayed()
  }

  // =========================================================================
  // REGRESSION TEST: Auth/HTML Redirect Wall Must Never Break Phone UX
  // =========================================================================

  @Test
  fun `regression - auth cookie html redirect from preview proxy seamlessly falls back to local Bengali engines`() = runBlocking {
    val authWallServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    authWallServer.createContext("/") { exchange ->
      val html = "<!doctype html><html><body>Cookie check login redirect</body></html>".toByteArray(Charsets.UTF_8)
      exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
      exchange.responseHeaders.add("Location", "/__cookie_check.html?return_url=%2Fapi%2Fgemini%2Fgenerate")
      exchange.sendResponseHeaders(302, html.size.toLong())
      exchange.responseBody.use { it.write(html) }
    }
    authWallServer.start()
    mockServer = authWallServer
    val authWallUrl = "http://127.0.0.1:${authWallServer.address.port}"

    val app = ApplicationProvider.getApplicationContext<SobdosuddhiApplication>()
    val db = Room.inMemoryDatabaseBuilder(app, SobdosuddhiDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val client = GeminiApiClient(baseUrl = authWallUrl)
      val health = client.checkServerHealth()
      assertEquals(ServerHealthStatus.AUTH_WALL_BLOCKED, health.status)

      val engine = SobdosuddhiAiEngine(client, db.analysisHistoryDao())

      // 1. Standard Online Proofreading falls back smoothly without "AI সার্ভারের উত্তর পড়া সম্ভব হয়নি।"
      val proofSample = "শ্রাবন মাসে কারন ছাড়াই দুরত্ব বেড়েছে. পুরুষ্কার নিয়ে কথা হলো."
      val proofReport = engine.proofreadManuscript(
        manuscriptId = null,
        manuscriptTitle = "রিগ্রেশন পরীক্ষা",
        text = proofSample,
        category = ManuscriptCategory.PROSE,
        mode = ProofreadingMode.STANDARD,
        useCache = false,
        isOnline = true,
        onProgress = {},
        onPartialResult = {}
      )
      assertTrue(proofReport.isOfflineMode)
      assertTrue(proofReport.issues.any { it.originalText == "শ্রাবন" && it.suggestedText == "শ্রাবণ" })
      assertTrue(proofReport.issues.any { it.originalText == "কারন" && it.suggestedText == "কারণ" })

      // 2. AI Detection falls back smoothly to 10-dimension local Bengali stylometry
      val detectSample = """
        সর্বোপরি বলা যায় যে, আধুনিক বাংলা সাহিত্যের বিকাশে প্রযুক্তির ব্যবহার গুরুত্বপূর্ণ ভূমিকা পালন করে। পরিশেষে বলা যায় যে, বিভিন্ন দিক থেকে এই পরিবর্তন অত্যন্ত তাৎপর্যপূর্ণভাবে সমাজের প্রতিটি স্তরে গভীর প্রভাব বিস্তার করেছে। অন্যদিকে এটি লক্ষণীয় যে, তরুণ লেখকেরা এখন নতুন ধারায় তাদের ভাবনা প্রকাশ করছেন এবং পাঠকদের কাছে তা দ্রুত পৌঁছে যাচ্ছে। উপসংহারে বলা যায় যে, সময়ের সাথে তাল মিলিয়ে এগিয়ে যাওয়া আমাদের সকলের দায়িত্ব।
      """.trimIndent()
      val detectReport = engine.detectAiWriting(
        manuscriptId = null,
        manuscriptTitle = "স্টাইলোমেট্রি রিগ্রেশন",
        text = detectSample,
        category = ManuscriptCategory.PROSE,
        useCache = false,
        onProgress = {}
      )
      assertEquals(10, detectReport.linguisticDimensions.size)
      assertTrue(detectReport.suspiciousSentences.isNotEmpty())
      assertTrue(detectReport.uncertaintyWarning.contains("সম্ভাব্যতামূলক"))

      // 3. Human Writing Refinement falls back smoothly with real prose refinements
      val refineReport = engine.refineHumanLanguage(
        manuscriptId = null,
        manuscriptTitle = "রিফাইনমেন্ট রিগ্রেশন",
        text = "সর্বোপরি বলা যায় যে, শ্রাবন মাসে শিক্ষা গুরুত্বপূর্ণ ভূমিকা পালন করে।",
        category = ManuscriptCategory.PROSE,
        mode = RefinementMode.NATURAL,
        useCache = false,
        onProgress = {}
      )
      assertTrue(refineReport.changes.isNotEmpty())
      assertTrue(refineReport.refinedFullText.contains("সব মিলিয়ে,"))
      assertTrue(refineReport.refinedFullText.contains("বড় ভূমিকা রাখে"))
      assertTrue(refineReport.refinedFullText.contains("শ্রাবণ"))
    } finally {
      db.close()
    }
  }

  @Test
  fun `regression - mobile typing detectors boundary-safe replacement and online fallback messaging work accurately`() = runBlocking {
    // 1. Mobile keyboard spelling + double kar + missing space after dari + double punctuation
    val mobileSample = "আজ ব্রিষ্টি হওয়ার কারনে বাাংলা বই পড়া গেল।সে হঠাত চলে গেল।।"
    val issues = BengaliLocalAnalyzer.runLocalProofreadingChecks(mobileSample, ManuscriptCategory.PROSE)
    assertTrue("Should detect ব্রিষ্টি -> বৃষ্টি", issues.any { it.originalText == "ব্রিষ্টি" && it.suggestedText == "বৃষ্টি" })
    assertTrue("Should detect কারনে -> কারণে", issues.any { it.originalText == "কারনে" && it.suggestedText == "কারণে" })
    assertTrue("Should detect duplicate kar বাাংলা -> বাংলা", issues.any { it.originalText.contains("বাাংলা") && it.suggestedText.contains("বাংলা") })
    assertTrue("Should detect missing space after dari গেল।সে -> গেল। সে", issues.any { it.originalText == "গেল।সে" && it.suggestedText == "গেল। সে" })
    assertTrue("Should detect হঠাত -> হঠাৎ", issues.any { it.originalText == "হঠাত" && it.suggestedText == "হঠাৎ" })
    assertTrue("Should detect double dari ।। -> ।", issues.any { it.originalText == "।।" && it.suggestedText == "।" })

    // 2. Boundary-safe replacement must NEVER corrupt ellipsis or longer compound words
    val ellipsisText = "সব ঠিক হলো... তবু কথা হলো."
    val ellipsisFixed = BengaliLocalAnalyzer.replaceFirstSafely(ellipsisText, "হলো.", "হলো।")
    assertEquals("সব ঠিক হলো... তবু কথা হলো।", ellipsisFixed)

    val compoundText = "তার কারনেই সব হলো; আসল কারন কী?"
    val compoundFixed = BengaliLocalAnalyzer.replaceFirstSafely(compoundText, "কারন", "কারণ")
    assertEquals("তার কারনেই সব হলো; আসল কারণ কী?", compoundFixed)

    // 3. AI Detection cache isolation + online device fallback summary wording
    val serverHits = AtomicInteger(0)
    var serveOnlineAi = false
    val dynamicProxyUrl = startLocalProxyServer { _, _, _ ->
      serverHits.incrementAndGet()
      if (!serveOnlineAi) {
        302 to "<!doctype html><html><body>Cookie check</body></html>"
      } else {
        val onlineJson = """
          {
            "classification": "LIKELY_HUMAN",
            "detectionIndicatorLevel": "স্বাভাবিক মানবিক লেখা",
            "aiLikelihoodRange": "খুব কম",
            "humanLikelihoodRange": "প্রবল মানবিক বৈশিষ্ট্য",
            "confidenceLevel": "HIGH",
            "explanation": "অনলাইন AI সার্ভার থেকে প্রাপ্ত বিশ্লেষণ।",
            "overallCharacteristics": ["স্বাভাবিক বাংলা"],
            "linguisticDimensions": [
              {"dimensionName": "বাক্য বৈচিত্র্য", "observation": "উচ্চ", "tendency": "মানবিক"}
            ],
            "suspiciousSentences": [],
            "paragraphAnalysis": []
          }
        """.trimIndent()
        200 to JSONObject().put("text", onlineJson).toString()
      }
    }

    val app = ApplicationProvider.getApplicationContext<SobdosuddhiApplication>()
    val db = Room.inMemoryDatabaseBuilder(app, SobdosuddhiDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val engine = SobdosuddhiAiEngine(GeminiApiClient(baseUrl = dynamicProxyUrl), db.analysisHistoryDao())

      // Online device + unreachable AI proxy => summary must guide to Settings, NOT say "ইন্টারনেট যুক্ত হলে"
      val fallbackProof = engine.proofreadManuscript(
        manuscriptId = null,
        manuscriptTitle = "অনলাইন ফলব্যাক বার্তা",
        text = "আজ আকাশ পরিষ্কার।",
        category = ManuscriptCategory.PROSE,
        mode = ProofreadingMode.STANDARD,
        useCache = false,
        isOnline = true,
        onProgress = {},
        onPartialResult = {}
      )
      assertTrue(fallbackProof.isOfflineMode)
      assertTrue("Online fallback summary must guide user to Settings", fallbackProof.overallSummary.contains("সেটিংস"))
      assertFalse("Online fallback summary must NOT claim internet is disconnected", fallbackProof.overallSummary.contains("ইন্টারনেট যুক্ত হলে"))

      // Run AI Detection while proxy fails -> saves under _LOCAL cache key
      val sample = "নদীর তীরে বিকেল বেলায় শীতল বাতাস বইছে এবং আকাশে রঙিন ঘুড়ি উড়ছে। দূরের গ্রাম থেকে ভেসে আসছে রাখালের বাঁশির সুর। সোনালি রোদের আভায় জলের ঢেউগুলো চিকচিক করে উঠছে আর পাখিরা ঝাঁক বেঁধে নীড়ে ফিরে যাচ্ছে। গ্রামের মেঠো পথ ধরে হাঁটতে হাঁটতে পুরোনো শৈশবের কথা মনে পড়ে যায়।"
      val localDetect = engine.detectAiWriting(
        manuscriptId = null,
        manuscriptTitle = "ক্যাশ আইসোলেশন",
        text = sample,
        category = ManuscriptCategory.PROSE,
        useCache = true,
        onProgress = {}
      )
      assertEquals(10, localDetect.linguisticDimensions.size)

      // Now online server comes back -> MUST hit online server and NOT return stale local cache!
      serveOnlineAi = true
      val hitsBeforeOnline = serverHits.get()
      val onlineDetect = engine.detectAiWriting(
        manuscriptId = null,
        manuscriptTitle = "ক্যাশ আইসোলেশন",
        text = sample,
        category = ManuscriptCategory.PROSE,
        useCache = true,
        onProgress = {}
      )
      assertTrue("Must call online server instead of serving local fallback cache", serverHits.get() > hitsBeforeOnline)
      assertEquals(AiDetectionLabel.LIKELY_HUMAN.name, onlineDetect.classification)
      assertFalse("Fresh online result must not be marked fromCache", onlineDetect.fromCache)
    } finally {
      db.close()
    }
  }
}

