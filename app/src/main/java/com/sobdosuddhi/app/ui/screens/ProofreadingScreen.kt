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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.sobdosuddhi.app.model.ProofreadingIssue
import com.sobdosuddhi.app.model.ProofreadingMode
import com.sobdosuddhi.app.ui.components.EmptyStateView
import com.sobdosuddhi.app.ui.components.ErrorBannerCard
import com.sobdosuddhi.app.ui.components.InlineDiffTextView
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.components.ProcessingProgressCard
import com.sobdosuddhi.app.ui.viewmodel.AnalysisUiState
import com.sobdosuddhi.app.ui.viewmodel.EditorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofreadingScreen(
  editorState: EditorUiState,
  analysisState: AnalysisUiState,
  defaultMode: ProofreadingMode,
  isOnline: Boolean = true,
  onCategoryChange: (ManuscriptCategory) -> Unit,
  onRunProofreading: (ProofreadingMode) -> Unit,
  onRunOfflineProofreading: () -> Unit = {},
  onCancel: () -> Unit,
  onClearError: () -> Unit,
  onAcceptIssue: (ProofreadingIssue) -> Unit,
  onRejectIssue: (ProofreadingIssue) -> Unit,
  onAcceptAll: () -> Unit,
  onOpenSettings: () -> Unit = {},
  onNavigateBack: () -> Unit
) {
  var selectedMode by remember(defaultMode) { mutableStateOf(defaultMode) }
  var showFullDiff by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("উন্নত বাংলা প্রুফরিডিং ইঞ্জিন", style = MaterialTheme.typography.titleLarge) },
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
      // Category & Protection Mode Notice
      item {
        LiteraryCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text("পান্ডুলিপির ধরন", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ManuscriptCategory.entries.forEach { cat ->
                FilterChip(
                  selected = editorState.category == cat,
                  onClick = { onCategoryChange(cat) },
                  label = { Text(cat.bengaliLabel) }
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
              Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (editorState.category == ManuscriptCategory.POETRY) {
                  "কবিতা সুরক্ষা সক্রিয়: কবিতার ছন্দ, অন্ত্যমিল, চিত্রকল্প, উপমা, রূপক, পঙ্ক্তির বিন্যাস ও কবির নিজস্ব শব্দচয়ন কখনোই স্বয়ংক্রিয়ভাবে নষ্ট করা হবে না।"
                } else {
                  "গদ্য সুরক্ষা সক্রিয়: লেখকের নিজস্ব কণ্ঠস্বর, বর্ণনাভঙ্গি, সংলাপ, চরিত্রের নাম ও সাহিত্যিক স্টাইল অক্ষুণ্ণ রেখে সংশোধনীর পরামর্শ দেওয়া হবে।"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // 3 Processing Modes (Quick / Standard / Deep)
      item {
        LiteraryCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("প্রুফরিডিং মোড নির্বাচন করুন", style = MaterialTheme.typography.titleMedium)
            ProofreadingMode.entries.forEach { mode ->
              val isSelected = selectedMode == mode
              LiteraryCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { selectedMode = mode },
                containerColor = if (isSelected) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                  MaterialTheme.colorScheme.surface
                }
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = mode.bengaliLabel,
                      style = MaterialTheme.typography.titleMedium,
                      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = mode.targetTimeLabel,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
            if (!isOnline) {
              Text(
                text = "অফলাইন মোড সক্রিয়: ইন্টারনেট ছাড়াই স্থানীয় বাংলা বানান, যুক্তাক্ষর, কার-ফলা ও বিরামচিহ্ন পরীক্ষা চলবে। ইন্টারনেট ফিরে এলে অনলাইন AI প্রুফরিডিং স্বয়ংক্রিয়ভাবে চালু হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
              )
              Spacer(modifier = Modifier.height(4.dp))
            }
            Button(
              onClick = { onRunProofreading(selectedMode) },
              enabled = !analysisState.isRunning && editorState.content.isNotBlank(),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Outlined.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = when {
                  analysisState.isRunning -> "প্রুফরিডিং চলছে…"
                  !isOnline -> "অফলাইন বানান পরীক্ষা শুরু করুন"
                  else -> "প্রুফরিডিং শুরু করুন (${selectedMode.bengaliLabel})"
                }
              )
            }
            OutlinedButton(
              onClick = onRunOfflineProofreading,
              enabled = !analysisState.isRunning && editorState.content.isNotBlank(),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("অফলাইন বানান পরীক্ষা (তাৎক্ষণিক)")
            }
          }
        }
      }

      // Real Measurable Progress Card
      if (analysisState.isRunning && analysisState.progress != null) {
        item {
          ProcessingProgressCard(
            progress = analysisState.progress,
            onCancel = onCancel
          )
        }
      }

      // Error State
      if (!analysisState.errorMessage.isNullOrBlank()) {
        item {
          ErrorBannerCard(
            message = analysisState.errorMessage,
            onRetry = { onRunProofreading(selectedMode) },
            onDismiss = onClearError
          )
        }
      }

      // Proofreading Results (supports partial streaming & final)
      val report = analysisState.proofreadingReport
      if (report != null) {
        item {
          LiteraryCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "প্রুফরিডিং সারসংক্ষেপ",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = report.overallSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
              if (report.poetryProtectionSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = report.poetryProtectionSummary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "মোট পরামর্শ: ${BengaliUtils.toBengaliDigits(report.issues.size)}টি  ·  সময়: ${BengaliUtils.formatDuration(report.durationMs)}" +
                  (if (report.isOfflineMode) "  ·  স্থানীয় বাংলা পরীক্ষা" else "") +
                  (if (report.fromCache) "  ·  ক্যাশ থেকে তাৎক্ষণিক লোড" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              if (isOnline && report.isOfflineMode) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                  onClick = onOpenSettings,
                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                  Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("অনলাইন AI সার্ভার সেটআপ (সেটিংস)")
                }
              }

              if (report.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  Button(
                    onClick = onAcceptAll,
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("সব গ্রহণ করুন", maxLines = 1)
                  }
                  OutlinedButton(
                    onClick = { showFullDiff = !showFullDiff },
                    modifier = Modifier.weight(1f)
                  ) {
                    Text(if (showFullDiff) "তালিকা দেখুন" else "তুলনা দেখুন", maxLines = 1)
                  }
                }
              }
            }
          }
        }

        if (showFullDiff) {
          item {
            val diff = remember(editorState.content, report.suggestedFullText) {
              BengaliUtils.computeDiff(editorState.content, report.suggestedFullText)
            }
            LiteraryCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text("মূল ও প্রস্তাবিত সংশোধিত পাঠ্যের তুলনা", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                InlineDiffTextView(segments = diff)
              }
            }
          }
        } else if (report.issues.isEmpty() && !analysisState.isRunning) {
          item {
            EmptyStateView(
              icon = Icons.Outlined.FactCheck,
              title = "কোনো ত্রুটি পাওয়া যায়নি",
              description = "আপনার পান্ডুলিপির বানান, বিরামচিহ্ন ও বাক্য গঠন পরিচ্ছন্ন রয়েছে।"
            )
          }
        } else {
          items(report.issues, key = { it.id }) { issue ->
            val accepted = issue.id in analysisState.acceptedIssueIds
            val rejected = issue.id in analysisState.rejectedIssueIds
            ProofreadingIssueCard(
              issue = issue,
              isAccepted = accepted,
              isRejected = rejected,
              onAccept = { onAcceptIssue(issue) },
              onReject = { onRejectIssue(issue) }
            )
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun ProofreadingIssueCard(
  issue: ProofreadingIssue,
  isAccepted: Boolean,
  isRejected: Boolean,
  onAccept: () -> Unit,
  onReject: () -> Unit
) {
  LiteraryCard(
    modifier = Modifier.fillMaxWidth(),
    containerColor = when {
      isAccepted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
      isRejected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
      else -> MaterialTheme.colorScheme.surface
    }
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = "${issue.category}  ·  ${issue.severity}  ·  অনুচ্ছেদ ${BengaliUtils.toBengaliDigits(issue.paragraphIndex + 1)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = issue.originalText,
        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough),
        color = MaterialTheme.colorScheme.error
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "→ ${issue.suggestedText}",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = issue.explanation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (issue.poetryPreservationNote.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "কবিতা সুরক্ষা: ${issue.poetryPreservationNote}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (isAccepted) {
          Text("গৃহীত হয়েছে", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else if (isRejected) {
          Text("প্রত্যাখ্যাত", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          TextButton(onClick = onReject) {
            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("বাতিল")
          }
          Spacer(modifier = Modifier.width(6.dp))
          Button(onClick = onAccept) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("সংশোধন গ্রহণ")
          }
        }
      }
    }
  }
}
