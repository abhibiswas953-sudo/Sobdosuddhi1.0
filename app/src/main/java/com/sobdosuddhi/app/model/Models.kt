package com.sobdosuddhi.app.model

import com.squareup.moshi.JsonClass

enum class ManuscriptCategory(val bengaliLabel: String, val shortLabel: String) {
  POETRY("বাংলা কবিতা", "কবিতা"),
  PROSE("বাংলা গল্প, উপন্যাস ও গদ্য", "গল্প ও গদ্য");

  companion object {
    fun fromName(value: String): ManuscriptCategory =
      entries.firstOrNull { it.name == value } ?: PROSE
  }
}

enum class ProofreadingMode(
  val bengaliLabel: String,
  val description: String,
  val targetTimeLabel: String
) {
  QUICK(
    "দ্রুত পরীক্ষা",
    "সাধারণ বানান ভুল, বিরামচিহ্ন ও প্রাথমিক ব্যাকরণ দ্রুত পরীক্ষা",
    "প্রায় ১০–২০ সেকেন্ড"
  ),
  STANDARD(
    "মানসম্মত পরীক্ষা",
    "সম্পূর্ণ প্রুফরিডিং: বানান, যুক্তাক্ষর, ব্যাকরণ, বাক্য গঠন, পুনরাবৃত্তি ও প্রাসঙ্গিক ভুল",
    "প্রায় ২০–৫০ সেকেন্ড"
  ),
  DEEP(
    "গভীর সম্পাদনা পর্যালোচনা",
    "বিস্তারিত ভাষাতাত্ত্বিক বিশ্লেষণ, সাহিত্যিক ধারাবাহিকতা, অনুচ্ছেদ কাঠামো ও শব্দচয়ন পরামর্শ",
    "বড় পান্ডুলিপির জন্য বিস্তারিত"
  );

  companion object {
    fun fromName(value: String): ProofreadingMode =
      entries.firstOrNull { it.name == value } ?: STANDARD
  }
}

enum class RefinementMode(val bengaliLabel: String, val subtitle: String) {
  NATURAL("স্বাভাবিক ও সাবলীল ভাষা", "দৈনন্দিন প্রাঞ্জল ও স্বচ্ছন্দ বাংলা গদ্য"),
  LITERARY("সাহিত্যিক ভাষা", "সমৃদ্ধ শব্দচয়ন, ধ্বনিমাধুর্য ও সাহিত্যিক গাম্ভীর্য"),
  SIMPLE("সহজ ও প্রাঞ্জল ভাষা", "সহজবোধ্য, স্পষ্ট ও পাঠকবান্ধব উপস্থাপনা"),
  POLISHED("মার্জিত ও পরিশীলিত ভাষা", "প্রমিত, পরিচ্ছন্ন ও আনুষ্ঠানিক সম্পাদকীয় মান"),
  PRESERVE_STYLE("লেখকের নিজস্ব স্টাইল বজায় রেখে সম্পাদনা", "মূল স্বর ও প্রকাশভঙ্গি অক্ষুণ্ণ রেখে সূক্ষ্ম পরিমার্জন")
}

enum class AiDetectionLabel(val bengaliLabel: String) {
  LIKELY_AI("AI-লিখিত হওয়ার সম্ভাবনা বেশি"),
  LIKELY_HUMAN("মানবিক লেখার বৈশিষ্ট্য বেশি"),
  MIXED("মিশ্র বৈশিষ্ট্য পাওয়া গেছে"),
  UNCERTAIN("নিশ্চিত সিদ্ধান্ত দেওয়া সম্ভব নয়");

  companion object {
    fun fromName(value: String): AiDetectionLabel =
      entries.firstOrNull { it.name == value } ?: UNCERTAIN
  }
}

enum class ConfidenceLevel(val bengaliLabel: String) {
  LOW("কম আত্মবিশ্বাস"),
  MEDIUM("মাঝারি আত্মবিশ্বাস"),
  HIGH("উচ্চ আত্মবিশ্বাস");

  companion object {
    fun fromName(value: String): ConfidenceLevel =
      entries.firstOrNull { it.name == value } ?: LOW
  }
}

enum class ThemeMode(val bengaliLabel: String) {
  LIGHT("হালকা থিম"),
  DARK("গাঢ় থিম"),
  SYSTEM("সিস্টেম অনুসরণ");

