# Sobdosuddhi 1.0 (শব্দশুদ্ধি ১.০) — Native Android Architecture & Build Guide

## 1. Application Identity
- **Application Name**: শব্দশুদ্ধি ১.০ (Sobdosuddhi 1.0)
- **Application ID**: `com.sobdosuddhi.app`
- **Version**: `1.0.0` (`versionCode = 1`)
- **Platform**: Native Android (Kotlin + Jetpack Compose + Material Design 3)
- **Minimum SDK**: Android 7.0 (API 24) · **Target SDK**: API 36
- **UI Language**: Bengali (`bn`) with native Bengali Unicode & HarfBuzz conjunct rendering

## 2. Core Native Modules
1. **Dashboard & 5-Tab Bottom Navigation (`MainActivity.kt`, `HomeScreen.kt`)**:
   - হোম · নতুন লেখা · আমার পান্ডুলিপি · ইতিহাস · সেটিংস
   - Splash screen, custom adaptive vector logo, edge-to-edge status/navigation bar theming, Light/Dark/System Material 3 themes, and native Android back navigation.
2. **Advanced Bengali Manuscript Editor & Version Control (`EditorScreen.kt`, `SobdosuddhiDatabase.kt`)**:
   - Room private local database (`sobdosuddhi_private.db`) storing manuscripts, folders, full draft version snapshots (`draft_versions`), and SHA-256 cached analysis history (`analysis_history`).
   - Preserves `originalContent` separately so the original manuscript is never overwritten without explicit user approval.
   - Undo/Redo stack, auto-save drafts, Find & Replace, live Bengali word/character/paragraph counters, Chapter Navigation, Font Size adjustment, and 2-version visual inline diff + one-tap restore.
3. **Ultra-Fast Bengali Proofreading Engine (`SobdosuddhiAiEngine.kt`, `BengaliLocalAnalyzer.kt`, `ManuscriptChunker.kt`)**:
   - Local zero-latency Bengali pre-checker (spelling, যুক্তাক্ষর, কার ও ফলা, ASCII `.` vs বাংলা `।`, spacing).
   - Stanza-safe (poetry) & paragraph-safe (prose) smart chunking with bounded parallel Gemini execution (`Semaphore(3)`), real measurable chunk/step progress, partial result streaming, and SHA-256 content-hash caching.
   - Three modes: দ্রুত পরীক্ষা (`gemini-3.1-flash-lite`), মানসম্মত পরীক্ষা (`gemini-4-hydrogen`, `LOW` thinking), গভীর সম্পাদনা পর্যালোচনা (`gemini-4-hydrogen`, `MEDIUM` thinking).
   - Strict Poetry Protection and Prose Protection rules.
4. **Probabilistic AI Writing Detection (`AiDetectionScreen.kt`)**:
   - Evaluates 10 Bengali linguistic dimensions with Fast/Detailed mode.
   - Clearly separates Detection Indicator, Model Confidence, and Verified Authorship, and includes a mandatory uncertainty warning in every report.
5. **Human Writing Refinement (`RefinementScreen.kt`)**:
   - 5 Bengali writing modes + Poetry-safe suggestion mode + word-level Before/After diff + Accept All / Accept Selected / Reject / Restore Original.
6. **Complete Manuscript Organizer (`OrganizerScreen.kt`)**:
   - Structure report (chapters, paragraphs, words, characters), Table of Contents, duplicate paragraph & repeated sentence detection, formatting consistency, character/terminology continuity, and poetry/book front & back matter (উৎসর্গ, ভূমিকা, শেষাংশ).
7. **Native Document Import, Export & Sharing (`DocumentImportExportManager.kt`)**:
   - Native SAF import for DOCX (OpenXML XML parser), PDF (Android `PdfRenderer` + Gemini Bengali OCR when required), TXT, and legacy DOC fallback.
   - Native export & `FileProvider` sharing for DOCX, PDF (`PdfDocument` + `StaticLayout` with Bengali conjunct shaping & page numbers), and TXT, plus full private JSON backup.

## 3. API Key & Backend Security
- The app uses Gradle Secrets Plugin (`secrets-gradle-plugin`) reading from `.env` (injected by AI Studio Secrets at build time).
- No API key is hardcoded in source code and no API key management UI is exposed.
- For production Play Store distribution at scale, route `GeminiApiClient` requests through a private HTTPS backend proxy (e.g., Cloud Run / Express / Cloud Functions) holding `GEMINI_API_KEY` server-side with per-user authentication and rate limiting.

## 4. Building Debug & Signed Release APKs
```bash
# Run unit & Robolectric tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build Signed Release APK (set KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD env vars)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```
