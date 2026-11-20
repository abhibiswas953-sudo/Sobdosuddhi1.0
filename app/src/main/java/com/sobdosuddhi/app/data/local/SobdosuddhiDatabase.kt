package com.sobdosuddhi.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.VersionSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
  entities = [
    FolderEntity::class,
    ManuscriptEntity::class,
    DraftVersionEntity::class,
    AnalysisHistoryEntity::class
  ],
  version = 1,
  exportSchema = false
)
abstract class SobdosuddhiDatabase : RoomDatabase() {
  abstract fun folderDao(): FolderDao
  abstract fun manuscriptDao(): ManuscriptDao
  abstract fun draftVersionDao(): DraftVersionDao
  abstract fun analysisHistoryDao(): AnalysisHistoryDao

  companion object {
    @Volatile
    private var INSTANCE: SobdosuddhiDatabase? = null

    fun getInstance(context: Context): SobdosuddhiDatabase {
      return INSTANCE ?: synchronized(this) {
        val db = Room.databaseBuilder(
          context.applicationContext,
          SobdosuddhiDatabase::class.java,
          "sobdosuddhi_private.db"
        ).fallbackToDestructiveMigration(dropAllTables = false).build()
        INSTANCE = db
        CoroutineScope(Dispatchers.IO).launch {
          seedInitialSamplesIfEmpty(db)
        }
        db
      }
    }

    private suspend fun seedInitialSamplesIfEmpty(db: SobdosuddhiDatabase) {
      if (db.manuscriptDao().getAllSnapshot().isNotEmpty()) return

      val poetryFolderId = db.folderDao().insert(FolderEntity(name = "কবিতা সংকলন"))
      val proseFolderId = db.folderDao().insert(FolderEntity(name = "গল্প ও উপন্যাস"))

      val samplePoem = """
        বর্ষার সন্ধ্যায় জানালার ধারে
        বৃষ্টি নামে নীরব শহরের ছাদে,
        ভিজে ওঠে পুরোনো স্মৃতির পাতা
        মেঘের আড়ালে আলো-ছায়া কাঁদে।

        তুমি বলেছিলে ফিরবে শ্রাবন শেষে
        দুরত্ব কখনো মুছে দেয় না টান,
        তবুও আজ শুধু মাত্র নিরবতা
        বাতাসে ভাসে অচেনা সেই গান.
      """.trimIndent()

      val poemMetrics = BengaliUtils.calculateMetrics(samplePoem)
      val poemId = db.manuscriptDao().insert(
        ManuscriptEntity(
          title = "শ্রাবণ সন্ধ্যার চিঠি (নমুনা কবিতা)",
          authorName = "অনির্বাণ সেন",
          category = ManuscriptCategory.POETRY.name,
          content = samplePoem,
          originalContent = samplePoem,
          folderId = poetryFolderId,
          preface = "বর্ষাকালীন স্মৃতি ও নিঃসঙ্গতা নিয়ে রচিত সংক্ষিপ্ত কবিতা।",
          dedication = "সকল বাংলা কবিতাপ্রেমীর উদ্দেশ্যে",
          wordCount = poemMetrics.words,
          charCount = poemMetrics.characters,
          paragraphCount = poemMetrics.paragraphs,
          isPinned = true
        )
      )
      db.draftVersionDao().insert(
        DraftVersionEntity(
          manuscriptId = poemId,
          versionNumber = 1,
          label = "প্রাথমিক খসড়া",
          changeSummary = "নমুনা কবিতার মূল সংস্করণ সংরক্ষিত হয়েছে",
          content = samplePoem,
          wordCount = poemMetrics.words,
          sourceType = VersionSourceType.INITIAL.name
        )
      )

      val sampleStory = """
        অধ্যায় ১: নদীর ধারের পুরোনো বাড়ি

        শরতের সকালবেলা কুয়াশা কাটতে তখনও বেশ দেরি। তমাল পুরোনো লাল ইটের বাড়িটার সামনে দাঁড়িয়ে দীর্ঘশ্বাস ফেলল। বারো বছর পর এই গ্রামে ফিরে আসাটা তার কাছে একই সাথে আনন্দ ও বিষাদের। ইতিমধ্যে গ্রামের অনেক রাস্তাঘাট বদলে গেছে , কিন্তু পুকুরপাড়ের সেই বকুল গাছটি আজও ঠিক আগের মতোই দাঁড়িয়ে আছে।

        বাড়ির বারান্দা থেকে সুমিতা ডেকে উঠল, "তমাল, বাইরে দাঁড়িয়ে আছিস কেন? ভেতরে আয়।" তার কণ্ঠে যেন সেই পুরোনো স্নেহের সুর আজও অটুট রয়েছে। তমাল মৃদু হেসে সিঁড়ি বেয়ে ওপরে উঠতে শুরু করল।

        অধ্যায় ২: হারানো ডায়েরির খোঁজে

        সন্ধ্যার আগেই তমাল চিলেকোঠার ঘরটি খুলল। ধুলোবালি জমে থাকা কাঠের আলমারির ভেতর থেকে সে বাবার পুরোনো চামড়ার ডায়েরিটা খুঁজে পেল। পাতাগুলো হলদে হয়ে গেছে, তবুও কালির অক্ষরগুলো এখনও বেশ স্পষ্ট।
      """.trimIndent()

      val storyMetrics = BengaliUtils.calculateMetrics(sampleStory)
      val storyId = db.manuscriptDao().insert(
        ManuscriptEntity(
          title = "পুরোনো বাড়ির চিলেকোঠা (নমুনা গল্প)",
          authorName = "অনির্বাণ সেন",
          category = ManuscriptCategory.PROSE.name,
          content = sampleStory,
          originalContent = sampleStory,
          folderId = proseFolderId,
          preface = "শৈশবের স্মৃতি ও ফিরে দেখার গল্প।",
          wordCount = storyMetrics.words,
          charCount = storyMetrics.characters,
          paragraphCount = storyMetrics.paragraphs
        )
      )
      db.draftVersionDao().insert(
        DraftVersionEntity(
          manuscriptId = storyId,
          versionNumber = 1,
          label = "প্রথম খসড়া",
          changeSummary = "নমুনা গল্পের মূল সংস্করণ সংরক্ষিত হয়েছে",
          content = sampleStory,
          wordCount = storyMetrics.words,
          sourceType = VersionSourceType.INITIAL.name
        )
      )
    }
  }
}
