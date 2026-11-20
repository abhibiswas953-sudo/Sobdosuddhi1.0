package com.sobdosuddhi.app.data.ai

import com.sobdosuddhi.app.data.local.AnalysisHistoryDao
import com.sobdosuddhi.app.data.local.AnalysisHistoryEntity
import com.sobdosuddhi.app.domain.BengaliLocalAnalyzer
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.domain.ManuscriptChunk
import com.sobdosuddhi.app.domain.ManuscriptChunker
import com.sobdosuddhi.app.model.AiDetectionLabel
import com.sobdosuddhi.app.model.AiDetectionReport
import com.sobdosuddhi.app.model.AnalysisType
import com.sobdosuddhi.app.model.ConfidenceLevel
import com.sobdosuddhi.app.model.ConsistencyFinding
import com.sobdosuddhi.app.model.LinguisticDimensionScore
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.OrganizationReport
import com.sobdosuddhi.app.model.ParagraphAiNote
import com.sobdosuddhi.app.model.ProcessingStage
import com.sobdosuddhi.app.model.ProgressState
import com.sobdosuddhi.app.model.ProofreadingIssue
import com.sobdosuddhi.app.model.ProofreadingMode
import com.sobdosuddhi.app.model.ProofreadingReport
import com.sobdosuddhi.app.model.RefinementChange
import com.sobdosuddhi.app.model.RefinementMode
import com.sobdosuddhi.app.model.RefinementReport
import com.sobdosuddhi.app.model.SuspiciousSentence
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Core Bengali Manuscript Intelligence Orchestrator for Sobdosuddhi 1.0.
 *
 * Implements 4 strictly separated operations:
 * 1. Ultra-Fast Proofreading Engine (local pre-checks + smart chunking + parallel Gemini calls + SHA-256 caching)
 * 2. AI Writing Detection Engine (probabilistic Bengali linguistic analysis + mandatory uncertainty warning)
 * 3. Human Writing Refinement Engine (5 Bengali modes + Poetry Protection + segment-level diff explanations)
 * 4. Complete Manuscript Organizer (local deterministic structure analysis + AI consistency & literary advice)
 */
