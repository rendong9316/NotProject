package com.example.locationer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FavoritesScreen(
    mapViewModel: MapViewModel,
    favoritesViewModel: FavoritesViewModel,
    pendingRestoreUri: Uri? = null,
    isActive: Boolean = true,
    onRestoreUriHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val favorites by favoritesViewModel.favorites.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredItems = remember(favorites, searchQuery) {
        if (searchQuery.isBlank()) favorites
        else favorites.filter { it.label.contains(searchQuery.trim(), ignoreCase = true) }
    }

    var editingPoint by remember { mutableStateOf<FavoritesViewModel.FavoritePoint?>(null) }
    var deleteTarget by remember { mutableStateOf<FavoritesViewModel.FavoritePoint?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearBackupDialog by remember { mutableStateOf(false) }
    var showClearLegacyDialog by remember { mutableStateOf(false) }
    var offlineBackupCount by remember { mutableStateOf(0) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backupChecked by remember { mutableStateOf(false) }
    var autoBackupAvailable by remember { mutableStateOf(false) }

    fun showRestoreResult(restoredCount: Int) {
        Toast.makeText(
            context,
            if (restoredCount > 0) "已从文件恢复 ${restoredCount} 条收藏"
            else "文件解析失败，请选择 favorites.json",
            if (restoredCount > 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
        ).show()
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) showRestoreResult(favoritesViewModel.restoreFromUri(uri))
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.write(favoritesViewModel.exportToJson().toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            Toast.makeText(
                context,
                if (saved) "已导出 ${favorites.size} 条收藏" else "导出失败，请重试",
                if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(isActive, favorites.isEmpty()) {
        if (isActive && favorites.isEmpty() && !backupChecked) {
            offlineBackupCount = favoritesViewModel.countBackupRecords()
            showRestoreDialog = offlineBackupCount > 0
            backupChecked = true
        }
    }

    LaunchedEffect(isActive, favorites) {
        if (isActive && favorites.isNotEmpty()) {
            autoBackupAvailable = favoritesViewModel.backupCurrentFavorites()
        }
    }

    LaunchedEffect(pendingRestoreUri, isActive) {
        if (pendingRestoreUri != null && isActive) {
            showRestoreResult(favoritesViewModel.restoreFromUri(pendingRestoreUri))
            onRestoreUriHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索收藏名称") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    copyToClipboard(context, "全部收藏", favoritesViewModel.exportAll())
                    Toast.makeText(context, "已复制 ${favorites.size} 条收藏", Toast.LENGTH_SHORT).show()
                },
                enabled = favorites.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("复制全部")
            }
            Button(
                onClick = { saveLauncher.launch(FavoriteFileStore.FILE_NAME) },
                enabled = favorites.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("手动导出")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val restoredCount = favoritesViewModel.restoreFromOffline()
                    if (restoredCount > 0) showRestoreResult(restoredCount)
                    else restoreLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("从备份恢复")
            }
            Button(
                onClick = { showClearDialog = true },
                enabled = favorites.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("清空")
            }
        }

        if (favorites.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (autoBackupAvailable) "已自动备份为 ${FavoriteFileStore.AUTO_FILE_NAME}"
                    else "自动备份不可用，可使用手动导出",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (autoBackupAvailable) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                if (autoBackupAvailable) {
                    TextButton(onClick = { showClearLegacyDialog = true }) {
                        Text("清理旧备份")
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isBlank()) "暂无收藏" else "未找到匹配的收藏",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filteredItems, key = { it.id }) { point ->
                    FavoriteItemCard(
                        point = point,
                        onJump = { favoritesViewModel.jumpTo(point, mapViewModel) },
                        onEdit = { editingPoint = point },
                        onDelete = { deleteTarget = point },
                        onCopyGcj = {
                            val text = "%.6f,%.6f".format(point.gcjLon, point.gcjLat)
                            copyToClipboard(context, "收藏GCJ02", text)
                            Toast.makeText(context, "已复制GCJ02：$text", Toast.LENGTH_SHORT).show()
                        },
                        onCopyWgs = {
                            val text = "%.6f,%.6f".format(point.wgsLon, point.wgsLat)
                            copyToClipboard(context, "收藏WGS84", text)
                            Toast.makeText(context, "已复制WGS84：$text", Toast.LENGTH_SHORT).show()
                        },
                        onToggleExpanded = { expanded ->
                            favoritesViewModel.updateExpanded(point.id, expanded)
                        },
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            title = { Text("检测到备份数据") },
            text = { Text("系统下载目录中发现 $offlineBackupCount 条收藏备份，是否恢复？") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    showRestoreResult(favoritesViewModel.restoreFromOffline())
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("取消") }
            },
        )
    }

    editingPoint?.let { point ->
        var label by remember(point.id) { mutableStateOf(point.label) }
        AlertDialog(
            onDismissRequest = { editingPoint = null },
            title = { Text("重命名收藏") },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        favoritesViewModel.rename(point.id, label)
                        editingPoint = null
                    },
                    enabled = label.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingPoint = null }) { Text("取消") }
            },
        )
    }

    deleteTarget?.let { point ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("删除收藏") },
            text = { Text("确定删除“${point.label}”吗？") },
            confirmButton = {
                TextButton(onClick = {
                    favoritesViewModel.remove(point.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("清空全部收藏") },
            text = { Text("确定清空全部 ${favorites.size} 条收藏吗？公共目录备份将暂时保留。") },
            confirmButton = {
                TextButton(onClick = {
                    val hadBackup = favoritesViewModel.hasAutoSavedToPublic()
                    favoritesViewModel.clearAll()
                    showClearDialog = false
                    showClearBackupDialog = hadBackup
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    if (showClearBackupDialog) {
        AlertDialog(
            onDismissRequest = { showClearBackupDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("同时删除备份文件？") },
            text = { Text("删除后将无法通过备份恢复。是否删除系统下载目录中的备份？") },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = favoritesViewModel.clearBackup()
                    showClearBackupDialog = false
                    Toast.makeText(
                        context,
                        if (deleted) "备份文件已删除" else "备份删除失败",
                        if (deleted) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearBackupDialog = false }) { Text("保留备份") }
            },
        )
    }

    if (showClearLegacyDialog) {
        AlertDialog(
            onDismissRequest = { showClearLegacyDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("清理旧版重复备份？") },
            text = {
                Text(
                    "将删除下载目录中名为 favorites.json、favorites (数字).json 的旧文件，" +
                        "保留当前 ${FavoriteFileStore.AUTO_FILE_NAME}。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val deletedCount = favoritesViewModel.clearLegacyBackups()
                    showClearLegacyDialog = false
                    Toast.makeText(
                        context,
                        if (deletedCount > 0) "已删除 $deletedCount 个旧备份"
                        else "没有可直接删除的旧备份，请在下载目录中手动清理",
                        if (deletedCount > 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }) { Text("清理", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearLegacyDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FavoriteItemCard(
    point: FavoritesViewModel.FavoritePoint,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopyGcj: () -> Unit,
    onCopyWgs: () -> Unit,
    onToggleExpanded: (Boolean) -> Unit,
) {
    var expanded by remember(point.id) { mutableStateOf(point.isExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onJump)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = point.label,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                )
                IconButton(onClick = {
                    expanded = !expanded
                    onToggleExpanded(expanded)
                }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                }
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 6.dp, bottom = 8.dp)) {
                    FavoriteCoordRow("GCJ02", point.gcjLon, point.gcjLat, onCopyGcj)
                    FavoriteCoordRow("WGS84", point.wgsLon, point.wgsLat, onCopyWgs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCoordRow(label: String, lon: Double, lat: Double, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "%.6f,%.6f".format(lon, lat),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = "复制$label",
            modifier = Modifier.size(16.dp).clickable(onClick = onCopy),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
