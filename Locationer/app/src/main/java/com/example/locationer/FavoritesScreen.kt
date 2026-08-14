package com.example.locationer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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

@Composable
fun FavoritesScreen(
    mapViewModel: MapViewModel,
    favoritesViewModel: FavoritesViewModel,
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
                    Toast.makeText(context, "已导出 ${favorites.size} 条收藏", Toast.LENGTH_SHORT).show()
                },
                enabled = favorites.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导出全部收藏")
            }
            Button(
                onClick = { showClearDialog = true },
                enabled = favorites.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("清空")
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
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
                        onCopy = {
                            val text = favoritesViewModel.copyText(point.id) ?: return@FavoriteItemCard
                            copyToClipboard(context, "收藏坐标", text)
                            Toast.makeText(context, "已复制坐标", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
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
            text = { Text("确定清空全部 ${favorites.size} 条收藏吗？") },
            confirmButton = {
                TextButton(onClick = {
                    favoritesViewModel.clearAll()
                    showClearDialog = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
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
    onCopy: () -> Unit,
) {
    var expanded by rememberSaveable(point.id) { mutableStateOf(true) }
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
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                }
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 6.dp, bottom = 8.dp)) {
                    FavoriteCoordRow("GCJ02", point.gcjLon, point.gcjLat)
                    FavoriteCoordRow("WGS84", point.wgsLon, point.wgsLat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = onCopy) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制坐标")
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
private fun FavoriteCoordRow(label: String, lon: Double, lat: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${FavoritesViewModel.formatCoordinate(lon)},${FavoritesViewModel.formatCoordinate(lat)}",
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
