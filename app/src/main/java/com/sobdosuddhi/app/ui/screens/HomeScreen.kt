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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sobdosuddhi.app.data.local.ManuscriptEntity
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.DashboardStats
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.ui.components.EmptyStateView
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.components.SectionHeader
import com.sobdosuddhi.app.ui.components.SobdosuddhiLogo

@Composable
fun HomeScreen(
  stats: DashboardStats,
  recentManuscripts: List<ManuscriptEntity>,
  onNewManuscript: (ManuscriptCategory) -> Unit,
  onQuickProofread: () -> Unit,
  onOpenAiDetection: () -> Unit,
  onOpenHumanRefinement: () -> Unit,
  onOpenOrganizer: () -> Unit,
  onOpenLibrary: () -> Unit,
  onImportDocument: () -> Unit,
  onOpenManuscript: (Long) -> Unit
) {
  val greeting = remember { BengaliUtils.greetingForCurrentTime() }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    // Brand Header & Welcome Greeting
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          SobdosuddhiLogo(size = 46.dp)
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = greeting,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "শব্দশুদ্ধি ১.০",
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
        }
        OutlinedButton(onClick = onImportDocument) {
          Icon(Icons.Outlined.UploadFile, contentDescription = "ফাইল আমদানি", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("আমদানি")
        }
      }
    }

    // Primary Focal CTA Card — নতুন পান্ডুলিপি তৈরি করুন
    item {
      LiteraryCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "নতুন পান্ডুলিপি তৈরি করুন",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "বাংলা কবিতা, গল্প, উপন্যাস বা প্রবন্ধ লিখুন—স্বয়ংক্রিয় খসড়া সংরক্ষণ, সংস্করণ নিয়ন্ত্রণ ও প্রুফরিডিং সুবিধাসহ।",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
          )
          Spacer(modifier = Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = { onNewManuscript(ManuscriptCategory.POETRY) },
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Outlined.Create, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("বাংলা কবিতা", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
              onClick = { onNewManuscript(ManuscriptCategory.PROSE) },
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("গল্প ও গদ্য", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }

    // Core Intelligence Actions Grid (2 columns, clean spacing)
    item {
      SectionHeader(
        title = "সাহিত্য ও পান্ডুলিপি সরঞ্জাম",
        subtitle = "প্রয়োজন অনুযায়ী নির্দিষ্ট বিশ্লেষণ নির্বাচন করুন"
      )
      Spacer(modifier = Modifier.height(12.dp))
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          DashboardActionTile(
            title = "দ্রুত প্রুফরিডিং",
            subtitle = "বানান, ব্যাকরণ ও কবিতার ছন্দ সুরক্ষা",
            icon = Icons.Outlined.FactCheck,
            onClick = onQuickProofread,
            modifier = Modifier.weight(1f)
          )
          DashboardActionTile(
            title = "AI লেখা শনাক্তকরণ",
            subtitle = "সম্ভাব্যতামূলক বাংলা ভাষাতাত্ত্বিক যাচাই",
            icon = Icons.Outlined.FindInPage,
            onClick = onOpenAiDetection,
            modifier = Modifier.weight(1f)
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          DashboardActionTile(
            title = "মানবিক ভাষায় রূপান্তর",
            subtitle = "সাবলীল ও স্বাভাবিক বাংলায় পরিমার্জন",
            icon = Icons.Outlined.AutoAwesome,
            onClick = onOpenHumanRefinement,
            modifier = Modifier.weight(1f)
          )
          DashboardActionTile(
            title = "পান্ডুলিপি সাজান",
            subtitle = "অধ্যায়, সূচিপত্র ও সামঞ্জস্য প্রতিবেদন",
            icon = Icons.Outlined.FormatListNumbered,
            onClick = onOpenOrganizer,
            modifier = Modifier.weight(1f)
          )
        }
        DashboardActionTile(
          title = "আমার সংরক্ষিত লেখা",
          subtitle = "সমস্ত পান্ডুলিপি, ফোল্ডার, সংস্করণ ইতিহাস ও ফাইল এক্সপোর্ট দেখুন",
          icon = Icons.Outlined.FolderCopy,
          onClick = onOpenLibrary,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    // Real Work Statistics (কাজের পরিসংখ্যান)
    item {
      SectionHeader(title = "কাজের পরিসংখ্যান")
      Spacer(modifier = Modifier.height(10.dp))
      LiteraryCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp, horizontal = 12.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          StatCell(value = BengaliUtils.toBengaliDigits(stats.totalManuscripts), label = "পান্ডুলিপি")
          StatCell(value = BengaliUtils.toBengaliDigits(stats.totalWords), label = "মোট শব্দ")
          StatCell(value = BengaliUtils.toBengaliDigits(stats.totalAnalyses), label = "বিশ্লেষণ")
          StatCell(value = BengaliUtils.toBengaliDigits(stats.totalVersions), label = "খসড়া সংস্করণ")
        }
      }
    }

    // Recent Works (সাম্প্রতিক কাজ)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        SectionHeader(title = "সাম্প্রতিক কাজ", modifier = Modifier.weight(1f))
      }
    }

    if (recentManuscripts.isEmpty()) {
      item {
        EmptyStateView(
          icon = Icons.Outlined.AutoStories,
          title = "এখনও কোনো পান্ডুলিপি নেই",
          description = "নতুন বাংলা কবিতা বা গল্প লিখে কিংবা DOCX, PDF, TXT ফাইল আমদানি করে কাজ শুরু করুন।",
          actionLabel = "নতুন লেখা শুরু করুন",
          onAction = { onNewManuscript(ManuscriptCategory.PROSE) }
        )
      }
    } else {
      items(recentManuscripts, key = { it.id }) { item ->
        RecentManuscriptRow(manuscript = item, onClick = { onOpenManuscript(item.id) })
      }
    }

    item { Spacer(modifier = Modifier.height(12.dp)) }
  }
}

@Composable
private fun DashboardActionTile(
  title: String,
  subtitle: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  LiteraryCard(modifier = modifier, onClick = onClick) {
    Column(modifier = Modifier.padding(14.dp)) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun StatCell(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun RecentManuscriptRow(
  manuscript: ManuscriptEntity,
  onClick: () -> Unit
) {
  val catLabel = ManuscriptCategory.fromName(manuscript.category).shortLabel
  LiteraryCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = manuscript.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = manuscript.content.replace("\n", " ").trim().take(110),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "$catLabel  ·  ${BengaliUtils.toBengaliDigits(manuscript.wordCount)} শব্দ  ·  ${BengaliUtils.formatBengaliDate(manuscript.updatedAt)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
