package com.sobdosuddhi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sobdosuddhi.app.domain.ExportFormat
import com.sobdosuddhi.app.model.AnalysisType
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.ThemeMode
import com.sobdosuddhi.app.ui.components.SobdosuddhiSplashScreen
import com.sobdosuddhi.app.ui.screens.AiDetectionScreen
import com.sobdosuddhi.app.ui.screens.EditorScreen
import com.sobdosuddhi.app.ui.screens.HistoryScreen
import com.sobdosuddhi.app.ui.screens.HomeScreen
import com.sobdosuddhi.app.ui.screens.LibraryScreen
import com.sobdosuddhi.app.ui.screens.OrganizerScreen
import com.sobdosuddhi.app.ui.screens.ProofreadingScreen
import com.sobdosuddhi.app.ui.screens.RefinementScreen
import com.sobdosuddhi.app.ui.screens.SettingsScreen
import com.sobdosuddhi.app.ui.theme.SobdosuddhiTheme
import com.sobdosuddhi.app.ui.viewmodel.SobdosuddhiViewModel

private enum class MainTab(val label: String, val icon: ImageVector) {
  HOME("হোম", Icons.Outlined.Home),
  EDITOR("নতুন লেখা", Icons.Outlined.Create),
  LIBRARY("আমার পান্ডুলিপি", Icons.Outlined.FolderCopy),
  HISTORY("ইতিহাস", Icons.Outlined.History),
  SETTINGS("সেটিংস", Icons.Outlined.Settings)
}

private enum class IntelligenceToolScreen {
  PROOFREADING,
  AI_DETECTION,
  HUMAN_REFINEMENT,
  ORGANIZER
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: SobdosuddhiViewModel = viewModel()
      val prefs by viewModel.preferences.collectAsState()
      val isDark = when (prefs.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
      }

      DisposableEffect(isDark) {
        enableEdgeToEdge(
          statusBarStyle = if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
          } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
          },
          navigationBarStyle = if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
          } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
          }
        )
        onDispose {}
      }

      SobdosuddhiTheme(themeMode = prefs.themeMode) {
        SobdosuddhiAppRoot(viewModel = viewModel)
      }
    }
  }
}

