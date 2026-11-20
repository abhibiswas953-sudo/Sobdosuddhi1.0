package com.sobdosuddhi.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
  @Query("SELECT * FROM folders ORDER BY name ASC")
  fun observeAll(): Flow<List<FolderEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(folder: FolderEntity): Long

  @Update
  suspend fun update(folder: FolderEntity)

  @Delete
  suspend fun delete(folder: FolderEntity)
}

@Dao
interface ManuscriptDao {
  @Query("SELECT * FROM manuscripts ORDER BY isPinned DESC, updatedAt DESC")
  fun observeAll(): Flow<List<ManuscriptEntity>>

  @Query("SELECT * FROM manuscripts ORDER BY updatedAt DESC LIMIT :limit")
  fun observeRecent(limit: Int = 5): Flow<List<ManuscriptEntity>>

  @Query("SELECT * FROM manuscripts WHERE id = :id")
  suspend fun getById(id: Long): ManuscriptEntity?

  @Query("SELECT * FROM manuscripts WHERE id = :id")
  fun observeById(id: Long): Flow<ManuscriptEntity?>

  @Query("SELECT COUNT(*) FROM manuscripts")
  fun observeCount(): Flow<Int>

  @Query("SELECT COALESCE(SUM(wordCount), 0) FROM manuscripts")
  fun observeTotalWords(): Flow<Int>

  @Query("SELECT * FROM manuscripts ORDER BY updatedAt DESC")
  suspend fun getAllSnapshot(): List<ManuscriptEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(manuscript: ManuscriptEntity): Long

  @Update
  suspend fun update(manuscript: ManuscriptEntity)

  @Query("DELETE FROM manuscripts WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface DraftVersionDao {
  @Query("SELECT * FROM draft_versions WHERE manuscriptId = :manuscriptId ORDER BY versionNumber DESC")
  fun observeVersionsForManuscript(manuscriptId: Long): Flow<List<DraftVersionEntity>>

  @Query("SELECT * FROM draft_versions ORDER BY createdAt DESC LIMIT :limit")
  fun observeRecentVersions(limit: Int = 30): Flow<List<DraftVersionEntity>>

  @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM draft_versions WHERE manuscriptId = :manuscriptId")
  suspend fun getMaxVersionNumber(manuscriptId: Long): Int

  @Query("SELECT * FROM draft_versions WHERE manuscriptId = :manuscriptId ORDER BY versionNumber DESC LIMIT 1")
  suspend fun getLatestVersion(manuscriptId: Long): DraftVersionEntity?

  @Query("SELECT COUNT(*) FROM draft_versions")
  fun observeTotalVersionsCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(version: DraftVersionEntity): Long

  @Query("DELETE FROM draft_versions WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface AnalysisHistoryDao {
  @Query("SELECT * FROM analysis_history ORDER BY createdAt DESC LIMIT :limit")
  fun observeAll(limit: Int = 100): Flow<List<AnalysisHistoryEntity>>

  @Query("SELECT * FROM analysis_history WHERE manuscriptId = :manuscriptId ORDER BY createdAt DESC")
  fun observeForManuscript(manuscriptId: Long): Flow<List<AnalysisHistoryEntity>>

  @Query(
    "SELECT * FROM analysis_history " +
      "WHERE contentHash = :contentHash AND analysisType = :analysisType AND mode = :mode " +
      "ORDER BY createdAt DESC LIMIT 1"
  )
  suspend fun findCached(
    contentHash: String,
    analysisType: String,
    mode: String
  ): AnalysisHistoryEntity?

  @Query("SELECT COUNT(*) FROM analysis_history")
  fun observeTotalCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: AnalysisHistoryEntity): Long

  @Query("DELETE FROM analysis_history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM analysis_history")
  suspend fun clearAll()
}