  companion object {
    fun fromName(value: String): ThemeMode =
      entries.firstOrNull { it.name == value } ?: SYSTEM
  }
}

enum class AnalysisType(val bengaliLabel: String) {
  PROOFREADING("প্রুফরিডিং"),
  AI_DETECTION("AI লেখা শনাক্তকরণ"),
  HUMAN_REFINEMENT("মানবিক ভাষায় রূপান্তর"),
  MANUSCRIPT_ORGANIZER("পান্ডুলিপি সাজান");

  companion object {
    fun fromName(value: String): AnalysisType =
      entries.firstOrNull { it.name == value } ?: PROOFREADING
  }
}

enum class VersionSourceType(val bengaliLabel: String) {
  INITIAL("মূল পান্ডুলিপি"),
  MANUAL_SAVE("সংরক্ষিত সংস্করণ"),
  AUTO_DRAFT("স্বয়ংক্রিয় খসড়া"),
  PROOFREAD_APPLY("প্রুফরিডিং প্রয়োগ"),
  REFINEMENT_APPLY("মানবিক রূপান্তর প্রয়োগ"),
  ORGANIZE_APPLY("পান্ডুলিপি বিন্যাস প্রয়োগ"),
  RESTORE("পূর্ববর্তী সংস্করণ পুনরুদ্ধার");

  companion object {
    fun fromName(value: String): VersionSourceType =
      entries.firstOrNull { it.name == value } ?: MANUAL_SAVE
  }
}

enum class ProcessingStage(val bengaliLabel: String, val stepIndex: Int) {
  RECEIVED("পান্ডুলিপি গৃহীত হয়েছে", 1),
  TEXT_PROCESSING("পাঠ্য প্রক্রিয়াকরণ চলছে", 2),
  LINGUISTIC_ANALYSIS("ভাষাতাত্ত্বিক বিশ্লেষণ চলছে", 3),
  CORRECTION_GENERATION("সংশোধনী ও পরামর্শ প্রস্তুত হচ্ছে", 4),
  FINAL_PREPARATION("চূড়ান্ত ফলাফল সাজানো হচ্ছে", 5)
}

data class ProgressState(
  val stage: ProcessingStage,
  val detailText: String = "",
  val completedChunks: Int = 0,
  val totalChunks: Int = 0,
  val elapsedSeconds: Int = 0,
  val isPartialReady: Boolean = false
)

// --- Proofreading Models ---

@JsonClass(generateAdapter = true)
data class ProofreadingIssue(
  val id: String,
  val originalText: String,
  val suggestedText: String,
  val category: String, // বানান ভুল, যুক্তাক্ষরের ভুল, কার ও ফলা, বিরামচিহ্ন, ব্যাকরণ, বাক্য গঠন, শব্দের ভুল প্রয়োগ, পুনরাবৃত্তি, টাইপিং ভুল, অপ্রয়োজনীয় শব্দ, অসংলগ্ন বাক্য, অনুচ্ছেদের অসামঞ্জস্য, স্বাভাবিকতা
  val severity: String, // "জরুরি" | "গুরুত্বপূর্ণ" | "পরামর্শ"
  val explanation: String,
  val paragraphIndex: Int = 0,
  val poetryPreservationNote: String = "",
  val source: String = "AI" // "LOCAL" | "AI"
)

@JsonClass(generateAdapter = true)
data class ProofreadingReport(
  val category: String,
  val mode: String,
  val overallSummary: String,
  val poetryProtectionSummary: String = "",
  val issues: List<ProofreadingIssue>,
  val suggestedFullText: String,
  val durationMs: Long = 0L,
  val fromCache: Boolean = false,
  val chunksProcessed: Int = 1,
  val totalChunks: Int = 1,
  val isOfflineMode: Boolean = false
)

// --- AI Writing Detection Models ---

@JsonClass(generateAdapter = true)
data class SuspiciousSentence(
  val sentence: String,
  val reason: String,
  val signalStrength: String // "বেশি" | "মাঝারি" | "সামান্য"
)

@JsonClass(generateAdapter = true)
data class ParagraphAiNote(
  val paragraphIndex: Int,
  val excerpt: String,
  val assessment: String,
  val observation: String
)

@JsonClass(generateAdapter = true)
data class LinguisticDimensionScore(
  val dimensionName: String,
  val observation: String,
  val tendency: String // "মানবিক" | "মিশ্র" | "যান্ত্রিক"
)

