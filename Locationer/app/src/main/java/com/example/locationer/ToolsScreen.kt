package com.example.locationer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
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

/** 距离计算结果 */
data class GeoResult(
    val from: String, val to: String,
    val dist: Double, val bearing: Double,
)

/**
 * 工具标签页：旗标管理 / 折线测距与面积测量
 */
@Composable
fun ToolsScreen(
    mapViewModel: MapViewModel = viewModel(),
    favoritesViewModel: FavoritesViewModel = viewModel(),
    isActive: Boolean = true,
    flagStyle: FlagStyle = FlagStyle(),
) {
    val flags by mapViewModel.flags.collectAsState()
    val gcj     by mapViewModel.currentGcj.collectAsState()
    val wgs     by mapViewModel.currentWgs.collectAsState()
    val statusBarTop  = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom  = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val scrollState = rememberScrollState()
    val measurementRequester = remember { BringIntoViewRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 8.dp, top = statusBarTop + 4.dp, end = 8.dp, bottom = navBarBottom + 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlagManagementCard(
            context = androidx.compose.ui.platform.LocalContext.current,
            flags = flags,
            mapViewModel = mapViewModel,
            favoritesViewModel = favoritesViewModel,
            currentGcj = gcj,
            currentWgs = wgs,
            flagStyle = flagStyle,
        )
        MeasurementCard(
            mapViewModel = mapViewModel,
            isToolsActive = isActive,
            bringIntoViewRequester = measurementRequester,
        )
    }
}

// ============================================================================
// 折叠卡片包装器
// ============================================================================