class SobdosuddhiAiEngine(
  private val geminiClient: GeminiApiClient,
  private val historyDao: AnalysisHistoryDao
) {

  val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val proofreadingAdapter = moshi.adapter(ProofreadingReport::class.java)
  private val aiDetectionAdapter = moshi.adapter(AiDetectionReport::class.java)
  private val refinementAdapter = moshi.adapter(RefinementReport::class.java)
  private val organizationAdapter = moshi.adapter(OrganizationReport::class.java)

  companion object {
    const val MANDATORY_AI_DETECTION_DISCLAIMER =
      "সতর্কবার্তা: AI লেখা শনাক্তকরণ একটি সম্ভাব্যতামূলক (probabilistic) ভাষাতাত্ত্বিক বিশ্লেষণ, এটি কোনো চূড়ান্ত বা শতভাগ নিশ্চিত প্রমাণ নয়। পরিশীলিত ব্যাকরণ, প্রমিত বাংলা বা পুনরাবৃত্ত শব্দ থাকলেই কোনো লেখা AI-লিখিত বলে ধরে নেওয়া উচিত নয়। লেখকের প্রকৃত লেখকত্ব কেবল এই বিশ্লেষণের মাধ্যমে যাচাই করা সম্ভব নয়।"

    const val MANDATORY_REFINEMENT_DISCLAIMER =
      "সম্পাদকীয় নোট: ‘মানবিক ভাষায় রূপান্তর’ একটি প্রকৃত বাংলা ভাষা ও শৈলী পরিমার্জন ব্যবস্থা। এটি লেখাকে স্বাভাবিক ও প্রাঞ্জল করতে সাহায্য করে; এটি কোনো AI ডিটেক্টর এড়ানো বা লেখকত্ব পরিবর্তনের নিশ্চয়তা দেয় না।"

    const val MANDATORY_ORGANIZER_APPROVAL_NOTE =
      "আপনার সুস্পষ্ট অনুমোদন ছাড়া কোনো অধ্যায়, অনুচ্ছেদ বা কবিতার ক্রম স্বয়ংক্রিয়ভাবে পরিবর্তন করা হবে না। নিচের প্রস্তাবনাগুলো পর্যালোচনা করে প্রয়োজনে ‘বিন্যাস প্রয়োগ করুন’ বোতামে চাপ দিন।"
  }

  // =========================================================================
  // 1. ULTRA-FAST ADVANCED BENGALI PROOFREADING ENGINE
  // =========================================================================

  suspend fun proofreadOffline(
    manuscriptId: Long?,
    manuscriptTitle: String,
    text: String,
    category: ManuscriptCategory,
    onProgress: (ProgressState) -> Unit
  ): ProofreadingReport = proofreadManuscript(
    manuscriptId = manuscriptId,
    manuscriptTitle = manuscriptTitle,
    text = text,
    category = category,
    mode = ProofreadingMode.QUICK,
    useCache = false,
    isOnline = false,
    forceOffline = true,
    poetryProtectionStrict = true,
    onProgress = onProgress,
    onPartialResult = {}
  )

  suspend fun proofreadManuscript(
    manuscriptId: Long?,
    manuscriptTitle: String,
    text: String,
    category: ManuscriptCategory,
    mode: ProofreadingMode,
    useCache: Boolean = true,
    isOnline: Boolean = true,
    forceOffline: Boolean = false,
    poetryProtectionStrict: Boolean = true,
    onProgress: (ProgressState) -> Unit,
    onPartialResult: (ProofreadingReport) -> Unit
  ): ProofreadingReport = withContext(Dispatchers.Default) {
    val startTime = System.currentTimeMillis()
    val elapsedSec = { ((System.currentTimeMillis() - startTime) / 1000L).toInt() }

    onProgress(
      ProgressState(
        stage = ProcessingStage.RECEIVED,
        detailText = "পান্ডুলিপি গ্রহণ করা হয়েছে (${BengaliUtils.toBengaliDigits(text.length)} অক্ষর)।",
        elapsedSeconds = elapsedSec()
      )
    )

    val runOfflineOnly = forceOffline || !isOnline
    val cacheModeKey = "${category.name}_${mode.name}_${if (runOfflineOnly) "OFFLINE" else "ONLINE"}"
    val contentHash = BengaliUtils.sha256(text)

    if (useCache && !runOfflineOnly) {
      val cached = historyDao.findCached(contentHash, AnalysisType.PROOFREADING.name, cacheModeKey)
      if (cached != null) {
        val parsed = runCatching { proofreadingAdapter.fromJson(cached.resultJson) }.getOrNull()
        if (parsed != null) {
          onProgress(
            ProgressState(
              stage = ProcessingStage.FINAL_PREPARATION,
              detailText = "অপরিবর্তিত পান্ডুলিপির পূর্বের বিশ্লেষণ পাওয়া গেছে—তাৎক্ষণিক লোড হচ্ছে।",
              completedChunks = parsed.totalChunks,
              totalChunks = parsed.totalChunks,
              elapsedSeconds = elapsedSec()
            )
          )
          return@withContext parsed.copy(fromCache = true, durationMs = System.currentTimeMillis() - startTime)
        }
      }
    }

    // Stage 2: Local fast linguistic pre-checks & smart chunking (works 100% offline)
    onProgress(
      ProgressState(
        stage = ProcessingStage.TEXT_PROCESSING,
        detailText = if (runOfflineOnly) {
          "অফলাইন বাংলা বানান, যুক্তাক্ষর, কার-ফলা ও বিরামচিহ্ন পরীক্ষা চলছে…"
        } else {
          "স্থানীয় বাংলা বানান ও বিরামচিহ্ন পরীক্ষা এবং খণ্ড বিভাজন চলছে…"
        },
        elapsedSeconds = elapsedSec()
      )
    )

    val localIssues = BengaliLocalAnalyzer.runLocalProofreadingChecks(text, category)
    val chunks = ManuscriptChunker.chunk(
      text = text,
      category = category,
      maxCharsPerChunk = when (mode) {
        ProofreadingMode.QUICK -> 2600
        ProofreadingMode.STANDARD -> if (category == ManuscriptCategory.POETRY) 1700 else 2200
        ProofreadingMode.DEEP -> if (category == ManuscriptCategory.POETRY) 1400 else 1800
      }
    )

    // True Offline Mode: return complete local Bengali spelling & punctuation proofreading report immediately
    if (runOfflineOnly) {
      return@withContext finalizeOfflineProofreadingReport(
        manuscriptId = manuscriptId,
        manuscriptTitle = manuscriptTitle,
        text = text,
        category = category,
        mode = mode,
        localIssues = localIssues,
        totalChunks = chunks.size.coerceAtLeast(1),
        contentHash = contentHash,
        startTime = startTime,
        isDeviceOnline = isOnline,
        reasonPrefix = if (forceOffline) "অফলাইন বানান পরীক্ষা সম্পন্ন" else "ইন্টারনেট না থাকায় অফলাইন বানান পরীক্ষা সম্পন্ন",
        onProgress = onProgress
      )
    }

    // Emit immediate local partial issues if found so user sees real instant feedback
    if (localIssues.isNotEmpty() && chunks.size > 1) {
      onPartialResult(
        ProofreadingReport(
          category = category.name,
          mode = mode.name,
          overallSummary = "প্রাথমিক স্থানীয় পরীক্ষায় ${BengaliUtils.toBengaliDigits(localIssues.size)}টি বিষয় শনাক্ত হয়েছে; বিস্তারিত AI বিশ্লেষণ চলছে…",
          issues = localIssues,
          suggestedFullText = if (text.length <= 20_000) applyIssuesSequentially(text, localIssues) else text,
          durationMs = System.currentTimeMillis() - startTime,
          chunksProcessed = 0,
          totalChunks = chunks.size,
          isOfflineMode = false
        )
      )
    }

    onProgress(
      ProgressState(
        stage = ProcessingStage.LINGUISTIC_ANALYSIS,
        detailText = "মোট ${BengaliUtils.toBengaliDigits(chunks.size)}টি খণ্ডে বাংলা ভাষাতাত্ত্বিক বিশ্লেষণ চলছে…",
        completedChunks = 0,
        totalChunks = chunks.size,
        elapsedSeconds = elapsedSec()
      )
    )

    val model = when (mode) {
      ProofreadingMode.QUICK -> GeminiApiClient.MODEL_FAST_PROOFREAD
      ProofreadingMode.STANDARD -> GeminiApiClient.MODEL_PRIMARY
      ProofreadingMode.DEEP -> GeminiApiClient.MODEL_PRIMARY
    }
    val thinkingLevel = when (mode) {
      ProofreadingMode.QUICK -> "MINIMAL"
      ProofreadingMode.STANDARD -> "LOW"
      ProofreadingMode.DEEP -> "MEDIUM"
    }

    // Stage 3: Bounded parallel AI chunk analysis (preserves exact order, zero skipped sections)
    val semaphore = Semaphore(3)
    val completedCounter = AtomicInteger(0)
    val partialIssuesLock = Any()
    val accumulatedAiIssues = mutableListOf<ProofreadingIssue>()

    val chunkResults = try {
      coroutineScope {
        chunks.map { chunk ->
          async(Dispatchers.IO) {
            semaphore.withPermit {
              val result = analyzeProofreadingChunk(
                chunk = chunk,
                category = category,
                mode = mode,
                model = model,
                thinkingLevel = thinkingLevel
              )
              val done = completedCounter.incrementAndGet()
              synchronized(partialIssuesLock) {
                accumulatedAiIssues.addAll(result.issues)
                if (chunks.size > 1 && done < chunks.size) {
                  val mergedSoFar = mergeIssues(localIssues, accumulatedAiIssues)
                  onPartialResult(
                    ProofreadingReport(
                      category = category.name,
                      mode = mode.name,
                      overallSummary = "${BengaliUtils.toBengaliDigits(done)}/${BengaliUtils.toBengaliDigits(chunks.size)} খণ্ডের বিশ্লেষণ সম্পন্ন হয়েছে…",
                      issues = mergedSoFar,
                      suggestedFullText = if (text.length <= 20_000) applyIssuesSequentially(text, mergedSoFar) else text,
                      durationMs = System.currentTimeMillis() - startTime,
                      chunksProcessed = done,
                      totalChunks = chunks.size,
                      isOfflineMode = false
                    )
                  )
                }
              }
              onProgress(
                ProgressState(
                  stage = if (done < chunks.size) ProcessingStage.LINGUISTIC_ANALYSIS else ProcessingStage.CORRECTION_GENERATION,
                  detailText = "খণ্ড ${BengaliUtils.toBengaliDigits(done)} / ${BengaliUtils.toBengaliDigits(chunks.size)} বিশ্লেষিত হয়েছে",
                  completedChunks = done,
                  totalChunks = chunks.size,
                  elapsedSeconds = elapsedSec(),
                  isPartialReady = done > 0
                )
              )
              chunk.index to result
            }
          }
        }.awaitAll()
      }.sortedBy { it.first }.map { it.second }
    } catch (e: AiServiceException) {
      if (e.isNetworkError || e.isServerUnavailable) {
        val reason = if (e.isNetworkError) {
          "নেটওয়ার্ক বিচ্ছিন্ন হওয়ায় অফলাইন বানান পরীক্ষা সম্পন্ন"
        } else {
          "অনলাইন AI সার্ভার সংযুক্ত না থাকায় স্থানীয় বাংলা ইঞ্জিনে প্রুফরিডিং সম্পন্ন"
        }
        return@withContext finalizeOfflineProofreadingReport(
          manuscriptId = manuscriptId,
          manuscriptTitle = manuscriptTitle,
          text = text,
          category = category,
          mode = mode,
          localIssues = localIssues,
          totalChunks = chunks.size.coerceAtLeast(1),
          contentHash = contentHash,
          startTime = startTime,
          isDeviceOnline = isOnline && !e.isNetworkError,
          reasonPrefix = reason,
          onProgress = onProgress
        )
      }
      throw e
    }

    // Stage 4: Merge in original order & synthesize final report
    onProgress(
      ProgressState(
        stage = ProcessingStage.FINAL_PREPARATION,
        detailText = "সমস্ত খণ্ডের সংশোধনী ও ব্যাখ্যা একত্রিত করা হচ্ছে…",
        completedChunks = chunks.size,
        totalChunks = chunks.size,
        elapsedSeconds = elapsedSec()
      )
    )

    val allAiIssues = chunkResults.flatMap { it.issues }
    val mergedIssues = mergeIssues(localIssues, allAiIssues)
    val suggestedText = if (chunkResults.all { it.correctedText.isNotBlank() } && chunkResults.size == chunks.size) {
      val aiJoined = chunkResults.joinToString("\n\n") { it.correctedText.trim() }
      val originalLines = text.trim().lines().size
      val aiLines = aiJoined.trim().lines().size
      val poetryStructurePreserved = category != ManuscriptCategory.POETRY ||
        !poetryProtectionStrict ||
        kotlin.math.abs(originalLines - aiLines) <= 1
      // Safety verification: never return drastically truncated text or altered poetic line structure
      if (aiJoined.length >= (text.length * 0.72).toInt() && poetryStructurePreserved) {
        aiJoined
      } else {
        applyIssuesSequentially(text, mergedIssues)
      }
    } else {
      applyIssuesSequentially(text, mergedIssues)
    }

    val combinedSummary = chunkResults
      .map { it.summary.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .joinToString(" ")
      .ifBlank {
        if (mergedIssues.isEmpty()) {
          "পান্ডুলিপিটি পরীক্ষা করা হয়েছে—কোনো উল্লেখযোগ্য বানান বা ব্যাকরণগত ত্রুটি পাওয়া যায়নি।"
        } else {
          "মোট ${BengaliUtils.toBengaliDigits(mergedIssues.size)}টি সংশোধনী ও সম্পাদকীয় পরামর্শ শনাক্ত হয়েছে।"
        }
      }

    val poetryNote = if (category == ManuscriptCategory.POETRY) {
      chunkResults.firstNotNullOfOrNull { it.poetryNote.takeIf { n -> n.isNotBlank() } }
        ?: "কবিতার ছন্দ, অন্ত্যমিল, চিত্রকল্প, উপমা, রূপক ও পঙ্ক্তির বিন্যাস সম্পূর্ণ অক্ষুণ্ণ রেখে কেবল প্রয়োজনীয় সংশোধনীর পরামর্শ দেওয়া হয়েছে।"
    } else {
      "লেখকের নিজস্ব কণ্ঠস্বর, সংলাপ, চরিত্রের নাম ও সাহিত্যিক ভঙ্গি বজায় রেখে প্রুফরিডিং সম্পন্ন হয়েছে।"
    }

    val duration = System.currentTimeMillis() - startTime
    val report = ProofreadingReport(
      category = category.name,
      mode = mode.name,
      overallSummary = combinedSummary,
      poetryProtectionSummary = poetryNote,
      issues = mergedIssues,
      suggestedFullText = suggestedText,
      durationMs = duration,
      fromCache = false,
      chunksProcessed = chunks.size,
      totalChunks = chunks.size,
      isOfflineMode = false
    )

    // Persist in local private history & cache
    historyDao.insert(
      AnalysisHistoryEntity(
        manuscriptId = manuscriptId,
        manuscriptTitle = manuscriptTitle.ifBlank { "শিরোনামহীন পান্ডুলিপি" },
        analysisType = AnalysisType.PROOFREADING.name,
        mode = cacheModeKey,
        contentHash = contentHash,
        summary = "${mode.bengaliLabel} • ${BengaliUtils.toBengaliDigits(mergedIssues.size)}টি পরামর্শ",
        resultJson = proofreadingAdapter.toJson(report),
        durationMs = duration
      )
    )

    report
  }

  private suspend fun finalizeOfflineProofreadingReport(
    manuscriptId: Long?,
    manuscriptTitle: String,
    text: String,
    category: ManuscriptCategory,
    mode: ProofreadingMode,
    localIssues: List<ProofreadingIssue>,
    totalChunks: Int,
    contentHash: String,
    startTime: Long,
    isDeviceOnline: Boolean,
    reasonPrefix: String,
    onProgress: (ProgressState) -> Unit
  ): ProofreadingReport {
    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000L).toInt()
    onProgress(
      ProgressState(
        stage = ProcessingStage.FINAL_PREPARATION,
        detailText = "স্থানীয় বানান পরীক্ষার প্রতিবেদন সাজানো হচ্ছে…",
        completedChunks = totalChunks,
        totalChunks = totalChunks,
        elapsedSeconds = elapsedSec
      )
    )

    val suggestedText = applyIssuesSequentially(text, localIssues)
    val nextStepHint = if (isDeviceOnline) {
      "গভীর ক্লাউড AI প্রুফরিডিংয়ের জন্য ‘সেটিংস → অনলাইন AI সার্ভার সংযোগ’-এ পাবলিক HTTPS সার্ভার URL যুক্ত করুন।"
    } else {
      "ইন্টারনেট যুক্ত হলে আরও গভীর AI প্রুফরিডিং করতে পারবেন।"
    }
    val summary = if (localIssues.isEmpty()) {
      "$reasonPrefix—স্থানীয় বাংলা অভিধান, কার-ফলা ও বিরামচিহ্ন পরীক্ষায় কোনো ত্রুটি পাওয়া যায়নি। $nextStepHint"
    } else {
      "$reasonPrefix—মোট ${BengaliUtils.toBengaliDigits(localIssues.size)}টি বানান, কার-ফলা, যুক্তাক্ষর বা বিরামচিহ্ন সংশোধনী শনাক্ত হয়েছে।"
    }
    val poetryNote = if (category == ManuscriptCategory.POETRY) {
      "কবিতা সুরক্ষা সক্রিয় (অফলাইন): কবিতার ছন্দ, স্তবক, পঙ্ক্তির বিন্যাস, অন্ত্যমিল ও ইচ্ছাকৃত পুনরুক্তি সম্পূর্ণ অক্ষুণ্ণ রাখা হয়েছে।"
    } else {
      "অফলাইন স্থানীয় বাংলা অভিধান ও ব্যাকরণ নিয়ম প্রয়োগ করা হয়েছে।"
    }

    val duration = System.currentTimeMillis() - startTime
    val report = ProofreadingReport(
      category = category.name,
      mode = mode.name,
      overallSummary = summary,
      poetryProtectionSummary = poetryNote,
      issues = localIssues,
      suggestedFullText = suggestedText,
      durationMs = duration,
      fromCache = false,
      chunksProcessed = totalChunks,
      totalChunks = totalChunks,
      isOfflineMode = true
    )

    historyDao.insert(
      AnalysisHistoryEntity(
        manuscriptId = manuscriptId,
        manuscriptTitle = manuscriptTitle.ifBlank { "শিরোনামহীন পান্ডুলিপি" },
        analysisType = AnalysisType.PROOFREADING.name,
        mode = "${category.name}_${mode.name}_OFFLINE",
        contentHash = contentHash,
        summary = "অফলাইন পরীক্ষা • ${BengaliUtils.toBengaliDigits(localIssues.size)}টি পরামর্শ",
        resultJson = proofreadingAdapter.toJson(report),
        durationMs = duration
      )
    )

    return report
  }

  private data class ChunkProofreadResult(
    val summary: String,
    val poetryNote: String,
    val correctedText: String,
    val issues: List<ProofreadingIssue>
  )

  private suspend fun analyzeProofreadingChunk(
    chunk: ManuscriptChunk,
    category: ManuscriptCategory,
    mode: ProofreadingMode,
    model: String,
    thinkingLevel: String
  ): ChunkProofreadResult {
    val protectionRules = if (category == ManuscriptCategory.POETRY) {
      """
      কবিতা সুরক্ষা নীতি (অবশ্যপালনীয়):
      - কবিতার ছন্দ, অন্ত্যমিল, চিত্রকল্প, উপমা, রূপক, কবির নিজস্ব শব্দচয়ন, পঙ্ক্তির বিন্যাস, বিরতি ও আবেগ কখনোই নষ্ট করবেন না।
      - প্রচলিত ব্যাকরণের অজুহাতে কবিতা পুনর্লিখন করবেন না।
      - কেবল প্রকৃত বানান ভুল, যুক্তাক্ষর ভুল, কার/ফলা ভুল, টাইপিং ভুল বা অনিচ্ছাকৃত বিরামচিহ্ন ত্রুটি চিহ্নিত করুন।
      - কবিতার শৈল্পিক বৈচিত্র্য থাকলে তা ব্যাখ্যাসহ পরামর্শ আকারে দিন, মূল পঙ্ক্তি বদলে দেবেন না।
      """.trimIndent()
    } else {
      """
      গল্প, উপন্যাস ও গদ্য সুরক্ষা নীতি (অবশ্যপালনীয়):
      - লেখকের নিজস্ব কণ্ঠস্বর, বর্ণনাভঙ্গি, সংলাপ, চরিত্রের নাম ও সাহিত্যিক স্টাইল সম্পূর্ণ অক্ষুণ্ণ রাখুন।
      - অপ্রয়োজনীয় পুনর্লিখন এড়িয়ে চলুন; কেবল প্রকৃত ত্রুটি সংশোধন ও প্রাসঙ্গিক সম্পাদকীয় পরামর্শ দিন।
      """.trimIndent()
    }

    val modeScope = when (mode) {
      ProofreadingMode.QUICK ->
        "মোড: দ্রুত পরীক্ষা। কেবল স্পষ্ট বানান ভুল, যুক্তাক্ষর ভুল, কার-ফলা, বিরামচিহ্ন, টাইপিং ভুল ও মৌলিক ব্যাকরণ ভুল চিহ্নিত করুন।"
      ProofreadingMode.STANDARD ->
        "মোড: মানসম্মত পরীক্ষা। বানান ভুল, যুক্তাক্ষর, কার ও ফলা, বিরামচিহ্ন, ব্যাকরণ, বাক্য গঠন, শব্দের ভুল প্রয়োগ, পুনরাবৃত্ত শব্দ, টাইপিং ভুল ও অসংলগ্ন বাক্য পরীক্ষা করুন।"
      ProofreadingMode.DEEP ->
        "মোড: গভীর সম্পাদনা পর্যালোচনা। সম্পূর্ণ প্রুফরিডিংয়ের পাশাপাশি ভাষার স্বাভাবিকতা, অনুচ্ছেদের অসামঞ্জস্য, সাহিত্যিক ধারাবাহিকতা, অপ্রয়োজনীয় শব্দ এবং সূক্ষ্ম শব্দচয়ন পরামর্শ প্রদান করুন।"
    }

    val systemInstruction = """
      আপনি ‘শব্দশুদ্ধি ১.০’-এর প্রধান বাংলা প্রুফরিডার ও সাহিত্য সম্পাদক। বাংলা একাডেমি ও পশ্চিমবঙ্গ বাংলা আকাদেমির প্রমিত বাংলা বানান ও ব্যাকরণে আপনার গভীর পাণ্ডিত্য রয়েছে।
      $protectionRules
      $modeScope
      সমস্ত ব্যাখ্যা ও মন্তব্য পরিষ্কার, শুদ্ধ বাংলায় লিখুন।
      অবশ্যই নিচের JSON কাঠামোতে উত্তর দিন:
      {
        "summary": "সংক্ষিপ্ত সামগ্রিক মূল্যায়ন (১-২ বাক্যে বাংলায়)",
        "poetryNote": "কবিতা বা সাহিত্যিক শৈলী কীভাবে সুরক্ষিত রাখা হয়েছে তার সংক্ষিপ্ত নোট",
        "correctedText": "প্রস্তাবিত সংশোধিত সম্পূর্ণ অংশ (কোনো পঙ্ক্তি বা অনুচ্ছেদ বাদ দেবেন না)",
        "issues": [
          {
            "originalText": "ভুল বা সংশোধনযোগ্য অংশ (মূল পাঠ্যের হুবহু অংশ)",
            "suggestedText": "সংশোধিত রূপ",
            "category": "বানান ভুল | যুক্তাক্ষরের ভুল | কার ও ফলা | বিরামচিহ্নের ভুল | ব্যাকরণগত ভুল | বাক্য গঠনের সমস্যা | শব্দের ভুল প্রয়োগ | অপ্রয়োজনীয় শব্দ | পুনরাবৃত্ত শব্দ | টাইপিং ভুল | অসংলগ্ন বাক্য | অনুচ্ছেদের অসামঞ্জস্য | ভাষার স্বাভাবিকতা",
            "severity": "জরুরি | গুরুত্বপূর্ণ | পরামর্শ",
            "explanation": "স্পষ্ট বাংলা ব্যাখ্যা",
            "relativeParagraphIndex": 0,
            "poetryPreservationNote": "কবিতা হলে ছন্দ/অর্থ সুরক্ষার নোট, নাহলে খালি"
          }
        ]
      }
    """.trimIndent()

    val contextHint = if (chunk.precedingContext.isNotBlank()) {
      "পূর্ববর্তী অংশের প্রাসঙ্গিক সূত্র (কেবল বোঝার জন্য, এটি সংশোধন করবেন না): ${chunk.precedingContext}\n\n"
    } else ""

    val userPrompt = "${contextHint}নিম্নলিখিত বাংলা ${category.bengaliLabel} অংশটি প্রুফরিড করুন:\n\n${chunk.text}"

    val rawJson = geminiClient.generateStructuredJson(
      model = model,
      systemInstruction = systemInstruction,
      userPrompt = userPrompt,
      thinkingLevel = thinkingLevel
    )

    return parseChunkProofreadJson(rawJson, chunk)
  }

  private fun parseChunkProofreadJson(rawJson: String, chunk: ManuscriptChunk): ChunkProofreadResult {
    val root = runCatching { JSONObject(rawJson) }.getOrElse {
      throw AiServiceException("প্রুফরিডিং ফলাফল পড়া সম্ভব হয়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।", cause = it)
    }
    val summary = root.optString("summary", "")
    val poetryNote = root.optString("poetryNote", "")
    val correctedText = root.optString("correctedText", chunk.text)
    val issuesArr = root.optJSONArray("issues") ?: JSONArray()

    val issues = mutableListOf<ProofreadingIssue>()
    for (i in 0 until issuesArr.length()) {
      val obj = issuesArr.optJSONObject(i) ?: continue
      val orig = obj.optString("originalText", "").trim()
      val sugg = obj.optString("suggestedText", "").trim()
      if (orig.isEmpty() || orig == sugg) continue
      issues.add(
        ProofreadingIssue(
          id = "ai_c${chunk.index}_${i + 1}",
          originalText = orig,
          suggestedText = sugg,
          category = obj.optString("category", "বানান ভুল").ifBlank { "বানান ভুল" },
          severity = obj.optString("severity", "গুরুত্বপূর্ণ").ifBlank { "গুরুত্বপূর্ণ" },
          explanation = obj.optString("explanation", "প্রমিত বাংলা অনুযায়ী সংশোধনের পরামর্শ।"),
          paragraphIndex = chunk.startParagraphIndex + obj.optInt("relativeParagraphIndex", 0),
          poetryPreservationNote = obj.optString("poetryPreservationNote", ""),
          source = "AI"
        )
      )
    }

    return ChunkProofreadResult(
      summary = summary,
      poetryNote = poetryNote,
      correctedText = correctedText,
      issues = issues
    )
  }

  private fun mergeIssues(
    localIssues: List<ProofreadingIssue>,
    aiIssues: List<ProofreadingIssue>
  ): List<ProofreadingIssue> {
    val combined = mutableListOf<ProofreadingIssue>()
    val seenKeys = mutableSetOf<String>()
    // Prioritize AI issues with richer context, then include any local issues not already covered
    for (issue in aiIssues) {
      val key = "${issue.originalText.trim()}|${issue.suggestedText.trim()}"
      if (seenKeys.add(key)) combined.add(issue)
    }
    for (issue in localIssues) {
      val key = "${issue.originalText.trim()}|${issue.suggestedText.trim()}"
      if (seenKeys.add(key)) combined.add(issue)
    }
    return combined.sortedBy { it.paragraphIndex }
  }

  fun applyIssuesSequentially(originalText: String, selectedIssues: List<ProofreadingIssue>): String {
    var result = originalText
    for (issue in selectedIssues) {
      if (issue.originalText.isNotBlank() && result.contains(issue.originalText)) {
        result = BengaliLocalAnalyzer.replaceFirstSafely(result, issue.originalText, issue.suggestedText)
      }
    }
    return result
  }

  // =========================================================================
  // 2. AI WRITING DETECTION ENGINE (PROBABILISTIC & SCIENTIFICALLY HONEST)
  // =========================================================================

  suspend fun detectAiWriting(
    manuscriptId: Long?,
    manuscriptTitle: String,
    text: String,
    category: ManuscriptCategory,
    useCache: Boolean = true,
    onProgress: (ProgressState) -> Unit
  ): AiDetectionReport = withContext(Dispatchers.Default) {
    val startTime = System.currentTimeMillis()
    val elapsedSec = { ((System.currentTimeMillis() - startTime) / 1000L).toInt() }

    onProgress(
      ProgressState(
        stage = ProcessingStage.RECEIVED,
        detailText = "AI লেখা শনাক্তকরণের জন্য পান্ডুলিপি গৃহীত হয়েছে।",
        elapsedSeconds = elapsedSec()
      )
    )

    val isDetailed = text.length > 1500
    val analysisModeLabel = if (isDetailed) "বিস্তারিত বিশ্লেষণ" else "দ্রুত বিশ্লেষণ"
    val cacheModeKey = "${category.name}_${if (isDetailed) "DETAILED" else "FAST"}"
    val contentHash = BengaliUtils.sha256(text)

    if (useCache) {
      val cached = historyDao.findCached(contentHash, AnalysisType.AI_DETECTION.name, cacheModeKey)
      if (cached != null) {
        val parsed = runCatching { aiDetectionAdapter.fromJson(cached.resultJson) }.getOrNull()
        if (parsed != null) {
          return@withContext parsed.copy(fromCache = true, durationMs = System.currentTimeMillis() - startTime)
        }
      }
    }

    onProgress(
      ProgressState(
        stage = ProcessingStage.LINGUISTIC_ANALYSIS,
        detailText = "বাংলা ভাষাতাত্ত্বিক বৈশিষ্ট্য ও ১০টি সূচক বিশ্লেষণ চলছে ($analysisModeLabel)…",
        elapsedSeconds = elapsedSec()
      )
    )

    // For very long manuscripts, sample beginning, middle, and end segments fairly without skipping context
    val textForAnalysis = if (text.length <= 7500) {
      text
    } else {
      val partLen = 2400
      val midStart = (text.length / 2 - partLen / 2).coerceAtLeast(partLen)
      val head = text.take(partLen)
      val mid = text.substring(midStart, (midStart + partLen).coerceAtMost(text.length))
      val tail = text.takeLast(partLen)
      "[শুরুর অংশ]\n$head\n\n[মধ্যবর্তী অংশ]\n$mid\n\n[শেষ অংশ]\n$tail"
    }

    val systemInstruction = """
      আপনি একজন বাংলা কম্পিউটেশনাল লিঙ্গুইস্টিকস ও স্টাইলোমেট্রি গবেষক। আপনাকে প্রদত্ত বাংলা পান্ডুলিপিতে AI-সহায়তাপ্রাপ্ত বা যান্ত্রিক লেখার ভাষাতাত্ত্বিক বৈশিষ্ট্য বনাম স্বাভাবিক মানবিক বাংলা লেখার বৈশিষ্ট্য মূল্যায়ন করতে হবে।

      অবশ্যপালনীয় নির্ভুলতা ও সততার নীতি:
      ১. AI শনাক্তকরণ সম্ভাব্যতামূলক, কখনোই ১০০% নিশ্চিত নয়।
      ২. কেবল পরিশীলিত ব্যাকরণ, সাধু/প্রমিত ভাষা বা পুনরাবৃত্ত শব্দের কারণে কোনো লেখাকে AI-লিখিত বলবেন না। বাংলা সাহিত্যিক গদ্য ও কবিতায় ছন্দ, পুনরুক্তি ও আনুষ্ঠানিক ভাষা অত্যন্ত স্বাভাবিক।
      ৩. কোনো অবৈজ্ঞানিক বা ভুয়া নিখুঁত শতাংশ দাবি করবেন না। পরিবর্তে স্পষ্ট গুণগত শ্রেণিবিন্যাস ও যুক্তিসঙ্গত বর্ণনামূলক পরিসর দিন।
      ৪. বাংলা ভাষার নিজস্ব বৈশিষ্ট্য (ক্রিয়াপদের বৈচিত্র্য, বাগধারা, অনুবাদসুলভ বাক্যগঠন, যান্ত্রিক সংযোগশব্দ যেমন 'সর্বোপরি', 'পরিশেষে বলা যায়', 'অন্যদিকে এটি লক্ষণীয়', বাক্যের দৈর্ঘ্যের সমরূপতা, আবেগের সূক্ষ্মতা) বিশ্লেষণ করুন।
      ৫. লেখা খুব ছোট হলে (< ৪০ শব্দ) বা অস্পষ্ট হলে "UNCERTAIN" শ্রেণিবিন্যাস দিন।

      নিচের ১০টি মাত্রা অবশ্যই বিশ্লেষণ করুন:
      ১. বাক্য গঠনের পুনরাবৃত্তি
      ২. বাক্যের দৈর্ঘ্য ও ছন্দের অস্বাভাবিক সমরূপতা
      ৩. শব্দভান্ডারের পুনরাবৃত্তি
      ৪. গতানুগতিক ও অনুবাদসুলভ অভিব্যক্তি
      ৫. অনুমেয় সংযোগশব্দ ও রূপান্তর
      ৬. অনুচ্ছেদের কাঠামোগত পুনরাবৃত্তি
      ৭. শৈলীগত ধারাবাহিকতা
      ৮. অস্বাভাবিক ভাষাতাত্ত্বিক প্যাটার্ন
      ৯. প্রাসঙ্গিক সামঞ্জস্য ও গভীরতা
      ১০. মানবিক লেখার স্বাভাবিক বৈচিত্র্য

      অবশ্যই নিচের JSON কাঠামোতে বাংলায় উত্তর দিন:
      {
        "classification": "LIKELY_AI | LIKELY_HUMAN | MIXED | UNCERTAIN",
        "detectionIndicatorLevel": "উচ্চ যান্ত্রিক প্রবণতা | মাঝারি মিশ্র প্রবণতা | নিম্ন যান্ত্রিক প্রবণতা (মানবিক) | অপর্যাপ্ত তথ্য",
        "aiLikelihoodRange": "যেমন: 'কম প্রবণতা (যান্ত্রিক লক্ষণ সামান্য)' বা 'মাঝারি-উচ্চ প্রবণতা (একাধিক যান্ত্রিক প্যাটার্ন দৃশ্যমান)' — কখনো ১০০% দাবি নয়",
        "humanLikelihoodRange": "যেমন: 'বেশি (স্বাভাবিক বাংলা প্রকাশভঙ্গি ও বৈচিত্র্য স্পষ্ট)'",
        "confidenceLevel": "LOW | MEDIUM | HIGH",
        "explanation": "বিস্তারিত, ভারসাম্যপূর্ণ বাংলা বিশ্লেষণ (৩-৫ বাক্য)",
        "overallCharacteristics": ["বৈশিষ্ট্য ১", "বৈশিষ্ট্য ২", "বৈশিষ্ট্য ৩"],
        "linguisticDimensions": [
          { "dimensionName": "মাত্রার নাম", "observation": "সংক্ষিপ্ত বাংলা পর্যবেক্ষণ", "tendency": "মানবিক | মিশ্র | যান্ত্রিক" }
        ],
        "suspiciousSentences": [
          { "sentence": "মূল পাঠ্যের নির্দিষ্ট সন্দেহজনক বাক্য", "reason": "কেন এটি যান্ত্রিক বা গতানুগতিক মনে হয়েছে", "signalStrength": "বেশি | মাঝারি | সামান্য" }
        ],
        "paragraphAnalysis": [
          { "paragraphIndex": 0, "excerpt": "অনুচ্ছেদের প্রথম কয়েকটি শব্দ…", "assessment": "মানবিক বৈশিষ্ট্য | মিশ্র বৈশিষ্ট্য | যান্ত্রিক বৈশিষ্ট্য", "observation": "সংক্ষিপ্ত কারণ" }
        ]
      }
    """.trimIndent()

    var usedLocalFallback = false
    val report = try {
      val rawJson = geminiClient.generateStructuredJson(
        model = GeminiApiClient.MODEL_PRIMARY,
        systemInstruction = systemInstruction,
        userPrompt = "পান্ডুলিপির ধরন: ${category.bengaliLabel}\n\nবিশ্লেষণের জন্য বাংলা পাঠ্য:\n$textForAnalysis",
        thinkingLevel = if (isDetailed) "MEDIUM" else "LOW"
      )

      onProgress(
        ProgressState(
          stage = ProcessingStage.FINAL_PREPARATION,
          detailText = "শনাক্তকরণ প্রতিবেদন ও সতর্কবার্তা প্রস্তুত করা হচ্ছে…",
          elapsedSeconds = elapsedSec()
        )
      )

      parseAiDetectionJson(
        rawJson = rawJson,
        analysisModeLabel = analysisModeLabel,
        durationMs = System.currentTimeMillis() - startTime
      )
    } catch (e: AiServiceException) {
      if (e.isNetworkError || e.isServerUnavailable) {
        usedLocalFallback = true
        onProgress(
          ProgressState(
            stage = ProcessingStage.FINAL_PREPARATION,
            detailText = "স্থানীয় বাংলা স্টাইলোমেট্রি বিশ্লেষণ সম্পন্ন হচ্ছে…",
            elapsedSeconds = elapsedSec()
          )
        )
        BengaliLocalAnalyzer.runLocalAiDetection(
          text = text,
          category = category,
          disclaimer = MANDATORY_AI_DETECTION_DISCLAIMER,
          durationMs = System.currentTimeMillis() - startTime
        )
      } else {
        throw e
      }
    }

    historyDao.insert(
      AnalysisHistoryEntity(
        manuscriptId = manuscriptId,
        manuscriptTitle = manuscriptTitle.ifBlank { "শিরোনামহীন পান্ডুলিপি" },
        analysisType = AnalysisType.AI_DETECTION.name,
        mode = if (usedLocalFallback) "${cacheModeKey}_LOCAL" else cacheModeKey,
        contentHash = contentHash,
        summary = "${AiDetectionLabel.fromName(report.classification).bengaliLabel} • ${ConfidenceLevel.fromName(report.confidenceLevel).bengaliLabel}" +
          if (usedLocalFallback) " (স্থানীয়)" else "",
        resultJson = aiDetectionAdapter.toJson(report),
        durationMs = report.durationMs
      )
    )

    report
  }

  fun parseAiDetectionJson(
    rawJson: String,
    analysisModeLabel: String,
    durationMs: Long
  ): AiDetectionReport {
    val root = runCatching { JSONObject(rawJson) }.getOrElse {
      throw AiServiceException("AI শনাক্তকরণ প্রতিবেদন পড়া সম্ভব হয়নি।", cause = it)
    }

    val classification = AiDetectionLabel.fromName(root.optString("classification", "UNCERTAIN")).name
    val confidence = ConfidenceLevel.fromName(root.optString("confidenceLevel", "LOW")).name

    val characteristics = mutableListOf<String>()
    val charArr = root.optJSONArray("overallCharacteristics") ?: JSONArray()
    for (i in 0 until charArr.length()) {
      val s = charArr.optString(i, "").trim()
      if (s.isNotEmpty()) characteristics.add(s)
    }

    val dimensions = mutableListOf<LinguisticDimensionScore>()
    val dimArr = root.optJSONArray("linguisticDimensions") ?: JSONArray()
    for (i in 0 until dimArr.length()) {
      val o = dimArr.optJSONObject(i) ?: continue
      dimensions.add(
        LinguisticDimensionScore(
          dimensionName = o.optString("dimensionName", ""),
          observation = o.optString("observation", ""),
          tendency = o.optString("tendency", "মিশ্র")
        )
      )
    }

    val suspicious = mutableListOf<SuspiciousSentence>()
    val suspArr = root.optJSONArray("suspiciousSentences") ?: JSONArray()
    for (i in 0 until suspArr.length()) {
      val o = suspArr.optJSONObject(i) ?: continue
      val sentence = o.optString("sentence", "").trim()
      if (sentence.isNotEmpty()) {
        suspicious.add(
          SuspiciousSentence(
            sentence = sentence,
            reason = o.optString("reason", ""),
            signalStrength = o.optString("signalStrength", "সামান্য")
          )
        )
      }
    }

    val paragraphs = mutableListOf<ParagraphAiNote>()
    val paraArr = root.optJSONArray("paragraphAnalysis") ?: JSONArray()
    for (i in 0 until paraArr.length()) {
      val o = paraArr.optJSONObject(i) ?: continue
      paragraphs.add(
        ParagraphAiNote(
          paragraphIndex = o.optInt("paragraphIndex", i),
          excerpt = o.optString("excerpt", ""),
          assessment = o.optString("assessment", "মিশ্র বৈশিষ্ট্য"),
          observation = o.optString("observation", "")
        )
      )
    }

    return AiDetectionReport(
      classification = classification,
      detectionIndicatorLevel = root.optString("detectionIndicatorLevel", "মাঝারি মিশ্র প্রবণতা"),
      aiLikelihoodRange = root.optString("aiLikelihoodRange", "সম্ভাব্যতামূলক মূল্যায়ন"),
      humanLikelihoodRange = root.optString("humanLikelihoodRange", "সম্ভাব্যতামূলক মূল্যায়ন"),
      confidenceLevel = confidence,
      verifiedAuthorshipNote = "অযাচাইকৃত — কোনো স্বয়ংক্রিয় পদ্ধতি প্রকৃত লেখকত্ব প্রমাণ করতে পারে না।",
      analysisMode = analysisModeLabel,
      explanation = root.optString("explanation", "ভাষাতাত্ত্বিক বৈশিষ্ট্য বিশ্লেষণ সম্পন্ন হয়েছে।"),
      overallCharacteristics = characteristics,
      linguisticDimensions = dimensions,
      suspiciousSentences = suspicious,
      paragraphAnalysis = paragraphs,
      uncertaintyWarning = MANDATORY_AI_DETECTION_DISCLAIMER,
      durationMs = durationMs,
      fromCache = false
    )
  }

  // =========================================================================
  // 3. HUMAN WRITING REFINEMENT ENGINE ("মানবিক ভাষায় রূপান্তর")
  // =========================================================================

  suspend fun refineHumanLanguage(
    manuscriptId: Long?,
    manuscriptTitle: String,
    text: String,
    category: ManuscriptCategory,
    mode: RefinementMode,
    useCache: Boolean = true,
    poetryProtectionStrict: Boolean = true,
    onProgress: (ProgressState) -> Unit
  ): RefinementReport = withContext(Dispatchers.Default) {
    val startTime = System.currentTimeMillis()
    val elapsedSec = { ((System.currentTimeMillis() - startTime) / 1000L).toInt() }

    onProgress(
      ProgressState(
        stage = ProcessingStage.RECEIVED,
        detailText = "মানবিক ভাষায় রূপান্তরের জন্য পান্ডুলিপি গৃহীত হয়েছে।",
        elapsedSeconds = elapsedSec()
      )
    )

    val cacheModeKey = "${category.name}_${mode.name}"
    val contentHash = BengaliUtils.sha256(text)

    if (useCache) {
      val cached = historyDao.findCached(contentHash, AnalysisType.HUMAN_REFINEMENT.name, cacheModeKey)
      if (cached != null) {
        val parsed = runCatching { refinementAdapter.fromJson(cached.resultJson) }.getOrNull()
        if (parsed != null) {
          return@withContext parsed.copy(fromCache = true, durationMs = System.currentTimeMillis() - startTime)
        }
      }
    }

    val chunks = ManuscriptChunker.chunk(
      text = text,
      category = category,
      maxCharsPerChunk = if (category == ManuscriptCategory.POETRY) 1500 else 2000
    )

    onProgress(
      ProgressState(
        stage = ProcessingStage.LINGUISTIC_ANALYSIS,
        detailText = "${mode.bengaliLabel} অনুযায়ী ভাষা পরিমার্জন চলছে (${BengaliUtils.toBengaliDigits(chunks.size)} খণ্ড)…",
        completedChunks = 0,
        totalChunks = chunks.size,
        elapsedSeconds = elapsedSec()
      )
    )

    val semaphore = Semaphore(3)
    val doneCounter = AtomicInteger(0)

    val chunkResults = try {
      coroutineScope {
        chunks.map { chunk ->
          async(Dispatchers.IO) {
            semaphore.withPermit {
              val res = refineChunk(chunk, category, mode)
              val done = doneCounter.incrementAndGet()
              onProgress(
                ProgressState(
                  stage = ProcessingStage.CORRECTION_GENERATION,
                  detailText = "খণ্ড ${BengaliUtils.toBengaliDigits(done)} / ${BengaliUtils.toBengaliDigits(chunks.size)} পরিমার্জিত হয়েছে",
                  completedChunks = done,
                  totalChunks = chunks.size,
                  elapsedSeconds = elapsedSec()
                )
              )
              chunk.index to res
            }
          }
        }.awaitAll()
      }.sortedBy { it.first }.map { it.second }
    } catch (e: AiServiceException) {
      if (e.isNetworkError || e.isServerUnavailable) {
        onProgress(
          ProgressState(
            stage = ProcessingStage.FINAL_PREPARATION,
            detailText = "স্থানীয় বাংলা পরিমার্জন প্রস্তাবনা সাজানো হচ্ছে…",
            completedChunks = chunks.size,
            totalChunks = chunks.size,
            elapsedSeconds = elapsedSec()
          )
        )
        val localReport = BengaliLocalAnalyzer.runLocalRefinement(
          text = text,
          category = category,
          mode = mode,
          poetryProtectionStrict = poetryProtectionStrict,
          editorialDisclaimer = MANDATORY_REFINEMENT_DISCLAIMER,
          durationMs = System.currentTimeMillis() - startTime
        )
        historyDao.insert(
          AnalysisHistoryEntity(
            manuscriptId = manuscriptId,
            manuscriptTitle = manuscriptTitle.ifBlank { "শিরোনামহীন পান্ডুলিপি" },
            analysisType = AnalysisType.HUMAN_REFINEMENT.name,
            mode = "${cacheModeKey}_LOCAL",
            contentHash = contentHash,
            summary = "${mode.bengaliLabel} (স্থানীয়) • ${BengaliUtils.toBengaliDigits(localReport.changes.size)}টি পরিবর্তন",
            resultJson = refinementAdapter.toJson(localReport),
            durationMs = localReport.durationMs
          )
        )
        return@withContext localReport
      }
      throw e
    }

    onProgress(
      ProgressState(
        stage = ProcessingStage.FINAL_PREPARATION,
        detailText = "পূর্ববর্তী ও পরিমার্জিত পাঠ্যের তুলনা প্রস্তুত হচ্ছে…",
        completedChunks = chunks.size,
        totalChunks = chunks.size,
        elapsedSeconds = elapsedSec()
      )
    )

    val allChanges = chunkResults.flatMap { it.changes }
    val rawRefined = chunkResults.joinToString("\n\n") { it.refinedText.trim() }.ifBlank { text }
    val refinedFull = if (category == ManuscriptCategory.POETRY && poetryProtectionStrict) {
      val origLineCount = text.trim().lines().size
      val refLineCount = rawRefined.trim().lines().size
      if (kotlin.math.abs(origLineCount - refLineCount) <= 1 && rawRefined.length >= (text.length * 0.72).toInt()) {
        rawRefined
      } else {
        // Strictly preserve original poetry lines & stanzas, applying only matched targeted changes
        var safeText = text
        for (change in allChanges) {
          if (change.originalSegment.isNotBlank() && safeText.contains(change.originalSegment)) {
            safeText = safeText.replaceFirst(change.originalSegment, change.refinedSegment)
          }
        }
        safeText
      }
    } else {
      rawRefined
    }
    val summary = chunkResults.map { it.summary }.filter { it.isNotBlank() }.distinct().joinToString(" ")
      .ifBlank { "${mode.bengaliLabel} অনুযায়ী মোট ${BengaliUtils.toBengaliDigits(allChanges.size)}টি পরিমার্জন প্রস্তাব করা হয়েছে।" }

    val poetryNote = if (category == ManuscriptCategory.POETRY) {
      "কবিতার চিত্রকল্প, রূপক, আবেগ, পঙ্ক্তির বিন্যাস ও ছন্দ অক্ষুণ্ণ রেখে কেবল বিকল্প শৈল্পিক পরামর্শ দেওয়া হয়েছে—মূল কাব্যভাষা জোরপূর্বক বদলে দেওয়া হয়নি।"
    } else ""

    val duration = System.currentTimeMillis() - startTime
    val report = RefinementReport(
      mode = mode.name,
      category = category.name,
      summary = summary,
      poetryPreservationNote = poetryNote,
      refinedFullText = refinedFull,
      changes = allChanges,
      editorialDisclaimer = MANDATORY_REFINEMENT_DISCLAIMER,
      durationMs = duration,
      fromCache = false
    )

    historyDao.insert(
      AnalysisHistoryEntity(
        manuscriptId = manuscriptId,
        manuscriptTitle = manuscriptTitle.ifBlank { "শিরোনামহীন পান্ডুলিপি" },
        analysisType = AnalysisType.HUMAN_REFINEMENT.name,
        mode = cacheModeKey,
        contentHash = contentHash,
        summary = "${mode.bengaliLabel} • ${BengaliUtils.toBengaliDigits(allChanges.size)}টি পরিবর্তন",
        resultJson = refinementAdapter.toJson(report),
        durationMs = duration
      )
    )

    report
  }

  private data class ChunkRefineResult(
    val summary: String,
    val refinedText: String,
    val changes: List<RefinementChange>
  )

  private suspend fun refineChunk(
    chunk: ManuscriptChunk,
    category: ManuscriptCategory,
    mode: RefinementMode
  ): ChunkRefineResult {
    val poetryClause = if (category == ManuscriptCategory.POETRY) {
      """
      কবিতার জন্য বিশেষ নির্দেশনা:
      - চিত্রকল্প, উপমা, রূপক, আবেগ, অন্ত্যমিল, ছন্দ এবং পঙ্ক্তির বিন্যাস সম্পূর্ণ রক্ষা করুন।
      - কবির ইচ্ছাকৃত ব্যতিক্রমী কাব্যিক প্রয়োগ কখনোই সাধারণ গদ্যভাষায় বদলে দেবেন না।
      - যান্ত্রিক বা অপ্রাকৃতিক শব্দ থাকলেই কেবল সূক্ষ্ম বিকল্প পরামর্শ দিন।
      """.trimIndent()
    } else {
      """
      গদ্যের জন্য নির্দেশনা:
      - যান্ত্রিক, অনুবাদসুলভ বা অতিরিক্ত গতানুগতিক বাক্যকে স্বাভাবিক, প্রাণবন্ত ও সাবলীল বাংলায় পরিমার্জন করুন।
      - লেখকের মূল অর্থ, তথ্য, চরিত্র এবং নিজস্ব কণ্ঠস্বর অক্ষুণ্ণ রাখুন।
      - অপ্রয়োজনীয় পুনরাবৃত্তি কমিয়ে বাক্যের ছন্দ ও অনুচ্ছেদের ধারাবাহিকতা উন্নত করুন।
      """.trimIndent()
    }

    val systemInstruction = """
      আপনি একজন অভিজ্ঞ বাংলা সাহিত্য সম্পাদক। আপনার কাজ হলো প্রদত্ত বাংলা লেখাকে ‘${mode.bengaliLabel}’ (${mode.subtitle}) অনুযায়ী স্বাভাবিক ও মানবিক সম্পাদকীয় মানে পরিমার্জন করা।
      $poetryClause
      অবশ্যই নিচের JSON কাঠামোতে বাংলায় উত্তর দিন:
      {
        "summary": "পরিমার্জনের সংক্ষিপ্ত সারসংক্ষেপ (১-২ বাক্যে বাংলায়)",
        "refinedText": "পরিমার্জিত সম্পূর্ণ অংশ (কোনো অনুচ্ছেদ বা পঙ্ক্তি বাদ দেবেন না)",
        "changes": [
          {
            "originalSegment": "মূল পাঠ্যের পরিবর্তিত বাক্য বা অংশ (হুবহু)",
            "refinedSegment": "পরিমার্জিত রূপ",
            "changeReason": "কেন এই পরিবর্তন করা হলো তার স্পষ্ট বাংলা ব্যাখ্যা",
            "changeType": "বাক্য গঠন | শব্দচয়ন | ছন্দ ও প্রবাহ | পুনরাবৃত্তি হ্রাস | সংযোগ ও ধারাবাহিকতা | কাব্যিক পরামর্শ"
          }
        ]
      }
    """.trimIndent()

    val rawJson = geminiClient.generateStructuredJson(
      model = GeminiApiClient.MODEL_PRIMARY,
      systemInstruction = systemInstruction,
      userPrompt = "পরিমার্জনের জন্য বাংলা ${category.bengaliLabel} অংশ:\n\n${chunk.text}",
      thinkingLevel = "LOW"
    )

    val root = runCatching { JSONObject(rawJson) }.getOrElse {
      throw AiServiceException("মানবিক রূপান্তরের ফলাফল পড়া সম্ভব হয়নি।", cause = it)
    }

    val summary = root.optString("summary", "")
    val refinedText = root.optString("refinedText", chunk.text).ifBlank { chunk.text }
    val arr = root.optJSONArray("changes") ?: JSONArray()
    val changes = mutableListOf<RefinementChange>()
    for (i in 0 until arr.length()) {
      val o = arr.optJSONObject(i) ?: continue
      val orig = o.optString("originalSegment", "").trim()
      val ref = o.optString("refinedSegment", "").trim()
      if (orig.isEmpty() || orig == ref) continue
      changes.add(
        RefinementChange(
          id = "ref_c${chunk.index}_${i + 1}",
          originalSegment = orig,
          refinedSegment = ref,
          changeReason = o.optString("changeReason", "ভাষার সাবলীলতা বৃদ্ধির জন্য পরিমার্জিত।"),
          changeType = o.optString("changeType", "বাক্য গঠন")
        )
      )
    }
    return ChunkRefineResult(summary, refinedText, changes)
  }

  // =========================================================================
  // 4. COMPLETE MANUSCRIPT ORGANIZER ("পান্ডুলিপি সাজান")
  // =========================================================================

  suspend fun organizeManuscript(
    manuscriptId: Long?,
    manuscriptTitle: String,
    text: String,
    category: ManuscriptCategory,
    includeAiDeepConsistency: Boolean = true,
    onProgress: (ProgressState) -> Unit
  ): OrganizationReport = withContext(Dispatchers.Default) {
    val startTime = System.currentTimeMillis()
    val elapsedSec = { ((System.currentTimeMillis() - startTime) / 1000L).toInt() }

    onProgress(
      ProgressState(
        stage = ProcessingStage.RECEIVED,
        detailText = "পান্ডুলিপির কাঠামো বিশ্লেষণ শুরু হচ্ছে…",
        elapsedSeconds = elapsedSec()
      )
    )

    // Instant deterministic local analysis (zero cost, 100% accurate structural stats)
    val metrics = BengaliUtils.calculateMetrics(text)
    val toc = BengaliLocalAnalyzer.extractTableOfContents(text)
    val repeated = BengaliLocalAnalyzer.findRepeatedSegments(text)
    val (formattingIssues, missingHeadings) = BengaliLocalAnalyzer.detectFormattingIssues(text, category)
    val cleanFormatted = BengaliLocalAnalyzer.buildCleanFormattedDraft(text, category)

    val consistencyFindings = mutableListOf<ConsistencyFinding>()
    val characterList = mutableListOf<String>()
    val improvements = mutableListOf<String>()

    if (includeAiDeepConsistency && text.length >= 60) {
      onProgress(
        ProgressState(
          stage = ProcessingStage.LINGUISTIC_ANALYSIS,
          detailText = if (category == ManuscriptCategory.POETRY) {
            "কবিতার শিরোনাম, বিভাগ, ক্রম ও সংকলন কাঠামো বিশ্লেষণ চলছে…"
          } else {
            "অধ্যায় কাঠামো, চরিত্রের নাম, পরিভাষা এবং স্থান-সময়ের সামঞ্জস্য পরীক্ষা চলছে…"
          },
          elapsedSeconds = elapsedSec()
        )
      )

      val excerpt = if (text.length <= 10_000) text else text.take(5500) + "\n\n...\n\n" + text.takeLast(4500)
      val domainFocus = if (category == ManuscriptCategory.POETRY) {
        """
        এটি একটি বাংলা কবিতা বা কবিতা সংকলন।
        - কবিতার শিরোনাম, বিভাগ, সূচিপত্র, ভূমিকা, উৎসর্গ ও শেষাংশ নিয়ে গঠনমূলক পরামর্শ দিন।
        - কখনোই কবির অনুমতি ছাড়া কবিতার ক্রম স্বয়ংক্রিয়ভাবে পাল্টানোর কথা বলবেন না; কেবল পরামর্শ দিন।
        """.trimIndent()
      } else {
        """
        এটি একটি বাংলা গল্প, উপন্যাস বা গদ্য পান্ডুলিপি।
        - অধ্যায় ও উপ-অধ্যায়ের বিভাজন, অনুপস্থিত অধ্যায় শিরোনাম, চরিত্রের নামের বানান/ব্যবহারের অসামঞ্জস্য, পরিভাষার অসামঞ্জস্য এবং স্থান ও সময়ের ধারাবাহিকতা বিশ্লেষণ করুন।
        - উল্লিখিত প্রধান চরিত্রগুলোর তালিকা তৈরি করুন।
        """.trimIndent()
      }

      val systemInstruction = """
        আপনি একজন জ্যেষ্ঠ বাংলা প্রকাশনা সম্পাদক ও পান্ডুলিপি বিন্যাস বিশেষজ্ঞ।
        $domainFocus
        অবশ্যই নিচের JSON কাঠামোতে বাংলায় উত্তর দিন:
        {
          "characterList": ["চরিত্র ১", "চরিত্র ২"],
          "missingHeadings": ["যে অংশে শিরোনাম যোগ করা যেতে পারে"],
          "consistencyFindings": [
            { "title": "সংক্ষিপ্ত শিরোনাম", "details": "বিস্তারিত পর্যবেক্ষণ", "category": "চরিত্রের নাম | পরিভাষা | স্থান ও সময় | শিরোনাম | বিন্যাস | কবিতার ক্রম" }
          ],
          "suggestedImprovements": ["ব্যবহারিক বিন্যাস পরামর্শ ১", "পরামর্শ ২"]
        }
      """.trimIndent()

      val aiRes = runCatching {
        geminiClient.generateStructuredJson(
          model = GeminiApiClient.MODEL_PRIMARY,
          systemInstruction = systemInstruction,
          userPrompt = "শিরোনাম: $manuscriptTitle\n\nপান্ডুলিপি:\n$excerpt",
          thinkingLevel = "LOW"
        )
      }.getOrNull()

      if (aiRes != null) {
        runCatching {
          val root = JSONObject(aiRes)
          val charsArr = root.optJSONArray("characterList") ?: JSONArray()
          for (i in 0 until charsArr.length()) {
            val c = charsArr.optString(i, "").trim()
            if (c.isNotEmpty()) characterList.add(c)
          }
          val missArr = root.optJSONArray("missingHeadings") ?: JSONArray()
          val extraMissing = mutableListOf<String>()
          for (i in 0 until missArr.length()) {
            val m = missArr.optString(i, "").trim()
            if (m.isNotEmpty()) extraMissing.add(m)
          }
          val findArr = root.optJSONArray("consistencyFindings") ?: JSONArray()
          for (i in 0 until findArr.length()) {
            val o = findArr.optJSONObject(i) ?: continue
            consistencyFindings.add(
              ConsistencyFinding(
                title = o.optString("title", "সামঞ্জস্য পর্যবেক্ষণ"),
                details = o.optString("details", ""),
                category = o.optString("category", "বিন্যাস")
              )
            )
          }
          val impArr = root.optJSONArray("suggestedImprovements") ?: JSONArray()
          for (i in 0 until impArr.length()) {
            val s = impArr.optString(i, "").trim()
            if (s.isNotEmpty()) improvements.add(s)
          }
          if (extraMissing.isNotEmpty()) {
            improvements.addAll(0, extraMissing)
          }
        }
      }
    }

    if (improvements.isEmpty()) {
      improvements.add(
        if (category == ManuscriptCategory.POETRY) {
          "প্রতিটি কবিতার শুরুতে স্পষ্ট শিরোনাম ও সংকলনের জন্য ভূমিকা/উৎসর্গ যুক্ত করলে প্রকাশনার মান বাড়বে।"
        } else {
          "অনুচ্ছেদের ফাঁকা লাইন ও অধ্যায়ের শিরোনাম অভিন্ন রাখলে পাঠযোগ্যতা আরও বৃদ্ধি পাবে।"
        }
      )
    }

    val duration = System.currentTimeMillis() - startTime
    val report = OrganizationReport(
      category = category.name,
      totalChapters = toc.count { it.level == 1 },
      totalParagraphs = metrics.paragraphs,
      totalWords = metrics.words,
      totalCharacters = metrics.characters,
      tableOfContents = toc,
      repeatedSegments = repeated,
      formattingIssues = formattingIssues,
      missingHeadings = missingHeadings,
      consistencyFindings = consistencyFindings,
      characterList = characterList,
      suggestedImprovements = improvements.distinct(),
      proposedFormattedContent = cleanFormatted,
      approvalRequiredNote = MANDATORY_ORGANIZER_APPROVAL_NOTE,
      durationMs = duration
    )

    historyDao.insert(
      AnalysisHistoryEntity(
        manuscriptId = manuscriptId,
        manuscriptTitle = manuscriptTitle.ifBlank { "শিরোনামহীন পান্ডুলিপি" },
        analysisType = AnalysisType.MANUSCRIPT_ORGANIZER.name,
        mode = category.name,
        contentHash = BengaliUtils.sha256(text),
        summary = "${BengaliUtils.toBengaliDigits(report.totalChapters)} অধ্যায় • ${BengaliUtils.toBengaliDigits(report.totalParagraphs)} অনুচ্ছেদ • ${BengaliUtils.toBengaliDigits(report.totalWords)} শব্দ",
        resultJson = organizationAdapter.toJson(report),
        durationMs = duration
      )
    )

    report
  }

  fun decodeProofreadingReport(json: String): ProofreadingReport? =
    runCatching { proofreadingAdapter.fromJson(json) }.getOrNull()

  fun decodeAiDetectionReport(json: String): AiDetectionReport? =
    runCatching { aiDetectionAdapter.fromJson(json) }.getOrNull()

  fun decodeRefinementReport(json: String): RefinementReport? =
    runCatching { refinementAdapter.fromJson(json) }.getOrNull()

  fun decodeOrganizationReport(json: String): OrganizationReport? =
    runCatching { organizationAdapter.fromJson(json) }.getOrNull()
}
