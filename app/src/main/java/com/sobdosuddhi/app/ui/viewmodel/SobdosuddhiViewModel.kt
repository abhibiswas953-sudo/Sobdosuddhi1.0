package com.sobdosuddhi.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sobdosuddhi.app.SobdosuddhiApplication
import com.sobdosuddhi.app.data.ai.AiServiceException
import com.sobdosuddhi.app.data.ai.ServerHealthResult
import com.sobdosuddhi.app.data.local.AnalysisHistoryEntity
import com.sobdosuddhi.app.data.local.DraftVersionEntity
import com.sobdosuddhi.app.data.local.FolderEntity
import com.sobdosuddhi.app.data.local.ManuscriptEntity
import com.sobdosuddhi.app.data.local.UserPreferences
import com.sobdosuddhi.app.domain.BengaliLocalAnalyzer
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.domain.ExportFormat
import com.sobdosuddhi.app.model.AiDetectionReport
import com.sobdosuddhi.app.model.AnalysisType
import com.sobdosuddhi.app.model.DashboardStats
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.ManuscriptSortOption
import com.sobdosuddhi.app.model.OrganizationReport
import com.sobdosuddhi.app.model.ProgressState
import com.sobdosuddhi.app.model.ProofreadingIssue
import com.sobdosuddhi.app.model.ProofreadingMode
import com.sobdosuddhi.app.model.ProofreadingReport
import com.sobdosuddhi.app.model.RefinementChange
import com.sobdosuddhi.app.model.RefinementMode
import com.sobdosuddhi.app.model.RefinementReport
import com.sobdosuddhi.app.model.ThemeMode
import com.sobdosuddhi.app.model.VersionSourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class EditorUiState(
  val manuscriptId: Long? = null,
  val title: String = "",
  val authorName: String = "",
  val category: ManuscriptCategory = ManuscriptCategory.PROSE,
  val content: String = "",
  val originalContent: String = "",
  val folderId: Long? = null,
  val preface: String = "",
  val dedication: String = "",
  val epilogue: String = "",
  val isDirty: Boolean = false,
  val lastSavedLabel: String = "",
  val undoStack: List<String> = emptyList(),
  val redoStack: List<String> = emptyList(),
  val importStatusMessage: String? = null
) {
  val canUndo: Boolean get() = undoStack.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()
}