@Composable
private fun SobdosuddhiAppRoot(viewModel: SobdosuddhiViewModel) {
  var showSplash by rememberSaveable { mutableStateOf(true) }
  var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
  var activeTool by rememberSaveable { mutableStateOf<IntelligenceToolScreen?>(null) }

  val snackbarHostState = remember { SnackbarHostState() }
  val prefs by viewModel.preferences.collectAsState()
  val stats by viewModel.dashboardStats.collectAsState()
  val recentManuscripts by viewModel.recentManuscripts.collectAsState()
  val allManuscripts by viewModel.allManuscripts.collectAsState()
  val filteredManuscripts by viewModel.filteredManuscripts.collectAsState()
  val folders by viewModel.folders.collectAsState()
  val editorState by viewModel.editor.collectAsState()
  val activeVersions by viewModel.activeManuscriptVersions.collectAsState()
  val analysisState by viewModel.analysis.collectAsState()
  val analysisHistory by viewModel.analysisHistory.collectAsState()
  val recentVersions by viewModel.recentVersions.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val selectedCatFilter by viewModel.selectedCategoryFilter.collectAsState()
  val selectedFolderFilter by viewModel.selectedFolderFilter.collectAsState()
  val sortOption by viewModel.sortOption.collectAsState()
  val isOnline by viewModel.isOnline.collectAsState()
  val serverHealth by viewModel.serverHealth.collectAsState()
  val isCheckingServer by viewModel.isCheckingServer.collectAsState()

  var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }

  // Native Android Document Picker (DOCX, PDF, TXT, DOC)
  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      viewModel.importDocument(uri)
      activeTool = null
      selectedTab = MainTab.EDITOR
    }
  }

  // Native Android CreateDocument for DOCX / PDF / TXT Export
  val exportDocxLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(ExportFormat.DOCX.mimeType)
  ) { uri -> if (uri != null) viewModel.exportActiveManuscriptToUri(ExportFormat.DOCX, uri) }

  val exportPdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType)
  ) { uri -> if (uri != null) viewModel.exportActiveManuscriptToUri(ExportFormat.PDF, uri) }

  val exportTxtLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(ExportFormat.TXT.mimeType)
  ) { uri -> if (uri != null) viewModel.exportActiveManuscriptToUri(ExportFormat.TXT, uri) }

  // Native Android JSON Backup Export
  val backupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri -> if (uri != null) viewModel.exportAllManuscriptsJsonBackup(uri) }

  LaunchedEffect(pendingExportFormat) {
    val fmt = pendingExportFormat ?: return@LaunchedEffect
    pendingExportFormat = null
    val baseName = editorState.title.trim().ifBlank { "sobdosuddhi_manuscript" }
    when (fmt) {
      ExportFormat.DOCX -> exportDocxLauncher.launch("$baseName.docx")
      ExportFormat.PDF -> exportPdfLauncher.launch("$baseName.pdf")
      ExportFormat.TXT -> exportTxtLauncher.launch("$baseName.txt")
    }
  }

  LaunchedEffect(Unit) {
    viewModel.snackbarMessage.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  // Native Android Back Button handling
  BackHandler(enabled = activeTool != null || selectedTab != MainTab.HOME) {
    if (activeTool != null) {
      activeTool = null
    } else if (selectedTab != MainTab.HOME) {
      selectedTab = MainTab.HOME
    }
  }

  if (showSplash) {
    SobdosuddhiSplashScreen(onFinished = { showSplash = false })
    return
  }

  fun launchImportPicker() {
    importLauncher.launch(
      arrayOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/pdf",
        "text/plain",
        "application/msword",
        "*/*"
      )
    )
  }

  fun openToolWithActiveOrLatest(tool: IntelligenceToolScreen) {
    if (editorState.content.isBlank() && recentManuscripts.isNotEmpty()) {
      viewModel.openManuscript(recentManuscripts.first().id)
    }
    activeTool = tool
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (activeTool == null) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
          MainTab.entries.forEach { tab ->
            NavigationBarItem(
              selected = selectedTab == tab,
              onClick = { selectedTab = tab },
              icon = { Icon(tab.icon, contentDescription = tab.label) },
              label = {
                Text(
                  text = tab.label,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  style = MaterialTheme.typography.labelSmall
                )
              }
            )
          }
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      if (activeTool != null) {
        Crossfade(targetState = activeTool!!, label = "toolTransition") { tool ->
          when (tool) {
            IntelligenceToolScreen.PROOFREADING -> ProofreadingScreen(
              editorState = editorState,
              analysisState = analysisState,
              defaultMode = prefs.defaultProofreadingMode,
              isOnline = isOnline,
              onCategoryChange = viewModel::updateCategory,
              onRunProofreading = { mode -> viewModel.runProofreading(mode) },
              onRunOfflineProofreading = viewModel::runOfflineProofreading,
              onCancel = viewModel::cancelActiveAnalysis,
              onClearError = viewModel::clearAnalysisError,
              onAcceptIssue = viewModel::acceptSingleProofreadingIssue,
              onRejectIssue = viewModel::rejectSingleProofreadingIssue,
              onAcceptAll = viewModel::acceptAllProofreadingIssues,
              onOpenSettings = {
                activeTool = null
                selectedTab = MainTab.SETTINGS
              },
              onNavigateBack = { activeTool = null }
            )

            IntelligenceToolScreen.AI_DETECTION -> AiDetectionScreen(
              editorState = editorState,
              analysisState = analysisState,
              isOnline = isOnline,
              onRunDetection = viewModel::runAiDetection,
              onCancel = viewModel::cancelActiveAnalysis,
              onClearError = viewModel::clearAnalysisError,
              onNavigateBack = { activeTool = null }
            )

            IntelligenceToolScreen.HUMAN_REFINEMENT -> RefinementScreen(
              editorState = editorState,
              analysisState = analysisState,
              isOnline = isOnline,
              onRunRefinement = viewModel::runHumanRefinement,
              onCancel = viewModel::cancelActiveAnalysis,
              onClearError = viewModel::clearAnalysisError,
              onToggleChange = viewModel::toggleRefinementChangeSelection,
              onApplySelected = viewModel::applySelectedRefinementChanges,
              onApplyAll = viewModel::applyAllRefinement,
              onReject = viewModel::rejectRefinement,
              onRestoreOriginal = viewModel::restoreOriginalManuscript,
              onNavigateBack = { activeTool = null }
            )

            IntelligenceToolScreen.ORGANIZER -> OrganizerScreen(
              editorState = editorState,
              analysisState = analysisState,
              onUpdateBookMetadata = viewModel::updateBookMetadata,
              onRunOrganizer = viewModel::runManuscriptOrganizer,
              onCancel = viewModel::cancelActiveAnalysis,
              onClearError = viewModel::clearAnalysisError,
              onApplyApprovedFormatting = viewModel::applyApprovedOrganizationFormatting,
              onNavigateBack = { activeTool = null }
            )
          }
        }
      } else {
        Crossfade(targetState = selectedTab, label = "tabTransition") { tab ->
          when (tab) {
            MainTab.HOME -> HomeScreen(
              stats = stats,
              recentManuscripts = recentManuscripts,
              onNewManuscript = { cat ->
                viewModel.createNewManuscript(cat)
                selectedTab = MainTab.EDITOR
              },
              onQuickProofread = { openToolWithActiveOrLatest(IntelligenceToolScreen.PROOFREADING) },
              onOpenAiDetection = { openToolWithActiveOrLatest(IntelligenceToolScreen.AI_DETECTION) },
              onOpenHumanRefinement = { openToolWithActiveOrLatest(IntelligenceToolScreen.HUMAN_REFINEMENT) },
              onOpenOrganizer = { openToolWithActiveOrLatest(IntelligenceToolScreen.ORGANIZER) },
              onOpenLibrary = { selectedTab = MainTab.LIBRARY },
              onImportDocument = { launchImportPicker() },
              onOpenManuscript = { id ->
                viewModel.openManuscript(id)
                selectedTab = MainTab.EDITOR
              }
            )

            MainTab.EDITOR -> EditorScreen(
              editorState = editorState,
              versions = activeVersions,
              fontSizeSp = prefs.editorFontSizeSp,
              onTitleChange = viewModel::updateTitle,
              onAuthorChange = viewModel::updateAuthorName,
              onCategoryChange = viewModel::updateCategory,
              onContentChange = { viewModel.updateContent(it, pushUndo = true) },
              onUndo = viewModel::undo,
              onRedo = viewModel::redo,
              onSaveSnapshot = { label -> viewModel.saveManuscriptWithSnapshot(customLabel = label) },
              onInsertHeading = viewModel::insertHeading,
              onFindReplaceAll = { find, rep -> viewModel.findAndReplaceAll(find, rep) },
              onFontSizeChange = viewModel::setEditorFontSize,
              onRestoreVersion = viewModel::restoreDraftVersion,
              onRestoreOriginal = viewModel::restoreOriginalManuscript,
              onImportDocument = { launchImportPicker() },
              onShareManuscript = viewModel::shareActiveManuscript,
              onSaveExportFile = { fmt -> pendingExportFormat = fmt },
              onNewManuscript = { viewModel.createNewManuscript(ManuscriptCategory.PROSE) },
              onOpenProofreading = { activeTool = IntelligenceToolScreen.PROOFREADING },
              onOpenAiDetection = { activeTool = IntelligenceToolScreen.AI_DETECTION },
              onOpenHumanRefinement = { activeTool = IntelligenceToolScreen.HUMAN_REFINEMENT },
              onOpenOrganizer = { activeTool = IntelligenceToolScreen.ORGANIZER },
              onCopySuccess = viewModel::notifyClipboardCopied
            )

            MainTab.LIBRARY -> LibraryScreen(
              manuscripts = filteredManuscripts,
              folders = folders,
              searchQuery = searchQuery,
              selectedCategory = selectedCatFilter,
              selectedFolderId = selectedFolderFilter,
              sortOption = sortOption,
              onSearchQueryChange = viewModel::setSearchQuery,
              onCategoryFilterChange = viewModel::setCategoryFilter,
              onFolderFilterChange = viewModel::setFolderFilter,
              onSortOptionChange = viewModel::setSortOption,
              onCreateFolder = viewModel::createFolder,
              onDeleteFolder = viewModel::deleteFolder,
              onOpenManuscript = { id ->
                viewModel.openManuscript(id)
                selectedTab = MainTab.EDITOR
              },
              onRenameManuscript = viewModel::renameManuscript,
              onMoveToFolder = viewModel::moveManuscriptToFolder,
              onTogglePin = viewModel::togglePinManuscript,
              onDeleteManuscript = viewModel::deleteManuscript,
              onNewManuscript = {
                viewModel.createNewManuscript(ManuscriptCategory.PROSE)
                selectedTab = MainTab.EDITOR
              }
            )

            MainTab.HISTORY -> HistoryScreen(
              analysisHistory = analysisHistory,
              recentVersions = recentVersions,
              manuscripts = allManuscripts,
              onReopenReport = { entry ->
                val type = viewModel.reopenHistoryReport(entry)
                activeTool = when (type) {
                  AnalysisType.PROOFREADING -> IntelligenceToolScreen.PROOFREADING
                  AnalysisType.AI_DETECTION -> IntelligenceToolScreen.AI_DETECTION
                  AnalysisType.HUMAN_REFINEMENT -> IntelligenceToolScreen.HUMAN_REFINEMENT
                  AnalysisType.MANUSCRIPT_ORGANIZER -> IntelligenceToolScreen.ORGANIZER
                }
              },
              onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
              onOpenManuscript = { id ->
                viewModel.openManuscript(id)
                selectedTab = MainTab.EDITOR
              }
            )

            MainTab.SETTINGS -> SettingsScreen(
              preferences = prefs,
              serverHealth = serverHealth,
              isCheckingServer = isCheckingServer,
              onThemeModeChange = viewModel::setThemeMode,
              onFontSizeChange = viewModel::setEditorFontSize,
              onDefaultProofreadingModeChange = viewModel::setDefaultProofreadingMode,
              onAutoSaveChange = viewModel::setAutoSaveEnabled,
              onPoetryProtectionChange = viewModel::setPoetryProtectionStrict,
              onUseCacheChange = viewModel::setUseCacheForUnchangedText,
              onCustomProxyUrlSave = viewModel::setCustomProxyUrl,
              onTestServerConnection = viewModel::testAiServerConnection,
              onExportBackup = { backupLauncher.launch("sobdosuddhi_backup.json") },
              onClearHistoryCache = viewModel::clearAllHistory
            )
          }
        }
      }
    }
  }
}
