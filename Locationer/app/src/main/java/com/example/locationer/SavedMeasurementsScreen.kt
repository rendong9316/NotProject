package com.example.locationer

import android.content.ClipData
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
import androidx.compose.material.icons.filled.Straighten
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
import androidx.compose.material3.Surface
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

/**
 * 历史测量页面。
 * 展示已保存的测量记录列表，支持：
 * - 点击卡片跳转地图并回放蓝色区域与节点
 * - 展开查看每个节点的 GCJ02 / WGS84 坐标
 * - 重命名 / 删除 / 导出 / 从文件恢复
 */
@Composable
fun SavedMeasurementsScreen(
    mapViewModel       : MapViewModel,
    savedStore         : SavedMeasurementsStore,
    pendingRestoreUri  : Uri? = null,
    isActive           : Boolean = true,
    onRestoreUriHandled: () -> Unit = {},
) {
    val context          = LocalContext.current
    val records          by savedStore.records.collectAsState()
    var searchQuery      by rememberSaveable { mutableStateOf("") }
    val filteredItems = remember(records, searchQuery) {
        if (searchQuery.isBlank()) records
        else records.filter { it.label.contains(searchQuery.trim(), ignoreCase = true) }
    }

    var editingRecord       by remember { mutableStateOf<SavedMeasurementRecord?>(null) }
    var deleteTarget        by remember { mutableStateOf<SavedMeasurementRecord?>(null) }
    var showClearDialog             by remember { mutableStateOf(false) }
    var offlineBackupCount   by remember { mutableStateOf(0) }
    var showRestoreDialog       by remember { mutableStateOf(false) }
    var backupChecked          by remember { mutableStateOf(false) }
    var autoBackupAvailable    by remember { mutableStateOf(false) }

    fun showRestoreResult(restoredCount: Int) {
        Toast.makeText(
            context,
            if (restoredCount > 0) "已从文件恢复 ${restoredCount} 条测量记录"
            else "文件解析失败，请选择 measurements.json",
            if (restoredCount > 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
        ).show()
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) showRestoreResult(savedStore.restoreFromUri(uri))
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.write(savedStore.exportToJson().toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            Toast.makeText(
                context,
                if (saved) "已导出 ${records.size} 条测量记录" else "导出失败，请重试",
                if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(isActive, records.isEmpty()) {
        if (isActive && records.isEmpty() && !backupChecked) {
            offlineBackupCount = savedStore.countBackupRecords()
            showRestoreDialog = offlineBackupCount > 0
            backupChecked = true
        }
    }

    LaunchedEffect(isActive, records) {
        if (isActive && records.isNotEmpty()) {
            autoBackupAvailable = savedStore.hasAutoSaved()
        }
    }

    LaunchedEffect(pendingRestoreUri, isActive) {
        if (pendingRestoreUri != null && isActive) {
            showRestoreResult(savedStore.restoreFromUri(pendingRestoreUri))
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
            placeholder = { Text("搜索测量名称") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    copyToClipboard(context, "全部测量", savedStore.exportAll())
                    Toast.makeText(context, "已复制 ${records.size} 条测量", Toast.LENGTH_SHORT).show()
                },
                enabled = records.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("复制全部")
            }
            Button(
                onClick = { saveLauncher.launch("measurements.json") },
                enabled = records.isNotEmpty(),
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
                    val count = savedStore.countBackupRecords()
                    if (count > 0) {
                        showRestoreResult(savedStore.restoreFromOffline())
                    } else {
                        restoreLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("从备份恢复")
            }
            Button(
                onClick = { showClearDialog = true },
                enabled = records.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("清空")
            }
        }

        if (records.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (autoBackupAvailable) "已自动备份为 Locationer-saved-measurements.json"
                    else "自动备份不可用，可使用手动导出",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (autoBackupAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isBlank()) "暂无历史测量" else "未找到匹配的测量记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filteredItems, key = { it.id }) { record ->
                    SavedMeasurementCard(
                        record           = record,
                        onJump           = { savedStore.jumpTo(record, mapViewModel) },
                        onEdit           = { editingRecord = record },
                        onDelete         = { deleteTarget = record },
                        onCopy           = {
                            copyToClipboard(context, "测量记录", record.formatForClipboard())
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        onToggleExpanded = { expanded -> savedStore.updateExpanded(record.id, expanded) },
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }

    // ── 重命名弹框 ──
    editingRecord?.let { record ->
        var label by remember(record.id) { mutableStateOf(record.label) }
        AlertDialog(
            onDismissRequest = { editingRecord = null },
            title = { Text("重命名测量") },
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
                        savedStore.rename(record.id, label)
                        editingRecord = null
                    },
                    enabled = label.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingRecord = null }) { Text("取消") } },
        )
    }

    // ── 删除单条确认 ──
    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("删除测量记录") },
            text = { Text("确定删除测量「${record.label}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    savedStore.remove(record.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    // ── 清空全部确认 ──
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("清空全部测量记录") },
            text = { Text("确定清空全部 ${records.size} 条测量记录吗？公共目录备份将暂时保留。") },
            confirmButton = {
                TextButton(onClick = {
                    savedStore.clearAll()
                    showClearDialog = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } },
        )
    }
}

// ============================================================================
// 卡片组件
// ============================================================================

@Composable
private fun SavedMeasurementCard(
    record           : SavedMeasurementRecord,
    onJump           : () -> Unit,
    onEdit           : () -> Unit,
    onDelete         : () -> Unit,
    onCopy           : () -> Unit,
    onToggleExpanded : (Boolean) -> Unit,
) {
    var expanded by remember(record.id) { mutableStateOf(record.isExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
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
                    Icons.Filled.Straighten,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = record.label,
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
                    // 摘要信息
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val modeText = if (record.mode == "AREA") "测面积" else "测距"
                            Text(
                                text = "%s  ·  %d 个节点  ·  累计 %s".format(
                                    modeText, record.waypoints.size, MapViewModel.formatDist(record.totalDist)
                                ),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            if (record.mode == "AREA" && record.totalArea > 0.0) {
                                Text(
                                    text = "面积 %s".format(MapViewModel.formatArea(record.totalArea)),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // 各节点坐标（GCJ02 + WGS84）
                    record.waypoints.forEachIndexed { i, wp ->
                        SavedCoordRow(label = "点 ${i + 1}",
                            gcjLon = wp.gcjLon, gcjLat = wp.gcjLat,
                            wgsLon = wp.wgsLon, wgsLat = wp.wgsLat)
                    }
                    Spacer(Modifier.height(4.dp))
                    // 操作按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = onCopy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制", fontSize = 12.sp)
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "重命名")
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
private fun SavedCoordRow(
    label  : String,
    gcjLon : Double, gcjLat: Double,
    wgsLon : Double, wgsLat: Double,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(1.dp))
        CoordLine(label = "GCJ02", lon = gcjLon, lat = gcjLat, context = context)
        CoordLine(label = "WGS84", lon = wgsLon, lat = wgsLat, context = context)
    }
}

@Composable
private fun CoordLine(label: String, lon: Double, lat: Double, context: Context) {
    val text = "%.6f,%.6f".format(lon, lat)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = "复制$label",
            modifier = Modifier.size(16.dp).clickable {
                copyToClipboard(context, "$label 坐标", text)
                Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