@Composable
private fun CollapsibleToolCard(
    title     : String,
    icon      : androidx.compose.ui.graphics.vector.ImageVector,
    iconTint  : androidx.compose.ui.graphics.Color,
    isExpanded: Boolean,
    onToggle  : () -> Unit,
    clearBtn  : (@Composable () -> Unit)? = null,
    content   : @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null,
    ) {
        Column {
            // 标题栏：点击展开/收起
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = iconTint)
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (clearBtn != null) clearBtn()
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + scaleIn(initialScale = 0.97f),
                exit = fadeOut() + scaleOut(targetScale = 0.97f),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    content()
                }
            }
        }
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
    favoritesViewModel: FavoritesViewModel,
    currentGcj  : CT.Coord?,
    currentWgs  : CT.Coord?,
    flagStyle   : FlagStyle = FlagStyle(),
) {
    var isExpanded by remember { mutableStateOf(true) }
    // 手动添加表单状态
    var showAddForm by remember { mutableStateOf(false) }
    var addLabel    by remember { mutableStateOf("") }
    var addLon      by remember { mutableStateOf("") }
    var addLat      by remember { mutableStateOf("") }
    var addType     by remember { mutableStateOf(CoordType.GCJ02) }

    // 手动添加表单打开时自动展开卡片
    LaunchedEffect(showAddForm) {
        if (showAddForm) isExpanded = true
    }

    // 批量操作
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var geoResult   by remember { mutableStateOf<GeoResult?>(null) }

    // 正在编辑的旗标
    var editingId   by remember { mutableStateOf<Long?>(null) }
    var editLabel   by remember { mutableStateOf("") }
    // 删除确认弹框
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetLabel by remember { mutableStateOf("") }
    var deleteFlagId by remember { mutableStateOf<Long?>(null) }
    // 清除全部确认弹框
    var showClearAllDialog by remember { mutableStateOf(false) }

    // 过滤分组
    val pickedFlags   = flags.filter { it.type == FlagType.PICKED }
    val jumpedFlags   = flags.filter { it.type == FlagType.JUMPED }

    CollapsibleToolCard(
        title = "旗标管理",
        icon = Icons.Filled.Person,
        iconTint = MaterialTheme.colorScheme.primary,
        isExpanded = isExpanded,
        onToggle = { isExpanded = !isExpanded },
        clearBtn = {
            TextButton(onClick = {
                showClearAllDialog = true
            }) { Text("清除全部", fontSize = 11.sp) }
        },
    ) {
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
                // 坐标类型选择（GCJ02 / WGS84）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText("坐标类型", 10, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    CoordRadio("GCJ02", addType == CoordType.GCJ02) { addType = CoordType.GCJ02 }
                    CoordRadio("WGS84", addType == CoordType.WGS84) { addType = CoordType.WGS84 }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        mapViewModel.addFlag(gcj, wgs, FlagType.PICKED, addLabel.trim())
                        addLabel = ""; addLon = ""; addLat = ""
                        showAddForm = false
                    }) { Text("放置", fontSize = 11.sp) }
                    TextButton(onClick = { showAddForm = false }) { Text("取消", fontSize = 10.sp) }
                }
            }
        } else {
            TextButton(onClick = { showAddForm = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("+ 手动添加", fontSize = 11.sp)
            }
        }

        // ---- 拾取旗标 ----
        if (pickedFlags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("拾取旗标 (${pickedFlags.size})", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    showClearAllDialog = true
                }) { Text("清除全部", fontSize = 10.sp) }
            }
            pickedFlags.forEach { flag ->
                val initLabel = flag.customName.ifEmpty { flag.label }
                FlagRow(
                    flag = flag,
                    color = Color(flagStyle.flagIconColor),
                    isSelected = selectedIds.contains(flag.id),
                    isEditing = editingId == flag.id,
                    editLabel = editLabel,
                    initialEditLabel = initLabel,
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
                    },
                    onCancelEdit = { editingId = null },
                    onDelete = {
                        deleteFlagId = flag.id
                        deleteTargetLabel = flag.customName.ifEmpty { flag.label }
                        showDeleteDialog = true
                    },
                    onFavorite = {
                        val added = favoritesViewModel.addFromFlag(flag)
                        Toast.makeText(
                            context,
                            if (added) "已添加到收藏夹" else "该旗标已在收藏夹中",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    renameFlag = { id, name -> mapViewModel.renameFlag(id, name) },
                    onCopyGcj = {
                        val t = "%.6f,%.6f".format(flag.gcjLon, flag.gcjLat)
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", t))
                        Toast.makeText(context, "已复制GCJ02：$t", Toast.LENGTH_SHORT).show()
                    },
                    onCopyWgs = {
                        val t = "%.6f,%.6f".format(flag.wgsLon, flag.wgsLat)
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", t))
                        Toast.makeText(context, "已复制WGS84：$t", Toast.LENGTH_SHORT).show()
                    },
                    onJump = {
                        mapViewModel.updateLonText("%.6f".format(flag.gcjLon))
                        mapViewModel.updateLatText("%.6f".format(flag.gcjLat))
                        mapViewModel.setCoordType(CoordType.GCJ02)
                    },
                    flagStyle = flagStyle,
                )
            }
        }

        // ---- 跳转目标 ----
        if (jumpedFlags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("跳转目标 (${jumpedFlags.size})", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    showClearAllDialog = true
                }) { Text("清除全部", fontSize = 10.sp) }
            }
            jumpedFlags.forEach { flag ->
                val initLabel = flag.customName.ifEmpty { flag.label }
                FlagRow(
                    flag = flag,
                    color = Color(flagStyle.flagIconColor),
                    isSelected = selectedIds.contains(flag.id),
                    isEditing = editingId == flag.id,
                    editLabel = editLabel,
                    initialEditLabel = initLabel,
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
                    },
                    onCancelEdit = { editingId = null },
                    onDelete = {
                        deleteFlagId = flag.id
                        deleteTargetLabel = flag.customName.ifEmpty { flag.label }
                        showDeleteDialog = true
                    },
                    onFavorite = {
                        val added = favoritesViewModel.addFromFlag(flag)
                        Toast.makeText(
                            context,
                            if (added) "已添加到收藏夹" else "该旗标已在收藏夹中",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    renameFlag = { id, name -> mapViewModel.renameFlag(id, name) },
                    onCopyGcj = {
                        val t = "%.6f,%.6f".format(flag.gcjLon, flag.gcjLat)
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", t))
                        Toast.makeText(context, "已复制GCJ02：$t", Toast.LENGTH_SHORT).show()
                    },
                    onCopyWgs = {
                        val t = "%.6f,%.6f".format(flag.wgsLon, flag.wgsLat)
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", t))
                        Toast.makeText(context, "已复制WGS84：$t", Toast.LENGTH_SHORT).show()
                    },
                    onJump = {
                        mapViewModel.updateLonText("%.6f".format(flag.gcjLon))
                        mapViewModel.updateLatText("%.6f".format(flag.gcjLat))
                        mapViewModel.setCoordType(CoordType.GCJ02)
                    },
                    flagStyle = flagStyle,
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
                            val flagA = selected[0]
                            val flagB = selected[1]
                            val a = CT.Coord(flagA.gcjLon, flagA.gcjLat)
                            val b = CT.Coord(flagB.gcjLon, flagB.gcjLat)
                            val dist = a.distanceTo(b)
                            val bearing = a.bearingTo(b)
                            val nameA = flagA.customName.ifEmpty { flagA.label }
                            val nameB = flagB.customName.ifEmpty { flagB.label }
                            geoResult = GeoResult(nameA, nameB, dist, bearing)
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
                    val r = geoResult!!
                    val reverse = (r.bearing + 180.0) % 360.0
                    val distText = MapViewModel.formatDist(r.dist)
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("$distText  ·  ${r.from}→${r.to}: ${"%.1f°".format(r.bearing)} ${bearingCardinal(r.bearing)}",
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                            Text("${r.to}→${r.from}: ${"%.1f°".format(reverse)} ${bearingCardinal(reverse)}",
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
                Button(
                    onClick = {
                        val idsToDelete = selectedIds
                        selectedIds = emptySet()
                        geoResult = null
                        idsToDelete.forEach { mapViewModel.deleteFlag(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除 (${selectedIds.size})", fontSize = 12.sp)
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

    // ── 删除单个旗标确认 ──
    if (showDeleteDialog) {
        AlertDialog(
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认删除旗标") },
            text = { Text("确定要删除旗标「$deleteTargetLabel」吗？此操作不可撤销。") },
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    mapViewModel.deleteFlag(deleteFlagId!!)
                    selectedIds = selectedIds - deleteFlagId!!
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
    // ── 清除全部旗标确认 ──
    if (showClearAllDialog) {
        AlertDialog(
            icon = { Icon(Icons.Filled.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认清除全部旗标") },
            text = { Text("确定要清除所有旗标吗？此操作不可撤销。") },
            onDismissRequest = { showClearAllDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllDialog = false
                    mapViewModel.clearAllFlags()
                    selectedIds = emptySet()
                }) { Text("清除全部", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlagRow(
    flag           : Flag,
    color          : Color,
    isSelected     : Boolean,
    isEditing      : Boolean,
    editLabel      : String,
    initialEditLabel : String,  // editing开始时传入的初始值，绕过remember缓存问题
    onToggleSelect : (Long) -> Unit,
    onFavorite     : () -> Unit,
    onStartEdit    : (Flag) -> Unit,
    onFinishEdit   : () -> Unit,
    onCancelEdit   : () -> Unit,
    onDelete       : () -> Unit,
    onCopyGcj      : () -> Unit,
    onCopyWgs      : () -> Unit,
    onJump         : () -> Unit,
    renameFlag     : (Long, String) -> Unit,
    flagStyle      : FlagStyle = FlagStyle(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onJump,
                onLongClick = onFavorite,
            )
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
                var editLabelLocal by remember(flag.id, initialEditLabel) { mutableStateOf(initialEditLabel) }
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
                            Icon(Icons.Filled.Check, contentDescription = "确认",
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
                    // 有自定义名时主文本显示自定义名，否则显示默认编号
                    Text(
                        text = if (flag.customName.isNotBlank()) flag.customName else flag.label,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
                // GCJ02 坐标行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GCJ02", fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.width(2.dp))
                    Text("%.6f,%.6f".format(flag.gcjLon, flag.gcjLat),
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制GCJ02",
                        modifier = Modifier.size(16.dp).clickable { onCopyGcj() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Edit, contentDescription = "重命名",
                        modifier = Modifier.size(16.dp).clickable { onStartEdit(flag) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // WGS84 坐标行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("WGS84", fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.width(2.dp))
                    Text("%.6f,%.6f".format(flag.wgsLon, flag.wgsLat),
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制WGS84",
                        modifier = Modifier.size(16.dp).clickable { onCopyWgs() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Delete, contentDescription = "删除",
                        modifier = Modifier.size(16.dp).clickable { onDelete() },
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ============================================================================
// 卡片 2：折线测距 / 面积测量
// ============================================================================

@Composable
private fun MeasurementCard(
    mapViewModel: MapViewModel,
    isToolsActive: Boolean,
    bringIntoViewRequester: BringIntoViewRequester,
) {
    var isExpanded by remember { mutableStateOf(true) }
    var userCollapsed by remember { mutableStateOf(false) }
    val vmMode by mapViewModel.measurementMode.collectAsState()
    var uiMode by remember { mutableStateOf(vmMode) }
    LaunchedEffect(vmMode) { uiMode = vmMode }
    val waypoints by mapViewModel.measurementWaypoints.collectAsState()
    val segments by mapViewModel.measurementSegments.collectAsState()
    val totalDist by mapViewModel.measurementTotalDist.collectAsState()
    val totalArea by mapViewModel.measurementTotalArea.collectAsState()
    val measurementState by mapViewModel.measurementState.collectAsState()
    val isMeasuring = measurementState == MapViewModel.MeasurementState.PLACING
    val isComplete = measurementState == MapViewModel.MeasurementState.COMPLETED
    // 重新开始测量时清除"已手动收起"标记，恢复自动展开行为
    LaunchedEffect(isMeasuring) { if (isMeasuring) userCollapsed = false }

    LaunchedEffect(isToolsActive, isComplete, isExpanded, userCollapsed) {
        if (isToolsActive && isComplete && !isExpanded && !userCollapsed) {
            isExpanded = true
        } else if (isToolsActive && isComplete) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        CollapsibleToolCard(
            title = "测距 / 测面积",
            icon = Icons.Filled.Draw,
            iconTint = MaterialTheme.colorScheme.secondary,
            isExpanded = isExpanded,
            onToggle = {
                userCollapsed = true
                isExpanded = !isExpanded
            },
        ) {
        // 模式选择
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            ModeChip("测距", uiMode == MapViewModel.MeasurementMode.DISTANCE, enabled = !isMeasuring) {
                uiMode = MapViewModel.MeasurementMode.DISTANCE
            }
            ModeChip("测面积", uiMode == MapViewModel.MeasurementMode.AREA, enabled = !isMeasuring) {
                uiMode = MapViewModel.MeasurementMode.AREA
            }
        }

        Spacer(Modifier.height(6.dp))

        // 操作按钮
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MeasureBtn(icon = Icons.Filled.PlayArrow, label = "开始",
                enabled = !isMeasuring,
                onClick = {
                    mapViewModel.startMeasurement(uiMode)
                    mapViewModel.triggerCollapsePanel()
                    mapViewModel.requestSwitchToMap()
                })
            Spacer(Modifier.width(6.dp))
            MeasureBtn(icon = Icons.Filled.Remove, label = "撤销",
                enabled = waypoints.isNotEmpty() && isMeasuring,
                onClick = { mapViewModel.removeLastWaypoint() })
            Spacer(Modifier.width(6.dp))
            MeasureBtn(icon = Icons.Filled.Clear, label = "清除",
                enabled = waypoints.isNotEmpty(),
                onClick = {
                    if (isMeasuring) mapViewModel.clearWaypoints()
                    else mapViewModel.stopMeasurement()
                })
        }

        // 测量中提示
        if (isMeasuring) {
            Spacer(Modifier.height(4.dp))
            Text("正在地图上放置测点，达到最低点数后点击「开始计算」",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }

        if (waypoints.isNotEmpty() && isComplete) {
            Spacer(Modifier.height(6.dp))
            ResultBox(totalDist, if (vmMode == MapViewModel.MeasurementMode.AREA) totalArea else null,
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

            // 各点位坐标（GCJ02 + WGS84 并列显示）
            if (waypoints.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                waypoints.forEachIndexed { i, wp ->
                    Text("点 ${i + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(1.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("GCJ02", fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.width(2.dp))
                        Text("%.6f,%.6f".format(wp.gcj.lon, wp.gcj.lat),
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("WGS84", fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.width(2.dp))
                        Text("%.6f,%.6f".format(wp.wgs.lon, wp.wgs.lat),
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
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
            val distText = MapViewModel.formatDist(dist)
            Text("累计 $distText  ·  $segmentCount 段",
                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            if (area != null) {
                val areaText = MapViewModel.formatArea(area)
                Text("面积 $areaText",
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}
