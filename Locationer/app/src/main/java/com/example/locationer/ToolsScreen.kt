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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
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
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 工具箱标签页：坐标收藏 / 距离方位角计算 / 折线测距与面积测量
 */
@Composable
fun ToolsScreen(
    mapViewModel: MapViewModel = viewModel(),
    favViewModel: FavoritesViewModel = viewModel(),
) {
    val favorites by favViewModel.favorites.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FavoriteCard(favorites, favViewModel, mapViewModel)
        DistanceBearingCard(mapViewModel)
        MeasurementCard(mapViewModel)
    }
}

// ============================================================================
// 卡片 1：坐标收藏
// ============================================================================

@Composable
private fun FavoriteCard(
    favorites  : List<FavoritesViewModel.FavoritePoint>,
    favViewModel: FavoritesViewModel,
    mapViewModel: MapViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CoordType.GCJ02) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("坐标收藏", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "添加", fontSize = 12.sp)
                }
            }

            if (expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("名称", fontSize = 11.sp) },
                        placeholder = { Text("家 / 公司", fontSize = 12.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = lon, onValueChange = { lon = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            label = { Text("经度", fontSize = 11.sp) },
                            placeholder = { Text("116.397428", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                        OutlinedTextField(value = lat, onValueChange = { lat = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            label = { Text("纬度", fontSize = 11.sp) },
                            placeholder = { Text("39.90923", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("坐标类型", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        RadioChip("GCJ02", type == CoordType.GCJ02) { type = CoordType.GCJ02 }
                        RadioChip("WGS84", type == CoordType.WGS84) { type = CoordType.WGS84 }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            val lonN = lon.trim().toDoubleOrNull() ?: return@TextButton
                            val latN = lat.trim().toDoubleOrNull() ?: return@TextButton
                            val typed = CT.Coord(lonN, latN)
                            val gcj = when (type) {
                                CoordType.GCJ02 -> typed
                                CoordType.WGS84 -> CT.wgs84ToGcj02(typed)
                            }
                            val wgs = CT.gcj02ToWgs84(gcj, precision = CT.HIGH_PRECISION)
                            favViewModel.add(label.trim() ?: "未命名", gcj, wgs, type)
                            label = ""; lon = ""; lat = ""
                        }) { Text("保存", fontSize = 12.sp) }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                favorites.forEach { p ->
                    FavoriteRow(point = p, onClick = { favViewModel.jumpTo(p, mapViewModel) },
                        onDelete = { favViewModel.remove(p.id) })
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    point   : FavoritesViewModel.FavoritePoint,
    onClick : () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Person, contentDescription = null,
            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(point.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("(${point.gcjLon.toString().padStart(9, '.')}${point.gcjLat.toString().padStart(8, '.')})",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onDelete,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp),
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
                onPick = { mapViewModel.lastPickedCoord.value?.let { c ->
                    aLon = "%.6f".format(c.lon); aLat = "%.6f".format(c.lat); aType = CoordType.GCJ02
                }})

            CoordsInputGroup(label = "B 点", lonText = bLon, latText = bLat, coordType = bType,
                onLonChange = { bLon = it }, onLatChange = { bLat = it },
                onTypeChange = { bType = it },
                onPick = { mapViewModel.lastPickedCoord.value?.let { c ->
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
                val reverse = b.bearingTo(a)
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
            // 标题 + 模式切换
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("测距 / 测面积", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                ModeChip("测距", mode == MapViewModel.MeasurementMode.DISTANCE) { mode = MapViewModel.MeasurementMode.DISTANCE }
                ModeChip("测面积", mode == MapViewModel.MeasurementMode.AREA) { mode = MapViewModel.MeasurementMode.AREA }
            }

            Spacer(Modifier.height(6.dp))

            // 控制按钮
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MeasureBtn(icon = Icons.Filled.PlayArrow, label = if (isMeasuring) "继续" else "开始",
                    enabled = true, onClick = {
                        if (isMeasuring) mapViewModel.stopMeasurement()
                        else {
                            mapViewModel.startMeasurement(mode)
                        }
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

            // 结果
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
