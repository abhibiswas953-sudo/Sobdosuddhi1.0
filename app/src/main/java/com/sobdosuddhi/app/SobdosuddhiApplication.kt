package com.sobdosuddhi.app

import android.app.Application
import com.sobdosuddhi.app.data.ai.GeminiApiClient
import com.sobdosuddhi.app.data.ai.SobdosuddhiAiEngine
import com.sobdosuddhi.app.data.local.SobdosuddhiDatabase
import com.sobdosuddhi.app.data.local.UserPreferencesRepository
import com.sobdosuddhi.app.data.network.NetworkConnectivityMonitor
import com.sobdosuddhi.app.domain.DocumentImportExportManager

class SobdosuddhiApplication : Application() {
  lateinit var database: SobdosuddhiDatabase
    private set
  lateinit var preferencesRepository: UserPreferencesRepository
    private set
  lateinit var connectivityMonitor: NetworkConnectivityMonitor
    private set
  lateinit var geminiClient: GeminiApiClient
    private set
  lateinit var aiEngine: SobdosuddhiAiEngine
    private set
  lateinit var documentManager: DocumentImportExportManager
    private set

  override fun onCreate() {
    super.onCreate()
    database = SobdosuddhiDatabase.getInstance(this)
    preferencesRepository = UserPreferencesRepository(this)
    connectivityMonitor = NetworkConnectivityMonitor(this)
    geminiClient = GeminiApiClient()
    aiEngine = SobdosuddhiAiEngine(geminiClient, database.analysisHistoryDao())
    documentManager = DocumentImportExportManager(this, geminiClient)
  }

  /**
   * Allows automated tests to substitute a local loopback server client without touching production config.
   */
  internal fun overrideAiClientForTesting(testClient: GeminiApiClient) {
    geminiClient = testClient
    aiEngine = SobdosuddhiAiEngine(testClient, database.analysisHistoryDao())
    documentManager = DocumentImportExportManager(this, testClient)
  }
}