@JsonClass(generateAdapter = true)
data class AiDetectionReport(
  val classification: String, // AiDetectionLabel.name
  val detectionIndicatorLevel: String, // "উচ্চ যান্ত্রিক প্রবণতা" | "মাঝারি মিশ্র প্রবণতা" | "নিম্ন যান্ত্রিক প্রবণতা (মানবিক)" | "অপর্যাপ্ত তথ্য"
  val aiLikelihoodRange: String, // Qualitative descriptive band, never claimed as 100% verified probability
  val humanLikelihoodRange: String,
  val confidenceLevel: String, // ConfidenceLevel.name
  val verifiedAuthorshipNote: String,
  val analysisMode: String, // "দ্রুত বিশ্লেষণ" | "বিস্তারিত বিশ্লেষণ"
  val explanation: String,
  val overallCharacteristics: List<String>,
  val linguisticDimensions: List<LinguisticDimensionScore>,
  val suspiciousSentences: List<SuspiciousSentence>,
  val paragraphAnalysis: List<ParagraphAiNote>,
  val uncertaintyWarning: String,
  val durationMs: Long = 0L,
  val fromCache: Boolean = false
)

// --- Human Writing Refinement Models ---

@JsonClass(generateAdapter = true)
data class RefinementChange(
  val id: String,
  val originalSegment: String,
  val refinedSegment: String,
  val changeReason: String,
  val changeType: String // "বাক্য গঠন" | "শব্দচয়ন" | "ছন্দ ও প্রবাহ" | "পুনরাবৃত্তি হ্রাস" | "সংযোগ ও ধারাবাহিকতা" | "কাব্যিক পরামর্শ"
)

@JsonClass(generateAdapter = true)
data class RefinementReport(
  val mode: String,
  val category: String,
  val summary: String,
  val poetryPreservationNote: String = "",
  val refinedFullText: String,
  val changes: List<RefinementChange>,
  val editorialDisclaimer: String,
  val durationMs: Long = 0L,
  val fromCache: Boolean = false
)

// --- Manuscript Organizer Models ---

@JsonClass(generateAdapter = true)
data class TocEntry(
  val title: String,
  val level: Int, // 1 = Chapter/Section, 2 = Sub-chapter/Poem
  val paragraphIndex: Int,
  val wordCount: Int
)

@JsonClass(generateAdapter = true)
data class RepeatedSegment(
  val text: String,
  val occurrences: Int,
  val locations: List<Int>,
  val type: String // "পুনরাবৃত্ত অনুচ্ছেদ" | "পুনরাবৃত্ত বাক্য"
)

@JsonClass(generateAdapter = true)
data class ConsistencyFinding(
  val title: String,
  val details: String,
  val category: String // "চরিত্রের নাম" | "পরিভাষা" | "স্থান ও সময়" | "শিরোনাম" | "বিন্যাস" | "কবিতার ক্রম"
)

@JsonClass(generateAdapter = true)
data class OrganizationReport(
  val category: String,
  val totalChapters: Int,
  val totalParagraphs: Int,
  val totalWords: Int,
  val totalCharacters: Int,
  val tableOfContents: List<TocEntry>,
  val repeatedSegments: List<RepeatedSegment>,
  val formattingIssues: List<String>,
  val missingHeadings: List<String>,
  val consistencyFindings: List<ConsistencyFinding>,
  val characterList: List<String>,
  val suggestedImprovements: List<String>,
  val proposedFormattedContent: String,
  val approvalRequiredNote: String,
  val durationMs: Long = 0L
)

// --- Diff & Stats Models ---

enum class DiffOp { EQUAL, INSERT, DELETE }

data class DiffSegment(
  val op: DiffOp,
  val text: String
)

data class TextMetrics(
  val words: Int,
  val characters: Int,
  val charactersNoSpaces: Int,
  val paragraphs: Int,
  val lines: Int,
  val chapters: Int
)

data class DashboardStats(
  val totalManuscripts: Int = 0,
  val totalWords: Int = 0,
  val totalAnalyses: Int = 0,
  val totalVersions: Int = 0
)

enum class ManuscriptSortOption(val bengaliLabel: String) {
  UPDATED_DESC("সর্বশেষ সম্পাদিত"),
  CREATED_DESC("নতুন থেকে পুরনো"),
  TITLE_ASC("শিরোনাম অনুযায়ী"),
  WORDS_DESC("বেশি শব্দ প্রথম")
}
