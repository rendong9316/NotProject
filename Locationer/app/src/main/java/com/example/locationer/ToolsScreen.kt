package com.example.locationer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.filterNotNull

/**
 * 工具箱标签页：旗标管理 / 距离方位角计算 / 折线测距与面积测量
 */
@Composable
fun ToolsScreen(
    mapViewModel: MapViewModel = viewModel(),
) {
    val flags by mapViewModel.flags.collectAsState()
    val gcj     by mapViewModel.currentGcj.collectAsState()
    val wgs     by mapViewModel.currentWgs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlagManagementCard(androidx.compose.ui.platform.LocalContext.current, flags, mapViewModel, gcj, wgs)
        DistanceBearingCard(mapViewModel)
        MeasurementCard(mapViewModel)
    }
}

// ============================================================================
// 卡片 1：旗标管理
// ============================================================================

@Composable
private fun FlagManagementCard(
    context     : android.content.Context,
    flags       : List<Flag>,
    mapViewModel: MapViewModel,
    currentGcj  : CT.Coord?,
    currentWgs  : CT.Coord?,
) {
    // 手动添加表单状态
    var showAddForm by remember { mutableStateOf(false) }
    var addLabel    by remember { mutableStateOf("") }
    var addLon      by remember { mutableStateOf("") }
    var addLat      by remember { mutableStateOf("") }
    var addType     by remember { mutableStateOf(CoordType.GCJ02) }

    // 批量操作
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var geoResult   by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // 正在编辑的旗标
    var editingId   by remember { mutableStateOf<Long?>(null) }
    var editLabel   by remember { mutableStateOf("") }

    // 过滤分组
    val currentFlags  = flags.filter { it.type == FlagType.CURRENT }
    val pickedFlags   = flags.filter { it.type == FlagType.PICKED }
    val jumpedFlags   = flags.filter { it.type == FlagType.JUMPED }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("旗标管理", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    mapViewModel.clearAllFlags()
                    selectedIds = emptySet()
                }) { Text("清除全部", fontSize = 11.sp) }
            }

            // ---- 手动添加 ----
            if (showAddForm) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = addLabel, onValueChange = { addLabel = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("名称", fontSize = 11.sp) },
                        placeholder = { Text("可选，留空则自动生成", fontSize = 12.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = addLon, onValueChange = { addLon = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            label = { Text("经度", fontSize = 11.sp) },
                            placeholder = { Text("116.397428", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ))
                        OutlinedTextField(value = addLat, onValueChange = { addLat = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            label = { Text("纬度", fontSize = 11.sp) },
                            placeholder = { Text("39.90923", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("坐标类型", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        RadioChip("GCJ02", addType == CoordType.GCJ02) { addType = CoordType.GCJ02 }
                        RadioChip("WGS84", addType == CoordType.WGS84) { addType = CoordType.WGS84 }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            val lonN = addLon.trim().toDoubleOrNull() ?: return@TextButton
                            val latN = addLat.trim().toDoubleOrNull() ?: return@TextButton
                            val typed = CT.Coord(lonN, latN)
                            val gcj = when (addType) {
                                CoordType.GCJ02 -> typed
                                CoordType.WGS84 -> CT.wgs84ToGcj02(typed)
                            }
                            val wgs = CT.gcj02ToWgs84(gcj, precision = CT.HIGH_PRECISION)
                            val name = addLabel.trim()
                            mapViewModel.confirmPlacement(gcj) // 复用现有方法，但手动设置名称
                            addLabel = ""; addLon = ""; addLat = ""
                            showAddForm = false
                        }) { Text("放置", fontSize = 12.sp) }
                        TextButton(onClick = { showAddForm = false }) { Text("取消", fontSize = 11.sp) }
                    }
                }
            } else {
                TextButton(onClick = { showAddForm = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("+ 手动添加", fontSize = 11.sp)
                }
            }

            // ---- 当前定位 ----
            if (currentFlags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("当前位置", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium)
                currentFlags.forEach { flag ->
                    FlagRow(
                        flag = flag,
                        color = Color(0xFF1E88E5),
                        isSelected = selectedIds.contains(flag.id),
                        isEditing = editingId == flag.id,
                        editLabel = editLabel,
                        onToggleSelect = { id ->
                            selectedIds = if (selectedIds.contains(id))
                                selectedIds - id else selectedIds + id
                        },
                        onStartEdit = { f ->
                            editingId = f.id
                            editLabel = f.customName.ifEmpty { f.label }
                        },
                        onFinishEdit = {
                            editingId = null
                            if (editLabel.isNotBlank()) mapViewModel.renameFlag(flag.id, editLabel)
                        },
                        onCancelEdit = { editingId = null },
                        onDelete = {
                            mapViewModel.deleteFlag(flag.id)
                            selectedIds = selectedIds - flag.id
                        },
                        renameFlag = { id, name -> mapViewModel.renameFlag(id, name) },
                        onCopyCoord = {
                            val text = "%.6f,%.6f".format(flag.gcjLon, flag.gcjLat)
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", text))
                            Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
                        },
                        onJump = {
                            mapViewModel.updateLonText("%.6f".format(flag.gcjLon))
                            mapViewModel.updateLatText("%.6f".format(flag.gcjLat))
                            mapViewModel.setCoordType(CoordType.GCJ02)
                        }
                    )
                }
            }

            // ---- 拾取旗标 ----
            if (pickedFlags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("拾取旗标 (${pickedFlags.size})", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                pickedFlags.forEach { flag ->
                    FlagRow(
                        flag = flag,
                        color = Color(0xFFFFC107),
                        isSelected = selectedIds.contains(flag.id),
                        isEditing = editingId == flag.id,
                        editLabel = editLabel,
                        onToggleSelect = { id ->
                            selectedIds = if (selectedIds.contains(id))
                                selectedIds - id else selectedIds + id
                        },
                        onStartEdit = { f ->
                            editingId = f.id
                            editLabel = f.customName.ifEmpty { f.label }
                        },
                        onFinishEdit = {
                            editingId = null
                            if (editLabel.isNotBlank()) mapViewModel.renameFlag(flag.id, editLabel)
                        },
                        onCancelEdit = { editingId = null },
                        onDelete = {
                            mapViewModel.deleteFlag(flag.id)
                            selectedIds = selectedIds - flag.id
                        },
                        renameFlag = { id, name -> mapViewModel.renameFlag(id, name) },
                        onCopyCoord = {
                            val text = "%.6f,%.6f".format(flag.gcjLon, flag.gcjLat)
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", text))
                            Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
                        },
                        onJump = {
                            mapViewModel.updateLonText("%.6f".format(flag.gcjLon))
                            mapViewModel.updateLatText("%.6f".format(flag.gcjLat))
                            mapViewModel.setCoordType(CoordType.GCJ02)
                        }
                    )
                }
            }

            // ---- 跳转目标 ----
            if (jumpedFlags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("跳转目标 (${jumpedFlags.size})", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                jumpedFlags.forEach { flag ->
                    FlagRow(
                        flag = flag,
                        color = Color(0xFFE53935),
                        isSelected = selectedIds.contains(flag.id),
                        isEditing = editingId == flag.id,
                        editLabel = editLabel,
                        onToggleSelect = { id ->
                            selectedIds = if (selectedIds.contains(id))
                                selectedIds - id else selectedIds + id
                        },
                        onStartEdit = { f ->
                            editingId = f.id
                            editLabel = f.customName.ifEmpty { f.label }
                        },
                        onFinishEdit = {
                            editingId = null
                            if (editLabel.isNotBlank()) mapViewModel.renameFlag(flag.id, editLabel)
                        },
                        onCancelEdit = { editingId = null },
                        onDelete = {
                            mapViewModel.deleteFlag(flag.id)
                            selectedIds = selectedIds - flag.id
                        },
                        renameFlag = { id, name -> mapViewModel.renameFlag(id, name) },
                        onCopyCoord = {
                            val text = "%.6f,%.6f".format(flag.gcjLon, flag.gcjLat)
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", text))
                            Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
                        },
                        onJump = {
                            mapViewModel.updateLonText("%.6f".format(flag.gcjLon))
                            mapViewModel.updateLatText("%.6f".format(flag.gcjLat))
                            mapViewModel.setCoordType(CoordType.GCJ02)
                        }
                    )
                }
            }

            // ---- 批量操作按钮 ----
            if (selectedIds.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val selected = flags.filter { selectedIds.contains(it.id) }
                    if (selected.size >= 2) {
                        Button(
                            onClick = {
                                val a = CT.Coord(selected[0].gcjLon, selected[0].gcjLat)
                                val b = CT.Coord(selected[1].gcjLon, selected[1].gcjLat)
                                val dist = a.distanceTo(b)
                                val bearing = a.bearingTo(b)
                                geoResult = Pair(dist, bearing)
                            },
                            enabled = selected.size >= 2,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Filled.Straighten, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("计算距离", fontSize = 12.sp)
                        }
                    }
                    if (selected.size == 2 && geoResult != null) {
                        val (dist, bearing) = geoResult!!
                        val distText = if (dist >= 1000) "%.2f km".format(dist / 1000) else "%.0f m".format(dist)
                        Text("$distText  ·  ${"%.1f°".format(bearing)}",
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { selectedIds = emptySet(); geoResult = null }) {
                        Text("取消选择", fontSize = 11.sp)
                    }
                }
            }

            if (flags.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("暂无旗标，点击地图右上角「拾取」按钮在地图上放置标记",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun FlagRow(
    flag          : Flag,
    color         : Color,
    isSelected    : Boolean,
    isEditing     : Boolean,
    editLabel     : String,
    onToggleSelect: (Long) -> Unit,
    onStartEdit   : (Flag) -> Unit,
    onFinishEdit  : () -> Unit,
    onCancelEdit  : () -> Unit,
    onDelete      : () -> Unit,
    onCopyCoord   : () -> Unit,
    onJump        : () -> Unit,
    renameFlag    : (Long, String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onJump)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 复选框
        Row(modifier = Modifier.clickable(onClick = { onToggleSelect(flag.id) }),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = "选择",
                modifier = Modifier.size(16.dp),
                tint = color,
            )
        }
        Spacer(Modifier.width(6.dp))
        // 色点 + 名称 + 坐标
        Column(modifier = Modifier.weight(1f)) {
            if (isEditing) {
                var editLabelLocal by remember { mutableStateOf(editLabel) }
                OutlinedTextField(
                    value = editLabelLocal, onValueChange = { editLabelLocal = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = color,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    trailingIcon = {
                        Row {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "确认",
                                modifier = Modifier.size(14.dp).clickable {
                                    if (editLabelLocal.isNotBlank()) renameFlag(flag.id, editLabelLocal)
                                    onFinishEdit()
                                },
                                tint = color)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Clear, contentDescription = "取消",
                                modifier = Modifier.size(14.dp).clickable { onCancelEdit() },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = color)
                    Spacer(Modifier.width(4.dp))
                    Text(flag.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (flag.customName.isNotBlank() && flag.label != flag.customName) {
                        Spacer(Modifier.width(4.dp))
                        Text(flag.customName, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("%.6f,%.6f".format(flag.gcjLon, flag.gcjLat),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 复制坐标 + 编辑 + 删除
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isEditing) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "复制坐标",
                    modifier = Modifier.size(16.dp).clickable { onCopyCoord() },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Edit, contentDescription = "重命名",
                    modifier = Modifier.size(16.dp).clickable { onStartEdit(flag) },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.Filled.Delete, contentDescription = "删除",
                modifier = Modifier.size(16.dp).clickable { onDelete() },
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RadioChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 11.sp)
    }
}

// ============================================================================
// 卡片 2：距离 + 方位角计算器
// ============================================================================

@Composable
private fun DistanceBearingCard(mapViewModel: MapViewModel) {
    val pickedCoord by mapViewModel.lastPickedCoord.collectAsState()
    var aLon by remember { mutableStateOf("") }
    var aLat by remember { mutableStateOf("") }
    var bLon by remember { mutableStateOf("") }
    var bLat by remember { mutableStateOf("") }
    var aType by remember { mutableStateOf(CoordType.GCJ02) }
    var bType by remember { mutableStateOf(CoordType.GCJ02) }
    var result by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("距离 + 方位角", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            CoordsInputGroup(label = "A 点", lonText = aLon, latText = aLat, coordType = aType,
                onLonChange = { aLon = it }, onLatChange = { aLat = it },
                onTypeChange = { aType = it },
                onPick = { pickedCoord?.let { c ->
                    aLon = "%.6f".format(c.lon); aLat = "%.6f".format(c.lat); aType = CoordType.GCJ02
                }})

            CoordsInputGroup(label = "B 点", lonText = bLon, latText = bLat, coordType = bType,
                onLonChange = { bLon = it }, onLatChange = { bLat = it },
                onTypeChange = { bType = it },
                onPick = { pickedCoord?.let { c ->
                    bLon = "%.6f".format(c.lon); bLat = "%.6f".format(c.lat); bType = CoordType.GCJ02
                }})

            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                val aLonN = aLon.toDoubleOrNull(); val aLatN = aLat.toDoubleOrNull()
                val bLonN = bLon.toDoubleOrNull(); val bLatN = bLat.toDoubleOrNull()
                if (aLonN == null || aLatN == null || bLonN == null || bLatN == null) return@Button
                val a = when (aType) { CoordType.GCJ02 -> CT.Coord(aLonN, aLatN); CoordType.WGS84 -> CT.wgs84ToGcj02(CT.Coord(aLonN, aLatN)) }
                val b = when (bType) { CoordType.GCJ02 -> CT.Coord(bLonN, bLatN); CoordType.WGS84 -> CT.wgs84ToGcj02(CT.Coord(bLonN, bLatN)) }
                val dist = a.distanceTo(b)
                val bearing = a.bearingTo(b)
                result = Pair(dist, bearing)
            }, enabled = aLon.isNotBlank() && aLat.isNotBlank() && bLon.isNotBlank() && bLat.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("计算", fontSize = 12.sp)
            }

            if (result != null) {
                val (dist, bearing) = result!!
                val reverse = (bearing + 180.0) % 360.0
                val distText = if (dist >= 1000) "%.2f km".format(dist / 1000) else "%.0f m".format(dist)
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("$distText  ·  A→B: ${"%.1f°".format(bearing)} ${bearingCardinal(bearing)}",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                        Text("B→A: ${"%.1f°".format(reverse)} ${bearingCardinal(reverse)}",
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordsInputGroup(
    label     : String,
    lonText   : String,
    latText   : String,
    coordType : CoordType,
    onLonChange : (String) -> Unit,
    onLatChange : (String) -> Unit,
    onTypeChange: (CoordType) -> Unit,
    onPick      : () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        Spacer(Modifier.width(4.dp))
        OutlinedTextField(value = lonText, onValueChange = onLonChange, modifier = Modifier.weight(1f),
            label = { Text("经", fontSize = 10.sp) }, placeholder = { Text("116.397", fontSize = 11.sp) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ))
        Spacer(Modifier.width(4.dp))
        OutlinedTextField(value = latText, onValueChange = onLatChange, modifier = Modifier.weight(1f),
            label = { Text("纬", fontSize = 10.sp) }, placeholder = { Text("39.909", fontSize = 11.sp) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ))
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onPick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Icon(Icons.Filled.Draw, contentDescription = "从地图拾取", modifier = Modifier.size(18.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(32.dp))
        RadioChip("GCJ02", coordType == CoordType.GCJ02) { onTypeChange(CoordType.GCJ02) }
        RadioChip("WGS84", coordType == CoordType.WGS84) { onTypeChange(CoordType.WGS84) }
    }
    Spacer(Modifier.height(4.dp))
}

// ============================================================================
// 卡片 3：折线测距 / 面积测量
// ============================================================================

@Composable
private fun MeasurementCard(mapViewModel: MapViewModel) {
    var mode by remember { mutableStateOf(MapViewModel.MeasurementMode.DISTANCE) }
    val waypoints by mapViewModel.measurementWaypoints.collectAsState()
    val segments by mapViewModel.measurementSegments.collectAsState()
    val totalDist by mapViewModel.measurementTotalDist.collectAsState()
    val totalArea by mapViewModel.measurementTotalArea.collectAsState()
    val isMeasuring by remember(waypoints) { mutableStateOf(waypoints.isNotEmpty()) }

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("测距 / 测面积", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                ModeChip("测距", mode == MapViewModel.MeasurementMode.DISTANCE) { mode = MapViewModel.MeasurementMode.DISTANCE }
                ModeChip("测面积", mode == MapViewModel.MeasurementMode.AREA) { mode = MapViewModel.MeasurementMode.AREA }
            }

            Spacer(Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MeasureBtn(icon = Icons.Filled.PlayArrow, label = if (isMeasuring) "继续" else "开始",
                    enabled = true, onClick = {
                        if (isMeasuring) mapViewModel.stopMeasurement()
                        else mapViewModel.startMeasurement(mode)
                    })
                Spacer(Modifier.width(6.dp))
                MeasureBtn(icon = Icons.Filled.Remove, label = "撤销",
                    enabled = waypoints.size > 1,
                    onClick = { mapViewModel.removeLastWaypoint() })
                Spacer(Modifier.width(6.dp))
                MeasureBtn(icon = Icons.Filled.Clear, label = "清除",
                    enabled = waypoints.isNotEmpty(),
                    onClick = { mapViewModel.clearWaypoints() })
            }

            if (waypoints.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ResultBox(totalDist, if (mode == MapViewModel.MeasurementMode.AREA) totalArea else null,
                    segmentCount = segments.size)

                if (segments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    segments.forEachIndexed { i, seg ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i+1}→${i+2}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text(seg.distText, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (waypoints.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("点击「开始」后到地图标签页依次点击加点，再点击「继续」结束测量",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
    ) { Text(label, fontSize = 11.sp) }
}

@Composable
private fun MeasureBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                       enabled: Boolean, onClick: () -> Unit) {
    if (enabled) {
        Button(onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(onClick = {}, enabled = false,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
    }
}

@Composable
private fun ResultBox(dist: Double, area: Double?, segmentCount: Int) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val distText = if (dist >= 1000) "%.2f km".format(dist / 1000) else "%.0f m".format(dist)
            Text("累计 $distText  ·  $segmentCount 段",
                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            if (area != null) {
                val areaText = when {
                    area >= 1_000_000 -> "%.2f km²".format(area / 1_000_000)
                    area >= 666.67    -> "%.2f 亩".format(area / 666.67)
                    else              -> "%.0f m²".format(area)
                }
                Text("面积 $areaText",
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}
