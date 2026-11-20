package com.sobdosuddhi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.RefinementMode
import com.sobdosuddhi.app.ui.components.BengaliConfirmDialog
import com.sobdosuddhi.app.ui.components.ErrorBannerCard
import com.sobdosuddhi.app.ui.components.InlineDiffTextView
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.components.ProcessingProgressCard
import com.sobdosuddhi.app.ui.components.SectionHeader
import com.sobdosuddhi.app.ui.viewmodel.AnalysisUiState
import com.sobdosuddhi.app.ui.viewmodel.EditorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefinementScreen(
  editorState: EditorUiState,
  analysisState: AnalysisUiState,
  isOnline: Boolean = true,
  onRunRefinement: (RefinementMode) -> Unit,
  onCancel: () -> Unit,
  onClearError: () -> Unit,
  onToggleChange: (String) -> Unit,
  onApplySelected: () -> Unit,
  onApplyAll: () -> Unit,
  onReject: () -> Unit,
  onRestoreOriginal: () -> Unit,
  onNavigateBack: () -> Unit
) {
  var selectedMode by remember { mutableStateOf(RefinementMode.NATURAL) }
  var showFullDiff by remember { mutableStateOf(true) }
  var confirmRestoreOriginal by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("মানবিক ভাষায় রূপান্তর", style = MaterialTheme.typography.titleLarge) },
      navigationIcon = {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ফিরুন")
        }
      }
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 5 Writing Modes Selector
      item {
        LiteraryCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ভাষা ও সম্পাদনার ধরন নির্বাচন করুন", style = MaterialTheme.typography.titleMedium)
            if (editorState.category == ManuscriptCategory.POETRY) {
              Text(
                text = "কবিতা সুরক্ষা সক্রিয়: কবিতার চিত্রকল্প, রূপক, আবেগ, পঙ্ক্তির বিন্যাস ও ছন্দ অক্ষুণ্ণ রেখে কেবল বিকল্প শৈল্পিক পরামর্শ দেওয়া হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
              )
            }
            RefinementMode.entries.forEach { mode ->
              val selected = selectedMode == mode
              LiteraryCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { selectedMode = mode },
                containerColor = if (selected) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                  MaterialTheme.colorScheme.surface
                }
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text(
                    text = mode.bengaliLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (!isOnline) {
              Text(
                text = "অফলাইন মোড: মানবিক ভাষায় রূপান্তরের জন্য ইন্টারনেট সংযোগ প্রয়োজন। ইন্টারনেট ফিরে এলে অনলাইন মোড স্বয়ংক্রিয়ভাবে সক্রিয় হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
              )
              Spacer(modifier = Modifier.height(4.dp))
            }
            Button(
              onClick = { onRunRefinement(selectedMode) },
              enabled = !analysisState.isRunning && editorState.content.isNotBlank(),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (analysisState.isRunning) "রূপান্তর চলছে…" else "মানবিক ভাষায় রূপান্তর করুন")
            }
          }
        }
      }

      if (analysisState.isRunning && analysisState.progress != null) {
        item {
          ProcessingProgressCard(progress = analysisState.progress, onCancel = onCancel)
        }
      }

      if (!analysisState.errorMessage.isNullOrBlank()) {
        item {
          ErrorBannerCard(
            message = analysisState.errorMessage,
            onRetry = { onRunRefinement(selectedMode) },
            onDismiss = onClearError
          )
        }
      }

      val report = analysisState.refinementReport
      if (report != null) {
        // Summary & Actions (Accept All / Accept Selected / Reject / Restore Original)
        item {
          LiteraryCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text("পরিমার্জন সারসংক্ষেপ", style = MaterialTheme.typography.titleLarge)
              Spacer(modifier = Modifier.height(6.dp))
              Text(report.summary, style = MaterialTheme.typography.bodyMedium)
              if (report.poetryPreservationNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = report.poetryPreservationNote,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = report.editorialDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(12.dp))

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onApplyAll, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("সব গ্রহণ করুন", maxLines = 1)
                }
                OutlinedButton(
                  onClick = onApplySelected,
                  enabled = analysisState.selectedRefinementChangeIds.isNotEmpty(),
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("নির্বাচিত গ্রহণ", maxLines = 1)
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("পরিবর্তন বাতিল")
                }
                TextButton(onClick = { confirmRestoreOriginal = true }, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("মূল লেখা ফিরিয়ে আনুন")
                }
              }
            }
          }
        }

        // Before & After Comparison View
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            SectionHeader(title = "পূর্ববর্তী বনাম পরিমার্জিত তুলনা", modifier = Modifier.weight(1f))
            TextButton(onClick = { showFullDiff = !showFullDiff }) {
              Text(if (showFullDiff) "পৃথকভাবে দেখুন" else "রঙিন তুলনা দেখুন")
            }
          }
        }

        item {
          if (showFullDiff) {
            val diff = remember(editorState.content, report.refinedFullText) {
              BengaliUtils.computeDiff(editorState.content, report.refinedFullText)
            }
            LiteraryCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = "লাল কাটা অংশ মূল লেখা এবং সবুজ অংশ পরিমার্জিত রূপ:",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                InlineDiffTextView(segments = diff)
              }
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              LiteraryCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Text("মূল পাঠ্য", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(editorState.content, style = MaterialTheme.typography.bodyLarge)
                }
              }
              LiteraryCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Text("পরিমার্জিত পাঠ্য", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(report.refinedFullText, style = MaterialTheme.typography.bodyLarge)
                }
              }
            }
          }
        }

        // Individual Important Changes with Explanations & Checkboxes
        if (report.changes.isNotEmpty()) {
          item {
            SectionHeader(
              title = "গুরুত্বপূর্ণ পরিবর্তনের ব্যাখ্যা (${BengaliUtils.toBengaliDigits(report.changes.size)})",
              subtitle = "নির্দিষ্ট পরিবর্তন বাছাই করে প্রয়োগ করতে চেকবক্স ব্যবহার করুন"
            )
          }
          items(report.changes, key = { it.id }) { change ->
            val checked = change.id in analysisState.selectedRefinementChangeIds
            LiteraryCard(modifier = Modifier.fillMaxWidth(), onClick = { onToggleChange(change.id) }) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
              ) {
                Checkbox(checked = checked, onCheckedChange = { onToggleChange(change.id) })
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = change.changeType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = change.originalSegment,
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                    color = MaterialTheme.colorScheme.error
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "→ ${change.refinedSegment}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = change.changeReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }

  if (confirmRestoreOriginal) {
    BengaliConfirmDialog(
      title = "মূল পান্ডুলিপি পুনরুদ্ধার",
      message = "আপনি কি মূল পান্ডুলিপিতে ফিরে যেতে চান? বর্তমান সংস্করণ ইতিহাসে সুরক্ষিত থাকবে।",
      confirmLabel = "ফিরিয়ে আনুন",
      onConfirm = {
        onRestoreOriginal()
        confirmRestoreOriginal = false
      },
      onDismiss = { confirmRestoreOriginal = false }
    )
  }
}
