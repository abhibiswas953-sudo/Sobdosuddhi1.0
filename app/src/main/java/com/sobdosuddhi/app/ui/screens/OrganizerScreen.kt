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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.ui.components.BengaliConfirmDialog
import com.sobdosuddhi.app.ui.components.ErrorBannerCard
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.components.ProcessingProgressCard
import com.sobdosuddhi.app.ui.components.SectionHeader
import com.sobdosuddhi.app.ui.viewmodel.AnalysisUiState
import com.sobdosuddhi.app.ui.viewmodel.EditorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerScreen(
  editorState: EditorUiState,
  analysisState: AnalysisUiState,
  onUpdateBookMetadata: (preface: String, dedication: String, epilogue: String) -> Unit,
  onRunOrganizer: (includeAi: Boolean) -> Unit,
  onCancel: () -> Unit,
  onClearError: () -> Unit,
  onApplyApprovedFormatting: () -> Unit,
  onNavigateBack: () -> Unit
) {
  var preface by remember(editorState.manuscriptId, editorState.preface) { mutableStateOf(editorState.preface) }
  var dedication by remember(editorState.manuscriptId, editorState.dedication) { mutableStateOf(editorState.dedication) }
  var epilogue by remember(editorState.manuscriptId, editorState.epilogue) { mutableStateOf(editorState.epilogue) }
  var showApplyConfirm by remember { mutableStateOf(false) }

  val isPoetry = editorState.category == ManuscriptCategory.POETRY

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("পান্ডুলিপি সাজান", style = MaterialTheme.typography.titleLarge) },
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
      // Book / Poetry Collection Front & Back Matter Metadata
      item {
        LiteraryCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = if (isPoetry) "কবিতা সংকলনের অংশসমূহ" else "বই ও পান্ডুলিপির অংশসমূহ",
              style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
              value = dedication,
              onValueChange = {
                dedication = it
                onUpdateBookMetadata(preface, it, epilogue)
              },
              label = { Text("উৎসর্গ") },
              minLines = 1,
              maxLines = 3,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = preface,
              onValueChange = {
                preface = it
                onUpdateBookMetadata(it, dedication, epilogue)
              },
              label = { Text("ভূমিকা") },
              minLines = 2,
              maxLines = 4,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = epilogue,
              onValueChange = {
                epilogue = it
                onUpdateBookMetadata(preface, dedication, it)
              },
              label = { Text("শেষাংশ / উপসংহার") },
              minLines = 1,
              maxLines = 3,
              modifier = Modifier.fillMaxWidth()
            )
            Text(
              text = "DOCX, PDF বা TXT এক্সপোর্টে উৎসর্গ, ভূমিকা ও শেষাংশ স্বয়ংক্রিয়ভাবে যুক্ত হবে।",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Run Structure Analysis Buttons
      item {
        LiteraryCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("পান্ডুলিপি কাঠামো ও সামঞ্জস্য বিশ্লেষণ", style = MaterialTheme.typography.titleMedium)
            Text(
              text = if (isPoetry) {
                "কবিতার শিরোনাম, সূচিপত্র, পুনরাবৃত্ত পঙ্ক্তি, বিন্যাস ও সংকলন পরামর্শ তৈরি করা হবে। আপনার অনুমতি ছাড়া কবিতার ক্রম কখনোই বদলানো হবে না।"
              } else {
                "অধ্যায় ও অনুচ্ছেদ বিন্যাস, সূচিপত্র, পুনরাবৃত্ত অংশ, চরিত্রের নাম, পরিভাষা এবং স্থান-সময়ের সামঞ্জস্য বিশ্লেষণ করা হবে।"
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              Button(
                onClick = { onRunOrganizer(true) },
                enabled = !analysisState.isRunning && editorState.content.isNotBlank(),
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Outlined.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("সম্পূর্ণ বিশ্লেষণ", maxLines = 1)
              }
              OutlinedButton(
                onClick = { onRunOrganizer(false) },
                enabled = !analysisState.isRunning && editorState.content.isNotBlank(),
                modifier = Modifier.weight(1f)
              ) {
                Text("তাৎক্ষণিক কাঠামো", maxLines = 1)
              }
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
            onRetry = { onRunOrganizer(true) },
            onDismiss = onClearError
          )
        }
      }

      val report = analysisState.organizationReport
      if (report != null) {
        // Structure Metrics Overview
        item {
          LiteraryCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text("পান্ডুলিপি কাঠামো প্রতিবেদন", style = MaterialTheme.typography.titleLarge)
              Spacer(modifier = Modifier.height(12.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                StructureStat(BengaliUtils.toBengaliDigits(report.totalChapters), if (isPoetry) "বিভাগ/শিরোনাম" else "অধ্যায়")
                StructureStat(BengaliUtils.toBengaliDigits(report.totalParagraphs), if (isPoetry) "স্তবক" else "অনুচ্ছেদ")
                StructureStat(BengaliUtils.toBengaliDigits(report.totalWords), "মোট শব্দ")
                StructureStat(BengaliUtils.toBengaliDigits(report.totalCharacters), "মোট অক্ষর")
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = report.approvalRequiredNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
              if (report.proposedFormattedContent != editorState.content) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                  onClick = { showApplyConfirm = true },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("অনুমোদিত বিন্যাস প্রয়োগ করুন")
                }
              }
            }
          }
        }

        // Table of Contents (সূচিপত্র)
        item {
          SectionHeader(
            title = if (isPoetry) "কবিতার সূচিপত্র" else "পান্ডুলিপির সূচিপত্র",
            subtitle = "শনাক্তকৃত অধ্যায় ও শিরোনাম"
          )
        }
        item {
          LiteraryCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              if (report.tableOfContents.isEmpty()) {
                Text(
                  text = "কোনো শিরোনাম বা অধ্যায় শনাক্ত হয়নি। সম্পাদকে ‘শিরোনাম’ বোতাম ব্যবহার করে অধ্যায় বা কবিতার শিরোনাম যুক্ত করুন।",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              } else {
                report.tableOfContents.forEachIndexed { idx, entry ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = "${BengaliUtils.toBengaliDigits(idx + 1)}. ${if (entry.level > 1) "  " else ""}${entry.title}",
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (entry.level == 1) FontWeight.SemiBold else FontWeight.Normal,
                      modifier = Modifier.weight(1f)
                    )
                    Text(
                      text = "অনুচ্ছেদ ${BengaliUtils.toBengaliDigits(entry.paragraphIndex + 1)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  if (idx < report.tableOfContents.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                  }
                }
              }
            }
          }
        }

        // Character List (for Stories & Novels)
        if (report.characterList.isNotEmpty()) {
          item {
            SectionHeader(title = "শনাক্তকৃত চরিত্র তালিকা")
          }
          item {
            LiteraryCard(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = report.characterList.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp)
              )
            }
          }
        }

        // Repeated Paragraphs & Sentences
        if (report.repeatedSegments.isNotEmpty()) {
          item {
            SectionHeader(title = "পুনরাবৃত্ত অনুচ্ছেদ ও বাক্য (${BengaliUtils.toBengaliDigits(report.repeatedSegments.size)})")
          }
          items(report.repeatedSegments) { seg ->
            LiteraryCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = "${seg.type}  ·  ${BengaliUtils.toBengaliDigits(seg.occurrences)} বার পাওয়া গেছে",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(seg.text, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }

        // Consistency Findings (Character names, terminology, time & place)
        if (report.consistencyFindings.isNotEmpty()) {
          item {
            SectionHeader(title = "সামঞ্জস্য ও ধারাবাহিকতা পর্যবেক্ষণ")
          }
          items(report.consistencyFindings) { finding ->
            LiteraryCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = finding.category,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(finding.title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = finding.details,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Formatting Issues, Missing Headings & Organizational Improvements
        item {
          LiteraryCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (report.formattingIssues.isNotEmpty()) {
                Text("বিন্যাসগত অসঙ্গতি", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                report.formattingIssues.forEach { issue ->
                  Text("• $issue", style = MaterialTheme.typography.bodySmall)
                }
              }
              if (report.missingHeadings.isNotEmpty()) {
                Text("অনুপস্থিত শিরোনাম", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                report.missingHeadings.forEach { m ->
                  Text("• $m", style = MaterialTheme.typography.bodySmall)
                }
              }
              Text("পান্ডুলিপি উন্নয়নের পরামর্শ", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
              report.suggestedImprovements.forEach { imp ->
                Text("• $imp", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }

  if (showApplyConfirm) {
    BengaliConfirmDialog(
      title = "বিন্যাস প্রয়োগ নিশ্চিত করুন",
      message = "অনুমোদিত অনুচ্ছেদ ও স্পেসিং বিন্যাস প্রয়োগ করা হবে এবং একটি নতুন খসড়া সংস্করণ সংরক্ষিত হবে। কোনো কবিতা বা অধ্যায়ের ক্রম পরিবর্তন করা হবে না।",
      confirmLabel = "প্রয়োগ করুন",
      onConfirm = {
        onApplyApprovedFormatting()
        showApplyConfirm = false
      },
      onDismiss = { showApplyConfirm = false }
    )
  }
}

@Composable
private fun StructureStat(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
