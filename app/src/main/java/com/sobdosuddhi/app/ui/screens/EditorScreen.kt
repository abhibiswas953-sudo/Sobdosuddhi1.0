package com.sobdosuddhi.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sobdosuddhi.app.data.local.DraftVersionEntity
import com.sobdosuddhi.app.domain.BengaliLocalAnalyzer
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.domain.ExportFormat
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.VersionSourceType
import com.sobdosuddhi.app.ui.components.BengaliConfirmDialog
import com.sobdosuddhi.app.ui.components.InlineDiffTextView
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.viewmodel.EditorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
  editorState: EditorUiState,
  versions: List<DraftVersionEntity>,
  fontSizeSp: Float,
  onTitleChange: (String) -> Unit,
  onAuthorChange: (String) -> Unit,
  onCategoryChange: (ManuscriptCategory) -> Unit,
  onContentChange: (String) -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onSaveSnapshot: (String) -> Unit,
  onInsertHeading: (String) -> Unit,
  onFindReplaceAll: (String, String) -> Unit,
  onFontSizeChange: (Float) -> Unit,
  onRestoreVersion: (DraftVersionEntity) -> Unit,
  onRestoreOriginal: () -> Unit,
  onImportDocument: () -> Unit,
  onShareManuscript: (ExportFormat) -> Unit,
  onSaveExportFile: (ExportFormat) -> Unit,
  onNewManuscript: () -> Unit,
  onOpenProofreading: () -> Unit,
  onOpenAiDetection: () -> Unit,
  onOpenHumanRefinement: () -> Unit,
  onOpenOrganizer: () -> Unit,
  onCopySuccess: () -> Unit = {}
) {
  val clipboard = LocalClipboardManager.current
  val metrics = remember(editorState.content) { BengaliUtils.calculateMetrics(editorState.content) }

  var showFindReplace by remember { mutableStateOf(false) }
  var findQuery by remember { mutableStateOf("") }
  var replaceQuery by remember { mutableStateOf("") }

  var showFontSheet by remember { mutableStateOf(false) }
  var showHeadingDialog by remember { mutableStateOf(false) }
  var headingInput by remember { mutableStateOf("") }

  var showSaveSnapshotDialog by remember { mutableStateOf(false) }
  var snapshotLabelInput by remember { mutableStateOf("") }

  var showVersionsSheet by remember { mutableStateOf(false) }
  var compareVersionA by remember { mutableStateOf<DraftVersionEntity?>(null) }
  var compareVersionB by remember { mutableStateOf<DraftVersionEntity?>(null) }
  var versionToRestore by remember { mutableStateOf<DraftVersionEntity?>(null) }

  var showOriginalCompareSheet by remember { mutableStateOf(false) }
  var showRestoreOriginalConfirm by remember { mutableStateOf(false) }
  var showChapterNavSheet by remember { mutableStateOf(false) }
  var showExportSheet by remember { mutableStateOf(false) }

  val tocEntries = remember(showChapterNavSheet, editorState.content) {
    if (showChapterNavSheet) BengaliLocalAnalyzer.extractTableOfContents(editorState.content) else emptyList()
  }

  Column(modifier = Modifier.fillMaxSize().imePadding()) {
    // Top Editor Action Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 1.dp
    ) {
      Column {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUndo, enabled = editorState.canUndo) {
              Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "পূর্বাবস্থায় ফিরুন")
            }
            IconButton(onClick = onRedo, enabled = editorState.canRedo) {
              Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "পুনরায় করুন")
            }
            IconButton(onClick = { showFindReplace = !showFindReplace }) {
              Icon(Icons.Outlined.FindReplace, contentDescription = "খুঁজুন ও বদলান")
            }
            IconButton(onClick = { showVersionsSheet = true }) {
              Icon(Icons.Outlined.History, contentDescription = "সংস্করণ ইতিহাস")
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onImportDocument) {
              Icon(Icons.Outlined.UploadFile, contentDescription = "আমদানি")
            }
            IconButton(onClick = { showExportSheet = true }) {
              Icon(Icons.Outlined.Share, contentDescription = "এক্সপোর্ট ও শেয়ার")
            }
            Button(
              onClick = { showSaveSnapshotDialog = true },
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("সংরক্ষণ", maxLines = 1)
            }
          }
        }

        // Import/OCR progress bar if active
        if (!editorState.importStatusMessage.isNullOrBlank()) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          Text(
            text = editorState.importStatusMessage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
          )
        }

        // Secondary formatting & tool strip
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          ManuscriptCategory.entries.forEach { cat ->
            FilterChip(
              selected = editorState.category == cat,
              onClick = { onCategoryChange(cat) },
              label = { Text(cat.shortLabel, maxLines = 1) }
            )
          }
          TextButton(onClick = { showHeadingDialog = true }) {
            Icon(Icons.Outlined.Title, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("শিরোনাম")
          }
          TextButton(onClick = { showChapterNavSheet = true }) {
            Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("অধ্যায় (${BengaliUtils.toBengaliDigits(metrics.chapters)})")
          }
          TextButton(onClick = { showFontSheet = true }) {
            Icon(Icons.Outlined.FormatSize, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("ফন্ট ${BengaliUtils.toBengaliDigits(fontSizeSp.toInt())}")
          }
          TextButton(onClick = { showOriginalCompareSheet = true }) {
            Icon(Icons.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("মূল তুলনা")
          }
          TextButton(onClick = {
            if (editorState.content.isNotBlank()) {
              clipboard.setText(AnnotatedString(editorState.content))
              onCopySuccess()
            }
          }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("কপি")
          }
          TextButton(onClick = onNewManuscript) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("নতুন")
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }
    }

    // Collapsible Find & Replace Bar
    AnimatedVisibility(visible = showFindReplace) {
      Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = findQuery,
              onValueChange = { findQuery = it },
              label = { Text("খুঁজুন") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = replaceQuery,
              onValueChange = { replaceQuery = it },
              label = { Text("প্রতিস্থাপন করুন") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }
          Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { showFindReplace = false }) { Text("বন্ধ") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onFindReplaceAll(findQuery, replaceQuery) }, enabled = findQuery.isNotEmpty()) {
              Text("সব প্রতিস্থাপন করুন")
            }
          }
        }
      }
    }

    // Main Scrollable Manuscript Writing Canvas
    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
      // Manuscript Title
      BasicTextField(
        value = editorState.title,
        onValueChange = onTitleChange,
        textStyle = MaterialTheme.typography.headlineMedium.copy(
          color = MaterialTheme.colorScheme.onBackground
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
          if (editorState.title.isEmpty()) {
            Text(
              text = "পান্ডুলিপির শিরোনাম লিখুন…",
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
          }
          inner()
        }
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Author Name
      BasicTextField(
        value = editorState.authorName,
        onValueChange = onAuthorChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
          if (editorState.authorName.isEmpty()) {
            Text(
              text = "লেখকের নাম (ঐচ্ছিক)",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
          }
          inner()
        }
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Live quiet metadata strip (word, char, paragraph count + save status)
      Text(
        text = "${BengaliUtils.toBengaliDigits(metrics.words)} শব্দ  ·  ${BengaliUtils.toBengaliDigits(metrics.characters)} অক্ষর  ·  ${BengaliUtils.toBengaliDigits(metrics.paragraphs)} অনুচ্ছেদ  ·  ${if (editorState.isDirty) "অসংরক্ষিত পরিবর্তন" else editorState.lastSavedLabel}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(modifier = Modifier.height(14.dp))

      // Full Bengali Unicode Editor Canvas
      BasicTextField(
        value = editorState.content,
        onValueChange = onContentChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
          color = MaterialTheme.colorScheme.onBackground,
          fontSize = fontSizeSp.sp,
          lineHeight = (fontSizeSp * 1.65f).sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 380.dp),
        decorationBox = { inner ->
          if (editorState.content.isEmpty()) {
            Text(
              text = if (editorState.category == ManuscriptCategory.POETRY) {
                "এখানে আপনার বাংলা কবিতা লিখুন বা পেস্ট করুন…\n\nপ্রতিটি স্তবকের মাঝে একটি ফাঁকা লাইন রাখলে ছন্দ ও স্তবক বিশ্লেষণ আরও নিখুঁত হবে।"
              } else {
                "এখানে আপনার বাংলা গল্প, উপন্যাস বা প্রবন্ধ লিখুন অথবা ওপরের আমদানি বোতাম থেকে DOCX, PDF, TXT ফাইল যুক্ত করুন…"
              },
              style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.65f).sp
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
          }
          inner()
        }
      )
    }

    // Bottom Intelligence Tool Strip (1-tap launch to each independent engine)
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp
    ) {
      Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(onClick = onOpenProofreading) {
            Icon(Icons.Outlined.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("প্রুফরিডিং", maxLines = 1)
          }
          OutlinedButton(onClick = onOpenAiDetection) {
            Icon(Icons.Outlined.FindInPage, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("AI শনাক্তকরণ", maxLines = 1)
          }
          OutlinedButton(onClick = onOpenHumanRefinement) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("মানবিক রূপান্তর", maxLines = 1)
          }
          OutlinedButton(onClick = onOpenOrganizer) {
            Icon(Icons.Outlined.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("পান্ডুলিপি সাজান", maxLines = 1)
          }
        }
      }
    }
  }

  // --- Dialogs & Bottom Sheets ---

  if (showHeadingDialog) {
    AlertDialog(
      onDismissRequest = { showHeadingDialog = false },
      title = { Text("নতুন অধ্যায় বা শিরোনাম যুক্ত করুন") },
      text = {
        OutlinedTextField(
          value = headingInput,
          onValueChange = { headingInput = it },
          label = { Text("যেমন: অধ্যায় ৩: শেষ বিকেল") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(onClick = {
          onInsertHeading(headingInput)
          headingInput = ""
          showHeadingDialog = false
        }) { Text("যুক্ত করুন") }
      },
      dismissButton = {
        TextButton(onClick = { showHeadingDialog = false }) { Text("বাতিল") }
      }
    )
  }

  if (showSaveSnapshotDialog) {
    AlertDialog(
      onDismissRequest = { showSaveSnapshotDialog = false },
      title = { Text("নতুন খসড়া সংস্করণ সংরক্ষণ") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "সংস্করণ নিয়ন্ত্রণ ব্যবস্থায় আপনার বর্তমান পান্ডুলিপির একটি স্থায়ী স্ন্যাপশট সংরক্ষিত হবে। মূল পান্ডুলিপি সর্বদা সুরক্ষিত থাকবে।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = snapshotLabelInput,
            onValueChange = { snapshotLabelInput = it },
            label = { Text("সংস্করণের নাম (ঐচ্ছিক, যেমন: ২য় খসড়া)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          onSaveSnapshot(snapshotLabelInput)
          snapshotLabelInput = ""
          showSaveSnapshotDialog = false
        }) { Text("সংরক্ষণ করুন") }
      },
      dismissButton = {
        TextButton(onClick = { showSaveSnapshotDialog = false }) { Text("বাতিল") }
      }
    )
  }

  if (showFontSheet) {
    ModalBottomSheet(onDismissRequest = { showFontSheet = false }) {
      Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("সম্পাদকের ফন্টের আকার", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "বর্তমান আকার: ${BengaliUtils.toBengaliDigits(fontSizeSp.toInt())} পয়েন্ট",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary
        )
        Slider(
          value = fontSizeSp,
          onValueChange = onFontSizeChange,
          valueRange = 13f..26f,
          steps = 12
        )
        Text(
          text = "আমার সোনার বাংলা, আমি তোমায় ভালোবাসি।",
          style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp),
          modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (showChapterNavSheet) {
    ModalBottomSheet(onDismissRequest = { showChapterNavSheet = false }) {
      Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("অধ্যায় ও শিরোনাম তালিকা", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(10.dp))
        if (tocEntries.isEmpty()) {
          Text(
            text = "এখনও কোনো অধ্যায় বা শিরোনাম শনাক্ত হয়নি। ওপরের ‘শিরোনাম’ বোতাম ব্যবহার করে অধ্যায় যুক্ত করুন।",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          LazyColumn(
            modifier = Modifier.height(260.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(tocEntries) { entry ->
              LiteraryCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = (if (entry.level > 1) "   ↳ " else "") + entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                  )
                  Text(
                    text = "অনুচ্ছেদ ${BengaliUtils.toBengaliDigits(entry.paragraphIndex + 1)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (showExportSheet) {
    ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
      Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text("পান্ডুলিপি এক্সপোর্ট ও শেয়ার করুন", style = MaterialTheme.typography.titleLarge)
        Text(
          text = "বাংলা ইউনিকোড, অধ্যায়ের শিরোনাম, অনুচ্ছেদের ফাঁকা জায়গা এবং কবিতার পঙ্ক্তির বিন্যাস অক্ষুণ্ণ রেখে ফাইল তৈরি হবে।",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExportFormat.entries.forEach { format ->
          LiteraryCard(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(format.bengaliLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                  showExportSheet = false
                  onSaveExportFile(format)
                }) {
                  Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("সংরক্ষণ")
                }
                Button(onClick = {
                  showExportSheet = false
                  onShareManuscript(format)
                }) {
                  Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("শেয়ার")
                }
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (showOriginalCompareSheet) {
    val diffSegments = remember(editorState.originalContent, editorState.content) {
      BengaliUtils.computeDiff(editorState.originalContent, editorState.content)
    }
    ModalBottomSheet(onDismissRequest = { showOriginalCompareSheet = false }) {
      Column(
        modifier = Modifier
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Text("মূল পান্ডুলিপি বনাম বর্তমান সংস্করণ", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "লাল কাটা অংশ মূল লেখা থেকে বাদ পড়েছে এবং সবুজ অংশ নতুন যুক্ত হয়েছে।",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        LiteraryCard(modifier = Modifier.fillMaxWidth()) {
          InlineDiffTextView(
            segments = diffSegments,
            fontSizeSp = fontSizeSp - 1f,
            modifier = Modifier.padding(14.dp)
          )
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (editorState.originalContent != editorState.content && editorState.originalContent.isNotBlank()) {
          OutlinedButton(
            onClick = {
              showOriginalCompareSheet = false
              showRestoreOriginalConfirm = true
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("মূল পান্ডুলিপি ফিরিয়ে আনুন")
          }
        }
        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }

  // Full Draft Version Control Bottom Sheet (সংস্করণ নিয়ন্ত্রণ)
  if (showVersionsSheet) {
    ModalBottomSheet(onDismissRequest = {
      showVersionsSheet = false
      compareVersionA = null
      compareVersionB = null
    }) {
      Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("খসড়া সংস্করণ নিয়ন্ত্রণ (Version Control)", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "প্রতিটি সংরক্ষিত সংস্করণের পূর্ণ ইতিহাস। যেকোনো দুটি সংস্করণ তুলনা করুন অথবা পূর্ববর্তী খসড়া নিরাপদে পুনরুদ্ধার করুন।",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (compareVersionA != null && compareVersionB != null) {
          val verDiff = remember(compareVersionA, compareVersionB) {
            BengaliUtils.computeDiff(compareVersionA!!.content, compareVersionB!!.content)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "তুলনা: সংস্করণ ${BengaliUtils.toBengaliDigits(compareVersionA!!.versionNumber)} → সংস্করণ ${BengaliUtils.toBengaliDigits(compareVersionB!!.versionNumber)}",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { compareVersionA = null; compareVersionB = null }) {
              Text("তালিকায় ফিরুন")
            }
          }
          LiteraryCard(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
              InlineDiffTextView(segments = verDiff, fontSizeSp = 14.5f)
            }
          }
        } else if (versions.isEmpty()) {
          Text(
            text = "এখনও কোনো সংরক্ষিত সংস্করণ নেই। ওপরের ‘সংরক্ষণ’ বোতামে চাপ দিয়ে প্রথম সংস্করণ তৈরি করুন।",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          if (compareVersionA != null) {
            Text(
              text = "তুলনার জন্য সংস্করণ ${BengaliUtils.toBengaliDigits(compareVersionA!!.versionNumber)} নির্বাচিত হয়েছে—এখন দ্বিতীয় সংস্করণে ‘তুলনা’ চাপুন।",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(bottom = 8.dp)
            )
          }
          LazyColumn(
            modifier = Modifier.height(320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(versions, key = { it.id }) { ver ->
              LiteraryCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "সংস্করণ ${BengaliUtils.toBengaliDigits(ver.versionNumber)} • ${ver.label}",
                      style = MaterialTheme.typography.titleMedium,
                      modifier = Modifier.weight(1f),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "${BengaliUtils.toBengaliDigits(ver.wordCount)} শব্দ",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "${VersionSourceType.fromName(ver.sourceType).bengaliLabel}  ·  ${BengaliUtils.formatBengaliDate(ver.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  if (ver.changeSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = ver.changeSummary,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                      if (compareVersionA == null) {
                        compareVersionA = ver
                      } else if (compareVersionA!!.id != ver.id) {
                        compareVersionB = ver
                      }
                    }) {
                      Text(if (compareVersionA?.id == ver.id) "নির্বাচিত" else "তুলনা করুন")
                    }
                    TextButton(onClick = { versionToRestore = ver }) {
                      Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("পুনরুদ্ধার")
                    }
                  }
                }
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }

  if (versionToRestore != null) {
    val target = versionToRestore!!
    BengaliConfirmDialog(
      title = "পূর্ববর্তী সংস্করণ পুনরুদ্ধার",
      message = "সংস্করণ ${BengaliUtils.toBengaliDigits(target.versionNumber)} (${target.label}) ফিরিয়ে আনলে বর্তমান লেখার ওপর নতুন একটি পুনরুদ্ধার সংস্করণ তৈরি হবে। কোনো পূর্ববর্তী সংস্করণ মুছে যাবে না।",
      confirmLabel = "পুনরুদ্ধার করুন",
      onConfirm = {
        onRestoreVersion(target)
        versionToRestore = null
        showVersionsSheet = false
      },
      onDismiss = { versionToRestore = null }
    )
  }

  if (showRestoreOriginalConfirm) {
    BengaliConfirmDialog(
      title = "মূল পান্ডুলিপি ফিরিয়ে আনুন",
      message = "আপনি কি নিশ্চিত যে মূল সংরক্ষিত পান্ডুলিপিতে ফিরে যেতে চান? বর্তমান খসড়া সংস্করণ ইতিহাসে সুরক্ষিত থাকবে।",
      confirmLabel = "ফিরিয়ে আনুন",
      onConfirm = {
        onRestoreOriginal()
        showRestoreOriginalConfirm = false
      },
      onDismiss = { showRestoreOriginalConfirm = false }
    )
  }
}
