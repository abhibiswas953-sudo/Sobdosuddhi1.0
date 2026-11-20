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
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.AiDetectionLabel
import com.sobdosuddhi.app.model.ConfidenceLevel
import com.sobdosuddhi.app.ui.components.ErrorBannerCard
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.components.ProcessingProgressCard
import com.sobdosuddhi.app.ui.components.SectionHeader
import com.sobdosuddhi.app.ui.viewmodel.AnalysisUiState
import com.sobdosuddhi.app.ui.viewmodel.EditorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDetectionScreen(
  editorState: EditorUiState,
  analysisState: AnalysisUiState,
  isOnline: Boolean = true,
  onRunDetection: () -> Unit,
  onCancel: () -> Unit,
  onClearError: () -> Unit,
  onNavigateBack: () -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("AI লেখা শনাক্তকরণ", style = MaterialTheme.typography.titleLarge) },
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
      // Launch Panel
      item {
        LiteraryCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "বাংলা ভাষাতাত্ত্বিক ও শৈলীগত বিশ্লেষণ",
              style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "বাক্য গঠনের পুনরাবৃত্তি, অস্বাভাবিক সমরূপতা, গতানুগতিক অভিব্যক্তি, অনুচ্ছেদ কাঠামো ও স্বাভাবিক মানবিক বৈচিত্র্যসহ ১০টি বাংলা ভাষাতাত্ত্বিক মাত্রা বিশ্লেষণ করা হবে।",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            val isLong = editorState.content.length > 1500
            Text(
              text = "স্বয়ংক্রিয় মোড: ${if (isLong) "বিস্তারিত বিশ্লেষণ (দীর্ঘ পান্ডুলিপি)" else "দ্রুত বিশ্লেষণ (সংক্ষিপ্ত পান্ডুলিপি)"}  ·  ${BengaliUtils.toBengaliDigits(editorState.content.length)} অক্ষর",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (!isOnline) {
              Text(
                text = "অফলাইন মোড: AI লেখা শনাক্তকরণের জন্য ইন্টারনেট সংযোগ প্রয়োজন। ইন্টারনেট ফিরে এলে অনলাইন মোড স্বয়ংক্রিয়ভাবে সক্রিয় হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
              )
              Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
              onClick = onRunDetection,
              enabled = !analysisState.isRunning && editorState.content.isNotBlank(),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Outlined.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (analysisState.isRunning) "বিশ্লেষণ চলছে…" else "AI লেখা শনাক্তকরণ শুরু করুন")
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
            onRetry = onRunDetection,
            onDismiss = onClearError
          )
        }
      }

      val report = analysisState.aiDetectionReport
      if (report != null) {
        val label = AiDetectionLabel.fromName(report.classification)
        val confidence = ConfidenceLevel.fromName(report.confidenceLevel)

        // Primary Classification Result + Clear Separation of Indicator / Confidence / Authorship
        item {
          LiteraryCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = when (label) {
              AiDetectionLabel.LIKELY_HUMAN -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
              AiDetectionLabel.LIKELY_AI -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
              else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            }
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "সম্ভাব্যতামূলক শ্রেণিবিন্যাস",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = label.bengaliLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(10.dp))
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              Spacer(modifier = Modifier.height(10.dp))

              DetectionDistinctionRow("শনাক্তকরণ সূচক (Indicator)", report.detectionIndicatorLevel)
              Spacer(modifier = Modifier.height(6.dp))
              DetectionDistinctionRow("AI-লিখিত প্রবণতা", report.aiLikelihoodRange)
              Spacer(modifier = Modifier.height(6.dp))
              DetectionDistinctionRow("মানবিক লেখার প্রবণতা", report.humanLikelihoodRange)
              Spacer(modifier = Modifier.height(6.dp))
              DetectionDistinctionRow("মডেলের আত্মবিশ্বাসের স্তর", confidence.bengaliLabel)
              Spacer(modifier = Modifier.height(6.dp))
              DetectionDistinctionRow("প্রকৃত যাচাইকৃত লেখকত্ব", report.verifiedAuthorshipNote)

              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "${report.analysisMode}  ·  সময়: ${BengaliUtils.formatDuration(report.durationMs)}" +
                  if (report.fromCache) "  ·  ক্যাশ থেকে" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Mandatory Uncertainty Warning (Every Report)
        item {
          LiteraryCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
          ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
              Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = "সতর্কতা",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = report.uncertaintyWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }
          }
        }

        // Explanation & Overall Writing Characteristics
        item {
          LiteraryCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text("বিশ্লেষণের ব্যাখ্যা", style = MaterialTheme.typography.titleMedium)
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = report.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
              if (report.overallCharacteristics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("সামগ্রিক লেখার বৈশিষ্ট্য", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                report.overallCharacteristics.forEach { trait ->
                  Text(
                    text = "• $trait",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                  )
                }
              }
            }
          }
        }

        // Suspicious Sentences Highlighting
        if (report.suspiciousSentences.isNotEmpty()) {
          item {
            SectionHeader(
              title = "সন্দেহজনক বা যান্ত্রিক প্রবণতাসম্পন্ন বাক্য",
              subtitle = "এই বাক্যগুলোতে অনুমেয় বা গতানুগতিক গঠন লক্ষ্য করা গেছে"
            )
          }
          items(report.suspiciousSentences) { item ->
            LiteraryCard(
              modifier = Modifier.fillMaxWidth(),
              containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = "সংকেতের মাত্রা: ${item.signalStrength}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "“${item.sentence}”",
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = item.reason,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Paragraph-Level Analysis
        if (report.paragraphAnalysis.isNotEmpty()) {
          item {
            SectionHeader(title = "অনুচ্ছেদভিত্তিক বিশ্লেষণ")
          }
          items(report.paragraphAnalysis) { para ->
            LiteraryCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "অনুচ্ছেদ ${BengaliUtils.toBengaliDigits(para.paragraphIndex + 1)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = para.assessment,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                  )
                }
                if (para.excerpt.isNotBlank()) {
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = para.excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = para.observation,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // 10 Bengali Linguistic Dimensions
        if (report.linguisticDimensions.isNotEmpty()) {
          item {
            SectionHeader(title = "বাংলা ভাষাতাত্ত্বিক মাত্রা বিশ্লেষণ")
          }
          items(report.linguisticDimensions) { dim ->
            LiteraryCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(dim.dimensionName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                  Text(
                    text = dim.tendency,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = dim.observation,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun DetectionDistinctionRow(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
