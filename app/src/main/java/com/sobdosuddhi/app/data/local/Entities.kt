package com.sobdosuddhi.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "manuscripts",
  foreignKeys = [
    ForeignKey(
      entity = FolderEntity::class,
      parentColumns = ["id"],
      childColumns = ["folderId"],
      onDelete = ForeignKey.SET_NULL
    )
  ],
  indices = [Index("folderId"), Index("updatedAt")]
)
data class ManuscriptEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val authorName: String = "",
  val category: String, // ManuscriptCategory.name
  val content: String,
  val originalContent: String, // Preserved original manuscript — never overwritten without explicit approval
  val folderId: Long? = null,
  val preface: String = "", // ভূমিকা
  val dedication: String = "", // উৎসর্গ
  val epilogue: String = "", // শেষাংশ
  val wordCount: Int = 0,
  val charCount: Int = 0,
  val paragraphCount: Int = 0,
  val isPinned: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "draft_versions",
  foreignKeys = [
    ForeignKey(
      entity = ManuscriptEntity::class,
      parentColumns = ["id"],
      childColumns = ["manuscriptId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index("manuscriptId"), Index("createdAt")]
)
data class DraftVersionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val manuscriptId: Long,
  val versionNumber: Int,
  val label: String,
  val changeSummary: String,
  val content: String,
  val wordCount: Int,
  val sourceType: String, // VersionSourceType.name
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "analysis_history",
  indices = [Index("manuscriptId"), Index("contentHash"), Index("createdAt")]
)
data class AnalysisHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val manuscriptId: Long?,
  val manuscriptTitle: String,
  val analysisType: String, // AnalysisType.name
  val mode: String,
  val contentHash: String,
  val summary: String,
  val resultJson: String,
  val durationMs: Long,
  val createdAt: Long = System.currentTimeMillis()
)
