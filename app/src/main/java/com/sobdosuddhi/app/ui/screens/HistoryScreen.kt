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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sobdosuddhi.app.data.local.AnalysisHistoryEntity
import com.sobdosuddhi.app.data.local.DraftVersionEntity
import com.sobdosuddhi.app.data.local.ManuscriptEntity
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.AnalysisType
import com.sobdosuddhi.app.model.VersionSourceType
import com.sobdosuddhi.app.ui.components.EmptyStateView
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.components.SectionHeader

@Composable
fun HistoryScreen(
  analysisHistory: List<AnalysisHistoryEntity>,
  recentVersions: List<DraftVersionEntity>,
  manuscripts: List<ManuscriptEntity>,
  onReopenReport: (AnalysisHistoryEntity) -> Unit,
  onDeleteHistoryEntry: (Long) -> Unit,
  onOpenManuscript: (Long) -> Unit
) {
  var activeTab by remember { mutableIntStateOf(0) } // 0 = বিশ্লেষণ ইতিহাস, 1 = খসড়া সংস্করণ
  val titleById = remember(manuscripts) { manuscripts.associate { it.id to it.title } }

  Column(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      SectionHeader(
        title = "ইতিহাস ও সংস্করণ",
        subtitle = "পূর্ববর্তী প্রুফরিডিং, AI বিশ্লেষণ ও খসড়া সংস্করণের সম্পূর্ণ তালিকা"
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = activeTab == 0,
          onClick = { activeTab = 0 },
          label = { Text("বিশ্লেষণ ইতিহাস (${BengaliUtils.toBengaliDigits(analysisHistory.size)})") }
        )
        FilterChip(
          selected = activeTab == 1,
          onClick = { activeTab = 1 },
          label = { Text("খসড়া সংস্করণ (${BengaliUtils.toBengaliDigits(recentVersions.size)})") }
        )
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    if (activeTab == 0) {
      if (analysisHistory.isEmpty()) {
        EmptyStateView(
          icon = Icons.Outlined.History,
          title = "এখনও কোনো বিশ্লেষণ ইতিহাস নেই",
          description = "প্রুফরিডিং, AI শনাক্তকরণ, মানবিক রূপান্তর বা পান্ডুলিপি বিন্যাস চালালে ফলাফল এখানে সংরক্ষিত থাকবে।"
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(analysisHistory, key = { it.id }) { entry ->
            val typeLabel = AnalysisType.fromName(entry.analysisType).bengaliLabel
            LiteraryCard(modifier = Modifier.fillMaxWidth(), onClick = { onReopenReport(entry) }) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                  )
                  IconButton(onClick = { onDeleteHistoryEntry(entry.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                      Icons.Outlined.Delete,
                      contentDescription = "মুছুন",
                      modifier = Modifier.size(16.dp),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                Text(
                  text = entry.manuscriptTitle,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = entry.summary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "${BengaliUtils.formatBengaliDate(entry.createdAt)}  ·  সময়: ${BengaliUtils.formatDuration(entry.durationMs)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
          item { Spacer(modifier = Modifier.height(16.dp)) }
        }
      }
    } else {
      if (recentVersions.isEmpty()) {
        EmptyStateView(
          icon = Icons.Outlined.History,
          title = "কোনো খসড়া সংস্করণ নেই",
          description = "পান্ডুলিপি সংরক্ষণ করলে প্রতিটি সংস্করণ এখানে ধারাবাহিকভাবে যুক্ত হবে।"
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(recentVersions, key = { it.id }) { ver ->
            val msTitle = titleById[ver.manuscriptId] ?: "সংরক্ষিত পান্ডুলিপি"
            LiteraryCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenManuscript(ver.manuscriptId) }) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = "$msTitle  ·  সংস্করণ ${BengaliUtils.toBengaliDigits(ver.versionNumber)}",
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${ver.label} (${VersionSourceType.fromName(ver.sourceType).bengaliLabel})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary
                )
                if (ver.changeSummary.isNotBlank()) {
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = ver.changeSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "${BengaliUtils.toBengaliDigits(ver.wordCount)} শব্দ  ·  ${BengaliUtils.formatBengaliDate(ver.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  TextButton(onClick = { onOpenManuscript(ver.manuscriptId) }) {
                    Text("সম্পাদকে খুলুন")
                  }
                }
              }
            }
          }
          item { Spacer(modifier = Modifier.height(16.dp)) }
        }
      }
    }
  }
}
