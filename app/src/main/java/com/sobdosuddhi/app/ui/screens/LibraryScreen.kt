package com.sobdosuddhi.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sobdosuddhi.app.data.local.FolderEntity
import com.sobdosuddhi.app.data.local.ManuscriptEntity
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.ManuscriptCategory
import com.sobdosuddhi.app.model.ManuscriptSortOption
import com.sobdosuddhi.app.ui.components.BengaliConfirmDialog
import com.sobdosuddhi.app.ui.components.EmptyStateView
import com.sobdosuddhi.app.ui.components.LiteraryCard
import com.sobdosuddhi.app.ui.components.SectionHeader

@Composable
fun LibraryScreen(
  manuscripts: List<ManuscriptEntity>,
  folders: List<FolderEntity>,
  searchQuery: String,
  selectedCategory: ManuscriptCategory?,
  selectedFolderId: Long?,
  sortOption: ManuscriptSortOption,
  onSearchQueryChange: (String) -> Unit,
  onCategoryFilterChange: (ManuscriptCategory?) -> Unit,
  onFolderFilterChange: (Long?) -> Unit,
  onSortOptionChange: (ManuscriptSortOption) -> Unit,
  onCreateFolder: (String) -> Unit,
  onDeleteFolder: (FolderEntity) -> Unit,
  onOpenManuscript: (Long) -> Unit,
  onRenameManuscript: (ManuscriptEntity, String) -> Unit,
  onMoveToFolder: (ManuscriptEntity, Long?) -> Unit,
  onTogglePin: (ManuscriptEntity) -> Unit,
  onDeleteManuscript: (Long) -> Unit,
  onNewManuscript: () -> Unit
) {
  var showSortMenu by remember { mutableStateOf(false) }
  var showNewFolderDialog by remember { mutableStateOf(false) }
  var newFolderName by remember { mutableStateOf("") }

  var renameTarget by remember { mutableStateOf<ManuscriptEntity?>(null) }
  var renameInput by remember { mutableStateOf("") }
  var moveTarget by remember { mutableStateOf<ManuscriptEntity?>(null) }
  var deleteTarget by remember { mutableStateOf<ManuscriptEntity?>(null) }
  var deleteFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }

  Column(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        SectionHeader(
          title = "আমার পান্ডুলিপি",
          subtitle = "মোট ${BengaliUtils.toBengaliDigits(manuscripts.size)}টি সংরক্ষিত লেখা",
          modifier = Modifier.weight(1f)
        )
        Row {
          IconButton(onClick = { showNewFolderDialog = true }) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "নতুন ফোল্ডার")
          }
          IconButton(onClick = { showSortMenu = true }) {
            Icon(Icons.Outlined.Sort, contentDescription = "সাজানোর ক্রম")
          }
          DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
            ManuscriptSortOption.entries.forEach { option ->
              DropdownMenuItem(
                text = { Text(option.bengaliLabel) },
                onClick = {
                  onSortOptionChange(option)
                  showSortMenu = false
                }
              )
            }
          }
        }
      }

      // Search Input
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("শিরোনাম, লেখক বা বিষয়বস্তু খুঁজুন…") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      // Category & Folder Filter Strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = selectedCategory == null && selectedFolderId == null,
          onClick = {
            onCategoryFilterChange(null)
            onFolderFilterChange(null)
          },
          label = { Text("সব লেখা") }
        )
        ManuscriptCategory.entries.forEach { cat ->
          FilterChip(
            selected = selectedCategory == cat,
            onClick = {
              onCategoryFilterChange(if (selectedCategory == cat) null else cat)
            },
            label = { Text(cat.shortLabel) }
          )
        }
        folders.forEach { folder ->
          FilterChip(
            selected = selectedFolderId == folder.id,
            onClick = {
              onFolderFilterChange(if (selectedFolderId == folder.id) null else folder.id)
            },
            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text(folder.name) }
          )
        }
      }

      if (selectedFolderId != null) {
        val activeFolder = folders.firstOrNull { it.id == selectedFolderId }
        if (activeFolder != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "ফোল্ডার: ${activeFolder.name}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { deleteFolderTarget = activeFolder }) {
              Text("ফোল্ডার মুছুন", color = MaterialTheme.colorScheme.error)
            }
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    if (manuscripts.isEmpty()) {
      EmptyStateView(
        icon = Icons.Outlined.FolderCopy,
        title = "কোনো পান্ডুলিপি পাওয়া যায়নি",
        description = "অনুসন্ধানের শর্ত পরিবর্তন করুন অথবা নতুন পান্ডুলিপি তৈরি করুন।",
        actionLabel = "নতুন পান্ডুলিপি তৈরি করুন",
        onAction = onNewManuscript
      )
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(manuscripts, key = { it.id }) { item ->
          val folderName = folders.firstOrNull { it.id == item.folderId }?.name
          val catLabel = ManuscriptCategory.fromName(item.category).shortLabel
          LiteraryCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenManuscript(item.id) }) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = (if (item.isPinned) "★ " else "") + item.title,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onTogglePin(item) }, modifier = Modifier.size(32.dp)) {
                  Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = "পিন",
                    tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = item.content.replace("\n", " ").trim().take(120),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = buildString {
                  append(catLabel)
                  if (!folderName.isNullOrBlank()) append("  ·  $folderName")
                  append("  ·  ${BengaliUtils.toBengaliDigits(item.wordCount)} শব্দ")
                  append("  ·  ${BengaliUtils.formatBengaliDate(item.updatedAt)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(8.dp))
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
              ) {
                TextButton(onClick = {
                  renameTarget = item
                  renameInput = item.title
                }) {
                  Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("নাম বদলান")
                }
                TextButton(onClick = { moveTarget = item }) {
                  Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("ফোল্ডার")
                }
                TextButton(onClick = { deleteTarget = item }) {
                  Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("মুছুন", color = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
      }
    }
  }

  // Dialogs
  if (showNewFolderDialog) {
    AlertDialog(
      onDismissRequest = { showNewFolderDialog = false },
      title = { Text("নতুন ফোল্ডার তৈরি করুন") },
      text = {
        OutlinedTextField(
          value = newFolderName,
          onValueChange = { newFolderName = it },
          label = { Text("ফোল্ডারের নাম") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(onClick = {
          onCreateFolder(newFolderName)
          newFolderName = ""
          showNewFolderDialog = false
        }) { Text("তৈরি করুন") }
      },
      dismissButton = {
        TextButton(onClick = { showNewFolderDialog = false }) { Text("বাতিল") }
      }
    )
  }

  if (renameTarget != null) {
    val ms = renameTarget!!
    AlertDialog(
      onDismissRequest = { renameTarget = null },
      title = { Text("পান্ডুলিপির নাম পরিবর্তন") },
      text = {
        OutlinedTextField(
          value = renameInput,
          onValueChange = { renameInput = it },
          label = { Text("নতুন শিরোনাম") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(onClick = {
          onRenameManuscript(ms, renameInput)
          renameTarget = null
        }) { Text("সংরক্ষণ") }
      },
      dismissButton = {
        TextButton(onClick = { renameTarget = null }) { Text("বাতিল") }
      }
    )
  }

  if (moveTarget != null) {
    val ms = moveTarget!!
    AlertDialog(
      onDismissRequest = { moveTarget = null },
      title = { Text("ফোল্ডার নির্বাচন করুন") },
      text = {
        Column(
          modifier = Modifier
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              onMoveToFolder(ms, null)
              moveTarget = null
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("কোনো ফোল্ডার নয়") }
          folders.forEach { folder ->
            OutlinedButton(
              onClick = {
                onMoveToFolder(ms, folder.id)
                moveTarget = null
              },
              modifier = Modifier.fillMaxWidth()
            ) { Text(folder.name) }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { moveTarget = null }) { Text("বন্ধ করুন") }
      }
    )
  }

  if (deleteTarget != null) {
    val ms = deleteTarget!!
    BengaliConfirmDialog(
      title = "পান্ডুলিপি মুছে ফেলুন",
      message = "আপনি কি নিশ্চিত যে ‘${ms.title}’ এবং এর সমস্ত খসড়া সংস্করণ স্থায়ীভাবে মুছে ফেলতে চান?",
      confirmLabel = "স্থায়ীভাবে মুছুন",
      isDestructive = true,
      onConfirm = {
        onDeleteManuscript(ms.id)
        deleteTarget = null
      },
      onDismiss = { deleteTarget = null }
    )
  }

  if (deleteFolderTarget != null) {
    val folder = deleteFolderTarget!!
    BengaliConfirmDialog(
      title = "ফোল্ডার মুছুন",
      message = "‘${folder.name}’ ফোল্ডারটি মুছে ফেলা হবে। এর ভেতরের পান্ডুলিপিগুলো নিরাপদ থাকবে।",
      confirmLabel = "মুছুন",
      isDestructive = true,
      onConfirm = {
        onDeleteFolder(folder)
        deleteFolderTarget = null
      },
      onDismiss = { deleteFolderTarget = null }
    )
  }
}
