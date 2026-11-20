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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sobdosuddhi.app.BuildConfig
import com.sobdosuddhi.app.data.ai.ServerHealthResult
import com.sobdosuddhi.app.data.ai.ServerHealthStatus
import com.sobdosuddhi.app.data.local.UserPreferences
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.ProofreadingMode
import com.sobdosuddhi.app.model.ThemeMode
import com.sobdosuddhi.app.ui.components.BengaliConfirmDialog
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.components.SectionHeader

@Composable
fun SettingsScreen(
  preferences: UserPreferences,
  serverHealth: ServerHealthResult? = null,
  isCheckingServer: Boolean = false,
  onThemeModeChange: (ThemeMode) -> Unit,
  onFontSizeChange: (Float) -> Unit,
  onDefaultProofreadingModeChange: (ProofreadingMode) -> Unit,
  onAutoSaveChange: (Boolean) -> Unit,
  onPoetryProtectionChange: (Boolean) -> Unit,
  onUseCacheChange: (Boolean) -> Unit,
  onCustomProxyUrlSave: (String) -> Unit = {},
  onTestServerConnection: () -> Unit = {},
  onExportBackup: () -> Unit,
  onClearHistoryCache: () -> Unit
) {
  var showClearCacheConfirm by remember { mutableStateOf(false) }
  var proxyUrlDraft by remember(preferences.customProxyUrl) { mutableStateOf(preferences.customProxyUrl) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      SectionHeader(
        title = "সেটিংস ও গোপনীয়তা",
        subtitle = "থিম, সম্পাদকের ফন্ট, প্রুফরিডিং ও পান্ডুলিপি নিরাপত্তা নিয়ন্ত্রণ করুন"
      )
    }

    // Theme Mode (Light / Dark / System)
    item {
      LiteraryCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("অ্যাপের থিম", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
              FilterChip(
                selected = preferences.themeMode == mode,
                onClick = { onThemeModeChange(mode) },
                label = { Text(mode.bengaliLabel) }
              )
            }
          }
        }
      }
    }

    // Typography & Font Size
    item {
      LiteraryCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "সম্পাদকের ফন্টের আকার (${BengaliUtils.toBengaliDigits(preferences.editorFontSizeSp.toInt())} পয়েন্ট)",
            style = MaterialTheme.typography.titleMedium
          )
          Slider(
            value = preferences.editorFontSizeSp,
            onValueChange = onFontSizeChange,
            valueRange = 13f..26f,
            steps = 12
          )
          Text(
            text = "বাংলা সাহিত্য ও পান্ডুলিপির শুদ্ধতা রক্ষায় শব্দশুদ্ধি ১.০ আপনার বিশ্বস্ত সঙ্গী।",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = preferences.editorFontSizeSp.sp),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    // Proofreading & Editor Behavior
    item {
      LiteraryCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("প্রুফরিডিং ও সম্পাদনা পছন্দ", style = MaterialTheme.typography.titleMedium)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProofreadingMode.entries.forEach { mode ->
              FilterChip(
                selected = preferences.defaultProofreadingMode == mode,
                onClick = { onDefaultProofreadingModeChange(mode) },
                label = { Text(mode.bengaliLabel, maxLines = 1) }
              )
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          SettingSwitchRow(
            title = "স্বয়ংক্রিয় খসড়া সংরক্ষণ",
            subtitle = "লেখার সময় নিয়মিত বিরতিতে ব্যক্তিগত ডেটাবেসে খসড়া সংরক্ষিত হবে",
            checked = preferences.autoSaveEnabled,
            onCheckedChange = onAutoSaveChange
          )
          SettingSwitchRow(
            title = "কঠোর কবিতা সুরক্ষা নীতি",
            subtitle = "কবিতার ছন্দ, অন্ত্যমিল, চিত্রকল্প ও পঙ্ক্তির বিন্যাস সর্বদা সুরক্ষিত রাখুন",
            checked = preferences.poetryProtectionStrict,
            onCheckedChange = onPoetryProtectionChange
          )
          SettingSwitchRow(
            title = "অপরিবর্তিত লেখায় ক্যাশ ব্যবহার",
            subtitle = "লেখা না বদলালে পুনরায় AI অনুরোধ না পাঠিয়ে পূর্ববর্তী বিশ্লেষণ তাৎক্ষণিক দেখান",
            checked = preferences.useCacheForUnchangedText,
            onCheckedChange = onUseCacheChange
          )
        }
      }
    }

    // Privacy & Security Center
    item {
      LiteraryCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("গোপনীয়তা ও পান্ডুলিপি নিরাপত্তা", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text = "• আপনার সমস্ত অপ্রকাশিত পান্ডুলিপি ও খসড়া সংস্করণ কেবল আপনার ডিভাইসের ব্যক্তিগত (private) স্টোরেজে সংরক্ষিত থাকে।\n" +
              "• ইন্টারনেট ছাড়াই লেখা সংরক্ষণ, সংস্করণ নিয়ন্ত্রণ ও অফলাইন বানান পরীক্ষা সম্পূর্ণভাবে কাজ করে।\n" +
              "• কোনো API কী বা গোপন তথ্য APK ফাইলের ভেতরে রাখা হয়নি—সমস্ত AI অনুরোধ নিরাপদ সার্ভার-সাইড HTTPS প্রোক্সির মাধ্যমে প্রক্রিয়াকরণ হয়।\n" +
              "• আপনার সুস্পষ্ট অনুমতি ছাড়া কোনো লেখা মডেল প্রশিক্ষণে বা তৃতীয় পক্ষের সাথে ভাগ করা হয় না।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
              Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("ব্যাকআপ এক্সপোর্ট", maxLines = 1)
            }
            OutlinedButton(onClick = { showClearCacheConfirm = true }, modifier = Modifier.weight(1f)) {
              Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("ক্যাশ মুছুন", maxLines = 1)
            }
          }
        }
      }
    }

    // Server-Side HTTPS AI Proxy Configuration (Zero API keys in APK)
    item {
      LiteraryCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("অনলাইন AI সার্ভার সংযোগ", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text = "স্থানীয় বাংলা প্রুফরিডিং, স্টাইলোমেট্রি ও পরিমার্জন ইঞ্জিন সর্বদা অফলাইনেও কাজ করে। পূর্ণ ক্লাউড Gemini AI ব্যবহারের জন্য আপনার পাবলিক HTTPS প্রোক্সি সার্ভারের ঠিকানা (যেখানে API কী কেবল সার্ভারে সুরক্ষিত থাকে) এখানে যুক্ত করতে পারেন:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = proxyUrlDraft,
            onValueChange = { proxyUrlDraft = it },
            label = { Text("পাবলিক HTTPS সার্ভার প্রোক্সি URL (ঐচ্ছিক)") },
            placeholder = { Text("https://your-sobdosuddhi-proxy.run.app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = { onCustomProxyUrlSave(proxyUrlDraft) },
              modifier = Modifier.weight(1f)
            ) {
              Text("সার্ভার সংরক্ষণ", maxLines = 1)
            }
            OutlinedButton(
              onClick = onTestServerConnection,
              enabled = !isCheckingServer,
              modifier = Modifier.weight(1f)
            ) {
              Text(if (isCheckingServer) "যাচাই হচ্ছে…" else "সংযোগ পরীক্ষা", maxLines = 1)
            }
          }
          if (serverHealth != null) {
            val statusColor = when (serverHealth.status) {
              ServerHealthStatus.READY -> MaterialTheme.colorScheme.primary
              ServerHealthStatus.NO_API_KEY_ON_SERVER -> MaterialTheme.colorScheme.secondary
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
              text = serverHealth.bengaliMessage,
              style = MaterialTheme.typography.bodySmall,
              color = statusColor
            )
          }
        }
      }
    }

    // App Version & Technical Identity
    item {
      LiteraryCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("শব্দশুদ্ধি ১.০ (Sobdosuddhi)", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "সংস্করণ: ${BengaliUtils.toBengaliDigits(BuildConfig.VERSION_NAME)}  ·  প্যাকেজ: ${BuildConfig.APPLICATION_ID}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "নেটিভ অ্যান্ড্রয়েড (Jetpack Compose • Material Design 3 • Room • বাংলা ইউনিকোড)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    item { Spacer(modifier = Modifier.height(20.dp)) }
  }

  if (showClearCacheConfirm) {
    BengaliConfirmDialog(
      title = "বিশ্লেষণ ক্যাশ ও ইতিহাস মুছুন",
      message = "সমস্ত পূর্ববর্তী প্রুফরিডিং ও বিশ্লেষণ ক্যাশ মুছে ফেলা হবে। আপনার সংরক্ষিত পান্ডুলিপি ও খসড়া সংস্করণ সম্পূর্ণ নিরাপদ থাকবে।",
      confirmLabel = "মুছে ফেলুন",
      isDestructive = true,
      onConfirm = {
        onClearHistoryCache()
        showClearCacheConfirm = false
      },
      onDismiss = { showClearCacheConfirm = false }
    )
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.bodyMedium)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
