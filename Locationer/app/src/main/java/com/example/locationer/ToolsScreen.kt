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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel

// ============================================================================
// 持久化折叠状态辅助
// ============================================================================

/**
 * 带 SharedPreferences 持久化的布尔状态。
 * key 对应 prefs 中的键，初始值从 prefs 读取，每次变化立即写入。
 */
@Composable
private fun rememberPersistedBoolean(
    context: android.content.Context,
    key: String,
    default: Boolean = true,
): Pair<Boolean, (Boolean) -> Unit> {
    val prefs = remember(context) {
        context.getSharedPreferences("tool_card_prefs", Context.MODE_PRIVATE)
    }
    var value by remember { mutableStateOf(prefs.getBoolean(key, default)) }
    LaunchedEffect(value) {
        prefs.edit().putBoolean(key, value).apply()
    }
    return value to { newValue -> value = newValue }
}

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
        AngleConversionCard()
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
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    val (isExpanded, onExpandedChange) = rememberPersistedBoolean(context, "card_flag_management", true)
    // 手动添加表单状态
    var showAddForm by remember { mutableStateOf(false) }
    var addLabel    by remember { mutableStateOf("") }
    var addLon      by remember { mutableStateOf("") }
    var addLat      by remember { mutableStateOf("") }
    var addType     by remember { mutableStateOf(CoordType.GCJ02) }

    // 手动添加表单打开时自动展开卡片
    LaunchedEffect(showAddForm) {
        if (showAddForm) onExpandedChange(true)
    }

    // 批量操作
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var geoResult   by remember { mutableStateOf<GeoResult?>(null) }

    // 正在编辑的旗标（弹出重命名对话框）
    var editingFlag by remember { mutableStateOf<Flag?>(null) }
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
        onToggle = { onExpandedChange(!isExpanded) },
        clearBtn = {
            TextButton(onClick = {
                showClearAllDialog = true
            }) { Text("清除全部", fontSize = 14.sp) }
        },
    ) {
        // ---- 手动添加 ----
        if (showAddForm) {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = addLabel, onValueChange = { addLabel = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("名称", fontSize = 14.sp) },
                    placeholder = { Text("可选，留空则自动生成", fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = addLon, onValueChange = { addLon = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        label = { Text("经度", fontSize = 14.sp) },
                        placeholder = { Text("116.397428", fontSize = 14.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ))
                    OutlinedTextField(value = addLat, onValueChange = { addLat = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        label = { Text("纬度", fontSize = 14.sp) },
                        placeholder = { Text("39.90923", fontSize = 14.sp) },
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
                    FText("坐标类型", 14, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        mapViewModel.vibratePick()
                        addLabel = ""; addLon = ""; addLat = ""
                        showAddForm = false
                    }) { Text("放置", fontSize = 14.sp) }
                    TextButton(onClick = { showAddForm = false }) { Text("取消", fontSize = 14.sp) }
                }
            }
        } else {
            TextButton(onClick = { showAddForm = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("+ 手动添加", fontSize = 14.sp)
            }
        }

        // ---- 拾取旗标 ----
        if (pickedFlags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("拾取旗标 (${pickedFlags.size})", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    showClearAllDialog = true
                }) { Text("清除全部", fontSize = 14.sp) }
            }
            pickedFlags.forEach { flag ->
                FlagRow(
                    flag = flag,
                    color = Color(flagStyle.flagIconColor),
                    isSelected = selectedIds.contains(flag.id),
                    onToggleSelect = { id ->
                        selectedIds = if (selectedIds.contains(id))
                            selectedIds - id else selectedIds + id
                    },
                    onStartEdit = { editingFlag = it },
                    onFinishEdit = { editingFlag = null },
                    onCancelEdit = { editingFlag = null },
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
                    onToggleExpanded = { mapViewModel.updateExpanded(flag.id, it) },
                    flagStyle = flagStyle,
                )
            }
        }

        // ---- 跳转目标 ----
        if (jumpedFlags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("跳转目标 (${jumpedFlags.size})", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    showClearAllDialog = true
                }) { Text("清除全部", fontSize = 14.sp) }
            }
            jumpedFlags.forEach { flag ->
                FlagRow(
                    flag = flag,
                    color = Color(flagStyle.flagIconColor),
                    isSelected = selectedIds.contains(flag.id),
                    onToggleSelect = { id ->
                        selectedIds = if (selectedIds.contains(id))
                            selectedIds - id else selectedIds + id
                    },
                    onStartEdit = { editingFlag = it },
                    onFinishEdit = { editingFlag = null },
                    onCancelEdit = { editingFlag = null },
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
                    onToggleExpanded = { mapViewModel.updateExpanded(flag.id, it) },
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
                        Text("计算距离", fontSize = 14.sp)
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
                                fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                            Text("${r.to}→${r.from}: ${"%.1f°".format(reverse)} ${bearingCardinal(reverse)}",
                                fontSize = 14.sp, fontFamily = FontFamily.Monospace,
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
                    Text("删除 (${selectedIds.size})", fontSize = 14.sp)
                }
                TextButton(onClick = { selectedIds = emptySet(); geoResult = null }) {
                    Text("取消选择", fontSize = 14.sp)
                }
            }
        }

        if (flags.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("暂无旗标，点击地图右上角「拾取」按钮在地图上放置标记",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }

    // ── 重命名旗标（与收藏夹一致的 AlertDialog）──
    editingFlag?.let { flag ->
        var label by remember(flag.id) { mutableStateOf(flag.customName.ifEmpty { flag.label }) }
        AlertDialog(
            onDismissRequest = { editingFlag = null },
            title = { Text("重命名旗标") },
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
                        val trimmed = label.trim()
                        if (trimmed.isNotBlank()) mapViewModel.renameFlag(flag.id, trimmed)
                        editingFlag = null
                    },
                    enabled = label.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingFlag = null }) { Text("取消") }
            },
        )
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
    flag               : Flag,
    color              : Color,
    isSelected         : Boolean,
    onToggleSelect     : (Long) -> Unit,
    onFavorite         : () -> Unit,
    onStartEdit        : (Flag) -> Unit,
    onFinishEdit       : () -> Unit,
    onCancelEdit       : () -> Unit,
    onDelete           : () -> Unit,
    onCopyGcj          : () -> Unit,
    onCopyWgs          : () -> Unit,
    onJump             : () -> Unit,
    onToggleExpanded   : (Boolean) -> Unit,
    flagStyle          : FlagStyle = FlagStyle(),
) {
    val displayName = if (flag.customName.isNotBlank()) flag.customName else flag.label
    var expanded by remember { mutableStateOf(flag.isExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            // 名称行：眼睛选择 + 图标+名称 + 展开/收起按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onJump,
                        onLongClick = onFavorite,
                    )
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 眼睛选择（收藏夹没有，此处新增）
                Row(
                    modifier = Modifier.clickable(onClick = { onToggleSelect(flag.id) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "选择",
                        modifier = Modifier.size(16.dp),
                        tint = color,
                    )
                }
                Spacer(Modifier.width(8.dp))
                // 图标 + 名称（字号与收藏夹一致，不显式指定 fontSize）
                Icon(Icons.Filled.Person, contentDescription = null,
                    modifier = Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = displayName,
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
                    FlagCoordRow("GCJ02", flag.gcjLon, flag.gcjLat, onCopyGcj)
                    FlagCoordRow("WGS84", flag.wgsLon, flag.wgsLat, onCopyWgs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = { onStartEdit(flag) }) {
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
private fun FlagCoordRow(label: String, lon: Double, lat: Double, onCopy: () -> Unit) {
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

// ============================================================================
// 卡片 2：折线测距 / 面积测量
// ============================================================================

@Composable
private fun MeasurementCard(
    mapViewModel: MapViewModel,
    isToolsActive: Boolean,
    bringIntoViewRequester: BringIntoViewRequester,
) {
    val cardContext = androidx.compose.ui.platform.LocalContext.current
    val (isExpanded, onExpandedChange) = rememberPersistedBoolean(cardContext, "card_measurement", true)
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
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveLabel by remember { mutableStateOf(TextFieldValue()) }
    val saveLabelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showSaveDialog) {
        if (showSaveDialog) saveLabelFocusRequester.requestFocus()
    }
    // 重新开始测量时清除"已手动收起"标记，恢复自动展开行为
    LaunchedEffect(isMeasuring) { if (isMeasuring) userCollapsed = false }

    LaunchedEffect(isToolsActive, isComplete, isExpanded, userCollapsed) {
        if (isToolsActive && isComplete && !isExpanded && !userCollapsed) {
            onExpandedChange(true)
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
                onExpandedChange(!isExpanded)
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
                enabled = true,
                onClick = {
                    if (isMeasuring) mapViewModel.stopMeasurement()
                    else mapViewModel.clearWaypoints()
                })
        }

        // 测量中提示
        if (isMeasuring) {
            Spacer(Modifier.height(4.dp))
            Text("正在地图上放置测点，达到最低点数后点击「开始计算」",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }

        if (waypoints.isNotEmpty() && isComplete) {
            Spacer(Modifier.height(6.dp))
            ResultBox(totalDist, if (vmMode == MapViewModel.MeasurementMode.AREA) totalArea else null,
                segmentCount = segments.size)

            if (segments.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                segments.forEachIndexed { i, seg ->
                    val from = i + 1
                    val to = if (i == segments.size - 1 && waypoints.size >= 3 && vmMode == MapViewModel.MeasurementMode.AREA) 1 else i + 2
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$from→$to", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(seg.distText, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 各点位坐标（GCJ02 + WGS84 并列显示）
            if (waypoints.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                waypoints.forEachIndexed { i, wp ->
                    Text("点 ${i + 1}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(1.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("GCJ02", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.width(2.dp))
                        Text("%.6f,%.6f".format(wp.gcj.lon, wp.gcj.lat),
                            fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("WGS84", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.width(2.dp))
                        Text("%.6f,%.6f".format(wp.wgs.lon, wp.wgs.lat),
                            fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            // 保存到历史按钮
            Button(
                onClick = {
                    val defaultLabel = formatMeasurementTimestamp()
                    saveLabel = TextFieldValue(
                        text = defaultLabel,
                        selection = TextRange(0, defaultLabel.length),
                    )
                    showSaveDialog = true
                },
                enabled = waypoints.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存到历史", fontSize = 14.sp)
            }
        }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("命名测量记录") },
            text = {
                OutlinedTextField(
                    value = saveLabel,
                    onValueChange = { saveLabel = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(saveLabelFocusRequester),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val saved = mapViewModel.saveMeasurementToHistory(saveLabel.text.trim())
                        showSaveDialog = false
                        Toast.makeText(
                            cardContext,
                            if (saved) "已保存到历史测量" else "该测量已存在于历史记录中",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    enabled = saveLabel.text.trim().isNotEmpty(),
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            },
        )
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
    ) { Text(label, fontSize = 14.sp) }
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
            Text(label, fontSize = 14.sp)
        }
    } else {
        OutlinedButton(onClick = {}, enabled = false,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
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
                fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            if (area != null) {
                val areaText = MapViewModel.formatArea(area)
                Text("面积 $areaText",
                    fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

// ============================================================================
// 卡片 3：角度换算（度 ↔ 时分秒）
// ============================================================================

@Composable
private fun AngleConversionCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (isExpanded, onExpandedChange) = rememberPersistedBoolean(context, "card_angle_conversion", true)
    var userCollapsed by remember { mutableStateOf(false) }

    // 十进制度数输入
    var decimalInput by remember { mutableStateOf("") }
    var decimalError by remember { mutableStateOf<String?>(null) }

    // DMS 输入（分、秒分开）
    var dmsDeg by remember { mutableStateOf("") }
    var dmsMin by remember { mutableStateOf("") }
    var dmsSec by remember { mutableStateOf("") }
    var dmsError by remember { mutableStateOf<String?>(null) }

    // 实时显示转换结果
    var decimalResult by remember { mutableStateOf<String?>(null) }
    var dmsResult by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.bringIntoViewRequester(BringIntoViewRequester())) {
        CollapsibleToolCard(
            title = "角度换算",
            icon = Icons.Filled.FormatQuote,
            iconTint = MaterialTheme.colorScheme.tertiary,
            isExpanded = isExpanded,
            onToggle = {
                userCollapsed = true
                onExpandedChange(!isExpanded)
            },
        ) {
            // ---- 十进制 → DMS ----
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("十进制度数 → 时分秒", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = decimalInput,
                    onValueChange = { input ->
                        decimalInput = input
                        decimalResult = input.toDoubleOrNull()?.let { dec ->
                            dec.toDmsString()
                        }
                        decimalError = if (input.isNotEmpty() && decimalResult == null) "请输入有效的数字" else null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("度数（如 116.397428）", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = decimalError != null,
                    supportingText = decimalError?.let { { Text(it, fontSize = 11.sp) } },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        errorIndicatorColor = MaterialTheme.colorScheme.error,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        errorContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    ),
                )
                decimalResult?.let { result ->
                    ResultDisplayRow(label = "时分秒结果", text = result, onCopy = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("角度", result))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    })
                }
            }

            // 分隔线
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {}
            Spacer(Modifier.height(8.dp))

            // ---- DMS → 十进制 ----
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("时分秒 → 十进制度数", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = dmsDeg,
                        onValueChange = { input ->
                            dmsDeg = input
                            val deg = input.toDoubleOrNull()
                            val min = dmsMin.toDoubleOrNull() ?: 0.0
                            val sec = dmsSec.toDoubleOrNull() ?: 0.0
                            if (deg != null) {
                                dmsResult = "%.6f".format(deg + min / 60.0 + sec / 3600.0)
                                dmsError = null
                            } else {
                                dmsResult = null
                                dmsError = if (input.isNotEmpty()) "请输入有效的度数" else null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("度", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = dmsError != null,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                            errorIndicatorColor = MaterialTheme.colorScheme.error,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            errorContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        ),
                    )
                    OutlinedTextField(
                        value = dmsMin,
                        onValueChange = { input ->
                            dmsMin = input
                            val deg = dmsDeg.toDoubleOrNull()
                            val min = input.toDoubleOrNull() ?: 0.0
                            val sec = dmsSec.toDoubleOrNull() ?: 0.0
                            if (deg != null) {
                                dmsResult = "%.6f".format(deg + min / 60.0 + sec / 3600.0)
                                dmsError = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("分", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    OutlinedTextField(
                        value = dmsSec,
                        onValueChange = { input ->
                            dmsSec = input
                            val deg = dmsDeg.toDoubleOrNull()
                            val min = dmsMin.toDoubleOrNull() ?: 0.0
                            val sec = input.toDoubleOrNull() ?: 0.0
                            if (deg != null) {
                                dmsResult = "%.6f".format(deg + min / 60.0 + sec / 3600.0)
                                dmsError = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("秒", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
                dmsResult?.let { result ->
                    ResultDisplayRow(label = "十进制结果", text = result, onCopy = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("角度", result))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    })
                }
                dmsError?.let { error ->
                    Text(error, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ResultDisplayRow(label: String, text: String, onCopy: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(18.dp).clickable(onClick = onCopy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