data class AnalysisUiState(
  val isRunning: Boolean = false,
  val activeOperation: AnalysisType? = null,
  val progress: ProgressState? = null,
  val errorMessage: String? = null,
  val proofreadingReport: ProofreadingReport? = null,
  val acceptedIssueIds: Set<String> = emptySet(),
  val rejectedIssueIds: Set<String> = emptySet(),
  val aiDetectionReport: AiDetectionReport? = null,
  val refinementReport: RefinementReport? = null,
  val selectedRefinementChangeIds: Set<String> = emptySet(),
  val organizationReport: OrganizationReport? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SobdosuddhiViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as SobdosuddhiApplication
  private val db = app.database
  private val manuscriptDao = db.manuscriptDao()
  private val folderDao = db.folderDao()
  private val versionDao = db.draftVersionDao()
  private val historyDao = db.analysisHistoryDao()
  private val prefsRepo = app.preferencesRepository
  private val aiEngine get() = app.aiEngine
  private val docManager get() = app.documentManager
  private val connectivity = app.connectivityMonitor

  val isOnline: StateFlow<Boolean> = connectivity.isOnline

  val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    UserPreferences()
  )

  val allManuscripts: StateFlow<List<ManuscriptEntity>> = manuscriptDao.observeAll().stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    emptyList()
  )

  val recentManuscripts: StateFlow<List<ManuscriptEntity>> = manuscriptDao.observeRecent(5).stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    emptyList()
  )

  val folders: StateFlow<List<FolderEntity>> = folderDao.observeAll().stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    emptyList()
  )

  val analysisHistory: StateFlow<List<AnalysisHistoryEntity>> = historyDao.observeAll(100).stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    emptyList()
  )

  val recentVersions: StateFlow<List<DraftVersionEntity>> = versionDao.observeRecentVersions(40).stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    emptyList()
  )

  val dashboardStats: StateFlow<DashboardStats> = combine(
    manuscriptDao.observeCount(),
    manuscriptDao.observeTotalWords(),
    historyDao.observeTotalCount(),
    versionDao.observeTotalVersionsCount()
  ) { count, words, analyses, versions ->
    DashboardStats(count, words, analyses, versions)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

  private val _editor = MutableStateFlow(EditorUiState())
  val editor: StateFlow<EditorUiState> = _editor.asStateFlow()

  val activeManuscriptVersions: StateFlow<List<DraftVersionEntity>> = _editor
    .flatMapLatest { state ->
      val id = state.manuscriptId
      if (id != null) versionDao.observeVersionsForManuscript(id) else flowOf(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _analysis = MutableStateFlow(AnalysisUiState())
  val analysis: StateFlow<AnalysisUiState> = _analysis.asStateFlow()

  // Library Filter State
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedCategoryFilter = MutableStateFlow<ManuscriptCategory?>(null)
  val selectedCategoryFilter: StateFlow<ManuscriptCategory?> = _selectedCategoryFilter.asStateFlow()

  private val _selectedFolderFilter = MutableStateFlow<Long?>(null)
  val selectedFolderFilter: StateFlow<Long?> = _selectedFolderFilter.asStateFlow()

  private val _sortOption = MutableStateFlow(ManuscriptSortOption.UPDATED_DESC)
  val sortOption: StateFlow<ManuscriptSortOption> = _sortOption.asStateFlow()

  val filteredManuscripts: StateFlow<List<ManuscriptEntity>> = combine(
    allManuscripts,
    _searchQuery,
    _selectedCategoryFilter,
    _selectedFolderFilter,
    _sortOption
  ) { list, query, cat, folder, sort ->
    val q = query.trim().lowercase()
    val filtered = list.filter { item ->
      val matchesQuery = q.isEmpty() ||
        item.title.lowercase().contains(q) ||
        item.content.lowercase().contains(q) ||
        item.authorName.lowercase().contains(q)
      val matchesCategory = cat == null || item.category == cat.name
      val matchesFolder = folder == null || item.folderId == folder
      matchesQuery && matchesCategory && matchesFolder
    }
    when (sort) {
      ManuscriptSortOption.UPDATED_DESC -> filtered.sortedWith(
        compareByDescending<ManuscriptEntity> { it.isPinned }.thenByDescending { it.updatedAt }
      )
      ManuscriptSortOption.CREATED_DESC -> filtered.sortedByDescending { it.createdAt }
      ManuscriptSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
      ManuscriptSortOption.WORDS_DESC -> filtered.sortedByDescending { it.wordCount }
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val snackbarMessage = _snackbarMessage.asSharedFlow()

  private val _serverHealth = MutableStateFlow<ServerHealthResult?>(null)
  val serverHealth: StateFlow<ServerHealthResult?> = _serverHealth.asStateFlow()

  private val _isCheckingServer = MutableStateFlow(false)
  val isCheckingServer: StateFlow<Boolean> = _isCheckingServer.asStateFlow()

  private var autoSaveJob: Job? = null
  private var analysisJob: Job? = null

  init {
    viewModelScope.launch {
      prefsRepo.preferencesFlow.collect { prefs ->
        if (prefs.customProxyUrl.isNotBlank()) {
          app.geminiClient.updateProxyBaseUrl(prefs.customProxyUrl)
        }
      }
    }
    viewModelScope.launch {
      var previouslyOffline = !isOnline.value
      isOnline.collect { online ->
        if (online && previouslyOffline) {
          _analysis.update { state ->
            val clearedError = if (state.errorMessage?.contains("ইন্টারনেট") == true ||
              state.errorMessage?.contains("অফলাইন") == true
            ) null else state.errorMessage
            state.copy(errorMessage = clearedError)
          }
          notify("ইন্টারনেট সংযোগ ফিরে এসেছে—অনলাইন AI মোড সক্রিয় হয়েছে।")
        }
        previouslyOffline = !online
      }
    }
  }

  private fun notify(msg: String) {
    _snackbarMessage.tryEmit(msg)
  }

  fun notifyClipboardCopied() {
    notify("সম্পূর্ণ পান্ডুলিপি ক্লিপবোর্ডে কপি করা হয়েছে।")
  }

  private fun trimHistoryStack(stack: List<String>, maxEntries: Int = 50, maxTotalChars: Int = 2_000_000): List<String> {
    val bounded = stack.takeLast(maxEntries)
    if (bounded.size <= 3) return bounded
    var totalChars = bounded.sumOf { it.length }
    var startIdx = 0
    while (totalChars > maxTotalChars && bounded.size - startIdx > 3) {
      totalChars -= bounded[startIdx].length
      startIdx++
    }
    return if (startIdx == 0) bounded else bounded.subList(startIdx, bounded.size)
  }

  // =========================================================================
  // EDITOR & DRAFT VERSION CONTROL
  // =========================================================================

  private suspend fun persistDirtySnapshot(snapshot: EditorUiState) {
    if (!snapshot.isDirty) return
    if (snapshot.title.isBlank() && snapshot.content.isBlank()) return
    val now = System.currentTimeMillis()
    val finalTitle = snapshot.title.trim().ifBlank {
      snapshot.content.lines().firstOrNull { it.isNotBlank() }?.trim()?.take(36) ?: "শিরোনামহীন পান্ডুলিপি"
    }
    val metrics = BengaliUtils.calculateMetrics(snapshot.content)
    val existing = snapshot.manuscriptId?.let { manuscriptDao.getById(it) }
    val preservedOriginal = existing?.originalContent?.ifEmpty { snapshot.content }
      ?: snapshot.originalContent.ifEmpty { snapshot.content }

    val entity = ManuscriptEntity(
      id = existing?.id ?: 0L,
      title = finalTitle,
      authorName = snapshot.authorName.trim(),
      category = snapshot.category.name,
      content = snapshot.content,
      originalContent = preservedOriginal,
      folderId = snapshot.folderId,
      preface = snapshot.preface,
      dedication = snapshot.dedication,
      epilogue = snapshot.epilogue,
      wordCount = metrics.words,
      charCount = metrics.characters,
      paragraphCount = metrics.paragraphs,
      isPinned = existing?.isPinned ?: false,
      createdAt = existing?.createdAt ?: now,
      updatedAt = now
    )
    val savedId = if (existing == null) {
      manuscriptDao.insert(entity)
    } else {
      manuscriptDao.update(entity)
      existing.id
    }
    if (versionDao.getLatestVersion(savedId) == null) {
      versionDao.insert(
        DraftVersionEntity(
          manuscriptId = savedId,
          versionNumber = 1,
          label = "মূল পান্ডুলিপি (সংস্করণ ১)",
          changeSummary = "স্বয়ংক্রিয়ভাবে সংরক্ষিত খসড়া",
          content = snapshot.content,
          wordCount = metrics.words,
          sourceType = VersionSourceType.INITIAL.name,
          createdAt = now
        )
      )
    }
  }

  fun createNewManuscript(
    category: ManuscriptCategory = ManuscriptCategory.PROSE,
    saveCurrentIfDirty: Boolean = true
  ) {
    autoSaveJob?.cancel()
    val previousState = _editor.value
    if (saveCurrentIfDirty && previousState.isDirty && (previousState.content.isNotBlank() || previousState.title.isNotBlank())) {
      viewModelScope.launch {
        persistDirtySnapshot(previousState)
      }
    }
    _editor.value = EditorUiState(
      manuscriptId = null,
      title = "",
      authorName = "",
      category = category,
      content = "",
      originalContent = "",
      isDirty = false,
      lastSavedLabel = "নতুন খসড়া"
    )
    clearAnalysisReports()
  }

  fun openManuscript(manuscriptId: Long) {
    viewModelScope.launch {
      autoSaveJob?.cancel()
      val currentState = _editor.value
      if (currentState.isDirty && currentState.manuscriptId != manuscriptId) {
        persistDirtySnapshot(currentState)
      }
      val item = manuscriptDao.getById(manuscriptId) ?: return@launch
      _editor.value = EditorUiState(
        manuscriptId = item.id,
        title = item.title,
        authorName = item.authorName,
        category = ManuscriptCategory.fromName(item.category),
        content = item.content,
        originalContent = item.originalContent,
        folderId = item.folderId,
        preface = item.preface,
        dedication = item.dedication,
        epilogue = item.epilogue,
        isDirty = false,
        lastSavedLabel = "সংরক্ষিত: ${BengaliUtils.formatBengaliDate(item.updatedAt)}"
      )
      clearAnalysisReports()
    }
  }

  fun updateTitle(title: String) {
    _editor.update { it.copy(title = title, isDirty = true) }
    scheduleAutoSave()
  }

  fun updateAuthorName(author: String) {
    _editor.update { it.copy(authorName = author, isDirty = true) }
    scheduleAutoSave()
  }

  fun updateCategory(category: ManuscriptCategory) {
    _editor.update { it.copy(category = category, isDirty = true) }
    scheduleAutoSave()
  }

  fun updateFolderId(folderId: Long?) {
    _editor.update { it.copy(folderId = folderId, isDirty = true) }
    scheduleAutoSave()
  }

  fun updateBookMetadata(preface: String, dedication: String, epilogue: String) {
    _editor.update {
      it.copy(preface = preface, dedication = dedication, epilogue = epilogue, isDirty = true)
    }
    scheduleAutoSave()
  }

  fun updateContent(newContent: String, pushUndo: Boolean = true) {
    val prev = _editor.value.content
    if (newContent == prev) return
    _editor.update { state ->
      val updatedUndo = if (pushUndo) {
        trimHistoryStack(state.undoStack + prev)
      } else {
        state.undoStack
      }
      state.copy(
        content = newContent,
        originalContent = if (state.manuscriptId == null && state.originalContent.isEmpty()) newContent else state.originalContent,
        undoStack = updatedUndo,
        redoStack = if (pushUndo) emptyList() else state.redoStack,
        isDirty = true
      )
    }
    scheduleAutoSave()
  }

  fun undo() {
    val state = _editor.value
    if (state.undoStack.isEmpty()) return
    val previous = state.undoStack.last()
    _editor.update {
      it.copy(
        content = previous,
        undoStack = it.undoStack.dropLast(1),
        redoStack = trimHistoryStack(it.redoStack + state.content),
        isDirty = true
      )
    }
    scheduleAutoSave()
  }

  fun redo() {
    val state = _editor.value
    if (state.redoStack.isEmpty()) return
    val next = state.redoStack.last()
    _editor.update {
      it.copy(
        content = next,
        undoStack = trimHistoryStack(it.undoStack + state.content),
        redoStack = it.redoStack.dropLast(1),
        isDirty = true
      )
    }
    scheduleAutoSave()
  }

  fun insertHeading(headingText: String) {
    val clean = headingText.trim()
    if (clean.isEmpty()) return
    val current = _editor.value.content
    val prefix = if (current.isBlank()) "" else "\n\n"
    updateContent("$current$prefix# $clean\n\n")
    notify("শিরোনাম যুক্ত হয়েছে।")
  }

  fun findAndReplaceAll(findText: String, replaceText: String): Int {
    if (findText.isEmpty()) return 0
    val current = _editor.value.content
    val count = Regex.escape(findText).toRegex().findAll(current).count()
    if (count == 0) {
      notify("‘$findText’ শব্দটি পাওয়া যায়নি।")
      return 0
    }
    val updated = current.replace(findText, replaceText)
    updateContent(updated)
    notify("মোট ${BengaliUtils.toBengaliDigits(count)}টি স্থানে প্রতিস্থাপন সম্পন্ন হয়েছে।")
    return count
  }

  private fun scheduleAutoSave() {
    if (!preferences.value.autoSaveEnabled) return
    autoSaveJob?.cancel()
    autoSaveJob = viewModelScope.launch {
      delay(2200L)
      val state = _editor.value
      if (state.isDirty && (state.content.isNotBlank() || state.title.isNotBlank())) {
        saveManuscriptInternal(
          createSnapshot = false,
          versionLabel = "স্বয়ংক্রিয় খসড়া",
          changeSummary = "স্বয়ংক্রিয়ভাবে সংরক্ষিত খসড়া",
          sourceType = VersionSourceType.AUTO_DRAFT,
          silent = true
        )
      }
    }
  }

  fun saveManuscriptWithSnapshot(
    customLabel: String = "",
    changeSummary: String = "ব্যবহারকারীর সংরক্ষিত সংস্করণ",
    sourceType: VersionSourceType = VersionSourceType.MANUAL_SAVE
  ) {
    viewModelScope.launch {
      saveManuscriptInternal(
        createSnapshot = true,
        versionLabel = customLabel,
        changeSummary = changeSummary,
        sourceType = sourceType,
        silent = false
      )
    }
  }

  private suspend fun saveManuscriptInternal(
    createSnapshot: Boolean,
    versionLabel: String,
    changeSummary: String,
    sourceType: VersionSourceType,
    silent: Boolean
  ): Long? {
    val state = _editor.value
    if (state.title.isBlank() && state.content.isBlank()) {
      if (!silent) notify("সংরক্ষণের আগে শিরোনাম বা লেখা যুক্ত করুন।")
      return null
    }

    val now = System.currentTimeMillis()
    val finalTitle = state.title.trim().ifBlank {
      state.content.lines().firstOrNull { it.isNotBlank() }?.trim()?.take(36) ?: "শিরোনামহীন পান্ডুলিপি"
    }
    val metrics = BengaliUtils.calculateMetrics(state.content)
    val existingId = state.manuscriptId
    val existing = existingId?.let { manuscriptDao.getById(it) }

    val preservedOriginal = existing?.originalContent?.ifEmpty { state.content }
      ?: state.originalContent.ifEmpty { state.content }

    val entity = ManuscriptEntity(
      id = existing?.id ?: 0L,
      title = finalTitle,
      authorName = state.authorName.trim(),
      category = state.category.name,
      content = state.content,
      originalContent = preservedOriginal,
      folderId = state.folderId,
      preface = state.preface,
      dedication = state.dedication,
      epilogue = state.epilogue,
      wordCount = metrics.words,
      charCount = metrics.characters,
      paragraphCount = metrics.paragraphs,
      isPinned = existing?.isPinned ?: false,
      createdAt = existing?.createdAt ?: now,
      updatedAt = now
    )

    val savedId = if (existing == null) {
      manuscriptDao.insert(entity)
    } else {
      manuscriptDao.update(entity)
      existing.id
    }

    // Version Control: always create initial version #1; create subsequent snapshot when requested or content changed significantly
    val latestVer = versionDao.getLatestVersion(savedId)
    val shouldCreateVersion = latestVer == null ||
      (createSnapshot && latestVer.content != state.content) ||
      (sourceType == VersionSourceType.AUTO_DRAFT && latestVer != null &&
        kotlin.math.abs(latestVer.content.length - state.content.length) >= 120)

    if (shouldCreateVersion) {
      val nextVerNum = versionDao.getMaxVersionNumber(savedId) + 1
      val resolvedLabel = versionLabel.ifBlank {
        if (nextVerNum == 1) "মূল পান্ডুলিপি (সংস্করণ ১)" else "সংস্করণ ${BengaliUtils.toBengaliDigits(nextVerNum)}"
      }
      versionDao.insert(
        DraftVersionEntity(
          manuscriptId = savedId,
          versionNumber = nextVerNum,
          label = resolvedLabel,
          changeSummary = changeSummary,
          content = state.content,
          wordCount = metrics.words,
          sourceType = if (nextVerNum == 1) VersionSourceType.INITIAL.name else sourceType.name,
          createdAt = now
        )
      )
    }

    _editor.update {
      it.copy(
        manuscriptId = savedId,
        title = finalTitle,
        originalContent = preservedOriginal,
        isDirty = false,
        lastSavedLabel = if (silent) {
          "স্বয়ংক্রিয় সংরক্ষিত: ${BengaliUtils.formatBengaliDate(now)}"
        } else {
          "সংরক্ষিত: ${BengaliUtils.formatBengaliDate(now)}"
        }
      )
    }
    if (!silent) notify("পান্ডুলিপি ও নতুন সংস্করণ সংরক্ষিত হয়েছে।")
    return savedId
  }

  fun restoreDraftVersion(version: DraftVersionEntity) {
    viewModelScope.launch {
      updateContent(version.content, pushUndo = true)
      saveManuscriptInternal(
        createSnapshot = true,
        versionLabel = "পুনরুদ্ধার (সংস্করণ ${BengaliUtils.toBengaliDigits(version.versionNumber)} থেকে)",
        changeSummary = "সংস্করণ ${BengaliUtils.toBengaliDigits(version.versionNumber)} (${version.label}) পুনরুদ্ধার করা হয়েছে",
        sourceType = VersionSourceType.RESTORE,
        silent = false
      )
    }
  }

  fun restoreOriginalManuscript() {
    val orig = _editor.value.originalContent
    if (orig.isBlank()) return
    updateContent(orig, pushUndo = true)
    saveManuscriptWithSnapshot(
      customLabel = "মূল পান্ডুলিপি পুনরুদ্ধার",
      changeSummary = "মূল সংরক্ষিত পান্ডুলিপি ফিরিয়ে আনা হয়েছে",
      sourceType = VersionSourceType.RESTORE
    )
  }

  // =========================================================================
  // LIBRARY & FOLDER MANAGEMENT
  // =========================================================================

  fun setSearchQuery(q: String) { _searchQuery.value = q }
  fun setCategoryFilter(cat: ManuscriptCategory?) { _selectedCategoryFilter.value = cat }
  fun setFolderFilter(folderId: Long?) { _selectedFolderFilter.value = folderId }
  fun setSortOption(option: ManuscriptSortOption) { _sortOption.value = option }

  fun createFolder(name: String) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      folderDao.insert(FolderEntity(name = clean))
      notify("‘$clean’ ফোল্ডার তৈরি হয়েছে।")
    }
  }

  fun deleteFolder(folder: FolderEntity) {
    viewModelScope.launch {
      folderDao.delete(folder)
      if (_selectedFolderFilter.value == folder.id) {
        _selectedFolderFilter.value = null
      }
      notify("ফোল্ডারটি মুছে ফেলা হয়েছে।")
    }
  }

  fun renameManuscript(manuscript: ManuscriptEntity, newTitle: String) {
    val clean = newTitle.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      manuscriptDao.update(manuscript.copy(title = clean, updatedAt = System.currentTimeMillis()))
      if (_editor.value.manuscriptId == manuscript.id) {
        _editor.update { it.copy(title = clean) }
      }
      notify("পান্ডুলিপির নাম পরিবর্তন করা হয়েছে।")
    }
  }

  fun moveManuscriptToFolder(manuscript: ManuscriptEntity, folderId: Long?) {
    viewModelScope.launch {
      manuscriptDao.update(manuscript.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
      if (_editor.value.manuscriptId == manuscript.id) {
        _editor.update { it.copy(folderId = folderId) }
      }
      notify("ফোল্ডার হালনাগাদ হয়েছে।")
    }
  }

  fun togglePinManuscript(manuscript: ManuscriptEntity) {
    viewModelScope.launch {
      manuscriptDao.update(manuscript.copy(isPinned = !manuscript.isPinned))
    }
  }

  fun deleteManuscript(manuscriptId: Long) {
    viewModelScope.launch {
      manuscriptDao.deleteById(manuscriptId)
      if (_editor.value.manuscriptId == manuscriptId) {
        createNewManuscript(saveCurrentIfDirty = false)
      }
      notify("পান্ডুলিপিটি স্থায়ীভাবে মুছে ফেলা হয়েছে।")
    }
  }

  // =========================================================================
  // 4 SEPARATE AI INTELLIGENCE OPERATIONS (WITH CANCELLATION & REAL PROGRESS)
  // =========================================================================

  fun cancelActiveAnalysis() {
    analysisJob?.cancel()
    analysisJob = null
    _analysis.update { it.copy(isRunning = false, progress = null, activeOperation = null) }
    notify("বিশ্লেষণ বাতিল করা হয়েছে।")
  }

  fun clearAnalysisError() {
    _analysis.update { it.copy(errorMessage = null) }
  }

  private fun clearAnalysisReports() {
    _analysis.update {
      it.copy(
        errorMessage = null,
        proofreadingReport = null,
        acceptedIssueIds = emptySet(),
        rejectedIssueIds = emptySet(),
        aiDetectionReport = null,
        refinementReport = null,
        selectedRefinementChangeIds = emptySet(),
        organizationReport = null
      )
    }
  }

  fun runOfflineProofreading() {
    runProofreading(mode = ProofreadingMode.QUICK, forceOffline = true)
  }

  fun runProofreading(mode: ProofreadingMode, forceOffline: Boolean = false) {
    val state = _editor.value
    if (state.content.isBlank()) {
      notify("প্রুফরিডিংয়ের জন্য আগে কিছু বাংলা লেখা যুক্ত করুন।")
      return
    }
    analysisJob?.cancel()
    analysisJob = viewModelScope.launch {
      val savedId = saveManuscriptInternal(
        createSnapshot = false,
        versionLabel = "",
        changeSummary = "প্রুফরিডিংয়ের পূর্বের খসড়া",
        sourceType = VersionSourceType.AUTO_DRAFT,
        silent = true
      )
      _analysis.update {
        it.copy(
          isRunning = true,
          activeOperation = AnalysisType.PROOFREADING,
          errorMessage = null,
          proofreadingReport = null,
          acceptedIssueIds = emptySet(),
          rejectedIssueIds = emptySet()
        )
      }
      try {
        val onlineNow = connectivity.refreshNow()
        val report = aiEngine.proofreadManuscript(
          manuscriptId = savedId,
          manuscriptTitle = _editor.value.title,
          text = state.content,
          category = state.category,
          mode = mode,
          useCache = preferences.value.useCacheForUnchangedText,
          isOnline = onlineNow,
          forceOffline = forceOffline,
          poetryProtectionStrict = preferences.value.poetryProtectionStrict,
          onProgress = { progress ->
            _analysis.update { it.copy(progress = progress) }
          },
          onPartialResult = { partial ->
            _analysis.update { it.copy(proofreadingReport = partial) }
          }
        )
        _analysis.update {
          it.copy(
            isRunning = false,
            progress = null,
            activeOperation = null,
            proofreadingReport = report
          )
        }
      } catch (_: CancellationException) {
        _analysis.update { it.copy(isRunning = false, progress = null, activeOperation = null) }
      } catch (e: AiServiceException) {
        _analysis.update {
          it.copy(isRunning = false, progress = null, activeOperation = null, errorMessage = e.bengaliMessage)
        }
      } catch (e: Exception) {
        _analysis.update {
          it.copy(
            isRunning = false,
            progress = null,
            activeOperation = null,
            errorMessage = "প্রুফরিডিং সম্পন্ন করা যায়নি: ${e.localizedMessage ?: "অজানা ত্রুটি"}"
          )
        }
      }
    }
  }

  fun acceptSingleProofreadingIssue(issue: ProofreadingIssue) {
    val current = _editor.value.content
    val updated = if (issue.originalText.isNotBlank()) {
      BengaliLocalAnalyzer.replaceFirstSafely(current, issue.originalText, issue.suggestedText)
    } else {
      current
    }
    if (updated != current) {
      updateContent(updated, pushUndo = true)
      _analysis.update {
        it.copy(
          acceptedIssueIds = it.acceptedIssueIds + issue.id,
          rejectedIssueIds = it.rejectedIssueIds - issue.id
        )
      }
      notify("সংশোধনী প্রয়োগ করা হয়েছে।")
    } else {
      notify("মূল অংশটি বর্তমান লেখায় আর মিলছে না (সম্ভবত ইতোমধ্যেই পরিবর্তিত হয়েছে)।")
    }
  }

  fun rejectSingleProofreadingIssue(issue: ProofreadingIssue) {
    _analysis.update {
      it.copy(
        rejectedIssueIds = it.rejectedIssueIds + issue.id,
        acceptedIssueIds = it.acceptedIssueIds - issue.id
      )
    }
  }

  fun acceptAllProofreadingIssues() {
    val report = _analysis.value.proofreadingReport ?: return
    val remaining = report.issues.filter { it.id !in _analysis.value.rejectedIssueIds && it.id !in _analysis.value.acceptedIssueIds }
    val newText = if (_analysis.value.rejectedIssueIds.isEmpty() && _analysis.value.acceptedIssueIds.isEmpty() && report.suggestedFullText.isNotBlank()) {
      report.suggestedFullText
    } else {
      aiEngine.applyIssuesSequentially(_editor.value.content, remaining)
    }
    updateContent(newText, pushUndo = true)
    _analysis.update { it.copy(acceptedIssueIds = report.issues.map { i -> i.id }.toSet()) }
    saveManuscriptWithSnapshot(
      customLabel = "প্রুফরিডিং সংশোধিত সংস্করণ",
      changeSummary = "${BengaliUtils.toBengaliDigits(remaining.size)}টি প্রুফরিডিং সংশোধনী প্রয়োগ করা হয়েছে",
      sourceType = VersionSourceType.PROOFREAD_APPLY
    )
  }

  fun runAiDetection() {
    val state = _editor.value
    if (state.content.isBlank()) {
      notify("AI লেখা শনাক্তকরণের জন্য আগে পান্ডুলিপি লিখুন বা আমদানি করুন।")
      return
    }
    if (!connectivity.refreshNow()) {
      _analysis.update {
        it.copy(
          isRunning = false,
          progress = null,
          activeOperation = null,
          errorMessage = "বর্তমানে অফলাইন মোডে আছেন। AI লেখা শনাক্তকরণের জন্য ইন্টারনেট সংযোগ প্রয়োজন—ইন্টারনেট ফিরে এলে অনলাইন মোড স্বয়ংক্রিয়ভাবে চালু হবে।"
        )
      }
      return
    }
    analysisJob?.cancel()
    analysisJob = viewModelScope.launch {
      val savedId = saveManuscriptInternal(
        createSnapshot = false,
        versionLabel = "",
        changeSummary = "AI শনাক্তকরণের পূর্বের খসড়া",
        sourceType = VersionSourceType.AUTO_DRAFT,
        silent = true
      )
      _analysis.update {
        it.copy(
          isRunning = true,
          activeOperation = AnalysisType.AI_DETECTION,
          errorMessage = null,
          aiDetectionReport = null
        )
      }
      try {
        val report = aiEngine.detectAiWriting(
          manuscriptId = savedId,
          manuscriptTitle = _editor.value.title,
          text = state.content,
          category = state.category,
          useCache = preferences.value.useCacheForUnchangedText,
          onProgress = { p -> _analysis.update { it.copy(progress = p) } }
        )
        _analysis.update {
          it.copy(isRunning = false, progress = null, activeOperation = null, aiDetectionReport = report)
        }
      } catch (_: CancellationException) {
        _analysis.update { it.copy(isRunning = false, progress = null, activeOperation = null) }
      } catch (e: AiServiceException) {
        _analysis.update {
          it.copy(isRunning = false, progress = null, activeOperation = null, errorMessage = e.bengaliMessage)
        }
      } catch (e: Exception) {
        _analysis.update {
          it.copy(
            isRunning = false,
            progress = null,
            activeOperation = null,
            errorMessage = "শনাক্তকরণ বিশ্লেষণ ব্যর্থ হয়েছে: ${e.localizedMessage ?: "অজানা ত্রুটি"}"
          )
        }
      }
    }
  }

  fun runHumanRefinement(mode: RefinementMode) {
    val state = _editor.value
    if (state.content.isBlank()) {
      notify("মানবিক ভাষায় রূপান্তরের জন্য আগে পান্ডুলিপি যুক্ত করুন।")
      return
    }
    if (!connectivity.refreshNow()) {
      _analysis.update {
        it.copy(
          isRunning = false,
          progress = null,
          activeOperation = null,
          errorMessage = "বর্তমানে অফলাইন মোডে আছেন। মানবিক ভাষায় রূপান্তরের জন্য ইন্টারনেট সংযোগ প্রয়োজন—ইন্টারনেট ফিরে এলে অনলাইন মোড স্বয়ংক্রিয়ভাবে চালু হবে।"
        )
      }
      return
    }
    analysisJob?.cancel()
    analysisJob = viewModelScope.launch {
      val savedId = saveManuscriptInternal(
        createSnapshot = false,
        versionLabel = "",
        changeSummary = "মানবিক রূপান্তরের পূর্বের খসড়া",
        sourceType = VersionSourceType.AUTO_DRAFT,
        silent = true
      )
      _analysis.update {
        it.copy(
          isRunning = true,
          activeOperation = AnalysisType.HUMAN_REFINEMENT,
          errorMessage = null,
          refinementReport = null,
          selectedRefinementChangeIds = emptySet()
        )
      }
      try {
        val report = aiEngine.refineHumanLanguage(
          manuscriptId = savedId,
          manuscriptTitle = _editor.value.title,
          text = state.content,
          category = state.category,
          mode = mode,
          useCache = preferences.value.useCacheForUnchangedText,
          poetryProtectionStrict = preferences.value.poetryProtectionStrict,
          onProgress = { p -> _analysis.update { it.copy(progress = p) } }
        )
        _analysis.update {
          it.copy(
            isRunning = false,
            progress = null,
            activeOperation = null,
            refinementReport = report,
            selectedRefinementChangeIds = report.changes.map { c -> c.id }.toSet()
          )
        }
      } catch (_: CancellationException) {
        _analysis.update { it.copy(isRunning = false, progress = null, activeOperation = null) }
      } catch (e: AiServiceException) {
        _analysis.update {
          it.copy(isRunning = false, progress = null, activeOperation = null, errorMessage = e.bengaliMessage)
        }
      } catch (e: Exception) {
        _analysis.update {
          it.copy(
            isRunning = false,
            progress = null,
            activeOperation = null,
            errorMessage = "মানবিক ভাষায় রূপান্তর ব্যর্থ হয়েছে: ${e.localizedMessage ?: "অজানা ত্রুটি"}"
          )
        }
      }
    }
  }

  fun toggleRefinementChangeSelection(changeId: String) {
    _analysis.update { state ->
      val next = if (changeId in state.selectedRefinementChangeIds) {
        state.selectedRefinementChangeIds - changeId
      } else {
        state.selectedRefinementChangeIds + changeId
      }
      state.copy(selectedRefinementChangeIds = next)
    }
  }

  fun applyAllRefinement() {
    val report = _analysis.value.refinementReport ?: return
    updateContent(report.refinedFullText, pushUndo = true)
    saveManuscriptWithSnapshot(
      customLabel = "মানবিক ভাষায় পরিমার্জিত",
      changeSummary = "মানবিক ভাষায় রূপান্তরের সমস্ত প্রস্তাবনা প্রয়োগ করা হয়েছে",
      sourceType = VersionSourceType.REFINEMENT_APPLY
    )
  }

  fun applySelectedRefinementChanges() {
    val report = _analysis.value.refinementReport ?: return
    val selected = report.changes.filter { it.id in _analysis.value.selectedRefinementChangeIds }
    if (selected.isEmpty()) {
      notify("অন্তত একটি পরিবর্তন নির্বাচন করুন।")
      return
    }
    var updated = _editor.value.content
    var appliedCount = 0
    for (change: RefinementChange in selected) {
      if (change.originalSegment.isNotBlank()) {
        val next = BengaliLocalAnalyzer.replaceFirstSafely(updated, change.originalSegment, change.refinedSegment)
        if (next != updated) {
          updated = next
          appliedCount++
        }
      }
    }
    if (appliedCount == 0) {
      updated = report.refinedFullText
      appliedCount = selected.size
    }
    updateContent(updated, pushUndo = true)
    saveManuscriptWithSnapshot(
      customLabel = "নির্বাচিত পরিমার্জন প্রয়োগ",
      changeSummary = "${BengaliUtils.toBengaliDigits(appliedCount)}টি নির্বাচিত মানবিক পরিমার্জন প্রয়োগ করা হয়েছে",
      sourceType = VersionSourceType.REFINEMENT_APPLY
    )
  }

  fun rejectRefinement() {
    _analysis.update { it.copy(refinementReport = null, selectedRefinementChangeIds = emptySet()) }
    notify("পরিমার্জন প্রস্তাবনা বাতিল করা হয়েছে; মূল লেখা অপরিবর্তিত আছে।")
  }

  fun runManuscriptOrganizer(includeAiDeepAnalysis: Boolean = true) {
    val state = _editor.value
    if (state.content.isBlank()) {
      notify("পান্ডুলিপি সাজানোর জন্য আগে লেখা যুক্ত করুন।")
      return
    }
    analysisJob?.cancel()
    analysisJob = viewModelScope.launch {
      val savedId = saveManuscriptInternal(
        createSnapshot = false,
        versionLabel = "",
        changeSummary = "পান্ডুলিপি বিন্যাসের পূর্বের খসড়া",
        sourceType = VersionSourceType.AUTO_DRAFT,
        silent = true
      )
      _analysis.update {
        it.copy(
          isRunning = true,
          activeOperation = AnalysisType.MANUSCRIPT_ORGANIZER,
          errorMessage = null,
          organizationReport = null
        )
      }
      try {
        val report = aiEngine.organizeManuscript(
          manuscriptId = savedId,
          manuscriptTitle = _editor.value.title,
          text = state.content,
          category = state.category,
          includeAiDeepConsistency = includeAiDeepAnalysis && connectivity.refreshNow(),
          onProgress = { p -> _analysis.update { it.copy(progress = p) } }
        )
        _analysis.update {
          it.copy(isRunning = false, progress = null, activeOperation = null, organizationReport = report)
        }
      } catch (_: CancellationException) {
        _analysis.update { it.copy(isRunning = false, progress = null, activeOperation = null) }
      } catch (e: Exception) {
        _analysis.update {
          it.copy(
            isRunning = false,
            progress = null,
            activeOperation = null,
            errorMessage = "পান্ডুলিপি বিন্যাস বিশ্লেষণে ত্রুটি হয়েছে: ${e.localizedMessage ?: "অজানা ত্রুটি"}"
          )
        }
      }
    }
  }

  fun applyApprovedOrganizationFormatting() {
    val report = _analysis.value.organizationReport ?: return
    if (report.proposedFormattedContent.isBlank()) return
    updateContent(report.proposedFormattedContent, pushUndo = true)
    saveManuscriptWithSnapshot(
      customLabel = "বিন্যস্ত পান্ডুলিপি",
      changeSummary = "অনুমোদিত পান্ডুলিপি ফরম্যাটিং ও অনুচ্ছেদ বিন্যাস প্রয়োগ করা হয়েছে",
      sourceType = VersionSourceType.ORGANIZE_APPLY
    )
  }

  // =========================================================================
  // HISTORY RE-OPENING (ZERO REDUNDANT AI CALLS)
  // =========================================================================

  fun reopenHistoryReport(entry: AnalysisHistoryEntity): AnalysisType {
    val type = AnalysisType.fromName(entry.analysisType)
    when (type) {
      AnalysisType.PROOFREADING -> {
        val decoded = aiEngine.decodeProofreadingReport(entry.resultJson)
        if (decoded != null) {
          _analysis.update { it.copy(proofreadingReport = decoded.copy(fromCache = true), errorMessage = null) }
        }
      }
      AnalysisType.AI_DETECTION -> {
        val decoded = aiEngine.decodeAiDetectionReport(entry.resultJson)
        if (decoded != null) {
          _analysis.update { it.copy(aiDetectionReport = decoded.copy(fromCache = true), errorMessage = null) }
        }
      }
      AnalysisType.HUMAN_REFINEMENT -> {
        val decoded = aiEngine.decodeRefinementReport(entry.resultJson)
        if (decoded != null) {
          _analysis.update {
            it.copy(
              refinementReport = decoded.copy(fromCache = true),
              selectedRefinementChangeIds = decoded.changes.map { c -> c.id }.toSet(),
              errorMessage = null
            )
          }
        }
      }
      AnalysisType.MANUSCRIPT_ORGANIZER -> {
        val decoded = aiEngine.decodeOrganizationReport(entry.resultJson)
        if (decoded != null) {
          _analysis.update { it.copy(organizationReport = decoded, errorMessage = null) }
        }
      }
    }
    if (entry.manuscriptId != null && _editor.value.manuscriptId != entry.manuscriptId) {
      viewModelScope.launch {
        val ms = manuscriptDao.getById(entry.manuscriptId)
        if (ms != null) {
          _editor.update {
            it.copy(
              manuscriptId = ms.id,
              title = ms.title,
              authorName = ms.authorName,
              category = ManuscriptCategory.fromName(ms.category),
              content = ms.content,
              originalContent = ms.originalContent,
              folderId = ms.folderId,
              preface = ms.preface,
              dedication = ms.dedication,
              epilogue = ms.epilogue,
              isDirty = false
            )
          }
        }
      }
    }
    return type
  }

  fun deleteHistoryEntry(id: Long) {
    viewModelScope.launch {
      historyDao.deleteById(id)
      notify("ইতিহাসের নথি মুছে ফেলা হয়েছে।")
    }
  }

  fun clearAllHistory() {
    viewModelScope.launch {
      historyDao.clearAll()
      notify("সমস্ত বিশ্লেষণ ক্যাশ ও ইতিহাস মুছে ফেলা হয়েছে।")
    }
  }

  // =========================================================================
  // FILE IMPORT, EXPORT & NATIVE SHARING
  // =========================================================================

  fun importDocument(uri: Uri) {
    viewModelScope.launch {
      _editor.update { it.copy(importStatusMessage = "ফাইল পড়া হচ্ছে…") }
      val result = docManager.importFromUri(uri) { status ->
        _editor.update { it.copy(importStatusMessage = status) }
      }
      _editor.update { it.copy(importStatusMessage = null) }
      result.onSuccess { doc ->
        autoSaveJob?.cancel()
        persistDirtySnapshot(_editor.value)
        _editor.value = EditorUiState(
          manuscriptId = null,
          title = doc.title,
          category = _editor.value.category,
          content = doc.content,
          originalContent = doc.content,
          isDirty = true,
          lastSavedLabel = "আমদানিকৃত (${doc.format})"
        )
        saveManuscriptInternal(
          createSnapshot = true,
          versionLabel = "আমদানিকৃত মূল ফাইল (${doc.format})",
          changeSummary = doc.statusNote,
          sourceType = VersionSourceType.INITIAL,
          silent = true
        )
        notify(doc.statusNote)
      }.onFailure { err ->
        val msg = (err as? AiServiceException)?.bengaliMessage
          ?: err.localizedMessage
          ?: "ফাইল আমদানি করা সম্ভব হয়নি।"
        notify(msg)
      }
    }
  }

  fun shareActiveManuscript(format: ExportFormat) {
    val state = _editor.value
    if (state.content.isBlank()) {
      notify("শেয়ার করার জন্য আগে লেখা যুক্ত করুন।")
      return
    }
    viewModelScope.launch {
      val entity = buildTransientEntityFromEditor(state)
      val res = docManager.exportToFile(entity, format, preferences.value.editorFontSizeSp)
      res.onSuccess { file ->
        docManager.shareExportedFile(file, format)
      }.onFailure { err ->
        notify("এক্সপোর্ট ব্যর্থ হয়েছে: ${err.localizedMessage ?: "ত্রুটি"}")
      }
    }
  }

  fun exportActiveManuscriptToUri(format: ExportFormat, uri: Uri) {
    val state = _editor.value
    if (state.content.isBlank()) {
      notify("সংরক্ষণের জন্য আগে লেখা যুক্ত করুন।")
      return
    }
    viewModelScope.launch {
      val entity = buildTransientEntityFromEditor(state)
      val res = docManager.exportToUri(entity, format, uri, preferences.value.editorFontSizeSp)
      res.onSuccess {
        notify("${format.extension.uppercase()} ফাইল সফলভাবে সংরক্ষিত হয়েছে।")
      }.onFailure { err ->
        notify("ফাইল সংরক্ষণ ব্যর্থ হয়েছে: ${err.localizedMessage ?: "ত্রুটি"}")
      }
    }
  }

  fun exportAllManuscriptsJsonBackup(uri: Uri) {
    viewModelScope.launch {
      val res = withContext(Dispatchers.IO) {
        runCatching {
          val all = manuscriptDao.getAllSnapshot()
          val arr = JSONArray()
          for (m in all) {
            arr.put(
              JSONObject().apply {
                put("title", m.title)
                put("authorName", m.authorName)
                put("category", m.category)
                put("content", m.content)
                put("originalContent", m.originalContent)
                put("preface", m.preface)
                put("dedication", m.dedication)
                put("epilogue", m.epilogue)
                put("updatedAt", m.updatedAt)
              }
            )
          }
          val root = JSONObject()
            .put("app", "Sobdosuddhi 1.0")
            .put("exportedAt", System.currentTimeMillis())
            .put("manuscripts", arr)
          app.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
          } ?: throw IllegalStateException("ব্যাকআপ ফাইল লেখা যায়নি।")
        }
      }
      res.onSuccess { notify("সমস্ত পান্ডুলিপির ব্যক্তিগত ব্যাকআপ (JSON) সংরক্ষিত হয়েছে।") }
        .onFailure { notify("ব্যাকআপ তৈরি ব্যর্থ হয়েছে।") }
    }
  }

  private fun buildTransientEntityFromEditor(state: EditorUiState): ManuscriptEntity {
    val metrics = BengaliUtils.calculateMetrics(state.content)
    return ManuscriptEntity(
      id = state.manuscriptId ?: 0L,
      title = state.title.ifBlank { "শিরোনামহীন পান্ডুলিপি" },
      authorName = state.authorName,
      category = state.category.name,
      content = state.content,
      originalContent = state.originalContent.ifBlank { state.content },
      folderId = state.folderId,
      preface = state.preface,
      dedication = state.dedication,
      epilogue = state.epilogue,
      wordCount = metrics.words,
      charCount = metrics.characters,
      paragraphCount = metrics.paragraphs
    )
  }

  // =========================================================================
  // PREFERENCES / SETTINGS
  // =========================================================================

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { prefsRepo.setThemeMode(mode) }
  }

  fun setEditorFontSize(sizeSp: Float) {
    viewModelScope.launch { prefsRepo.setEditorFontSize(sizeSp) }
  }

  fun setDefaultProofreadingMode(mode: ProofreadingMode) {
    viewModelScope.launch { prefsRepo.setDefaultProofreadingMode(mode) }
  }

  fun setAutoSaveEnabled(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setAutoSaveEnabled(enabled) }
  }

  fun setPoetryProtectionStrict(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setPoetryProtectionStrict(enabled) }
  }

  fun setUseCacheForUnchangedText(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setUseCacheForUnchangedText(enabled) }
  }

  fun setCustomProxyUrl(url: String) {
    val clean = url.trim().trimEnd('/')
    if (clean.isNotEmpty() && !clean.startsWith("https://") && !clean.startsWith("http://")) {
      notify("সঠিক HTTPS সার্ভার URL দিন (যেমন: https://your-server.run.app)।")
      return
    }
    viewModelScope.launch {
      prefsRepo.setCustomProxyUrl(clean)
      app.geminiClient.updateProxyBaseUrl(clean)
      _serverHealth.value = null
      _analysis.update { it.copy(errorMessage = null) }
      notify(
        if (clean.isEmpty()) "ডিফল্ট সার্ভার প্রোক্সি পুনর্বহাল হয়েছে।"
        else "AI সার্ভার প্রোক্সি URL সংরক্ষিত হয়েছে।"
      )
    }
  }

  fun testAiServerConnection() {
    if (_isCheckingServer.value) return
    viewModelScope.launch {
      _isCheckingServer.value = true
      try {
        val result = app.geminiClient.checkServerHealth()
        _serverHealth.value = result
      } finally {
        _isCheckingServer.value = false
      }
    }
  }
}
