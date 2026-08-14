package com.example.locationer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    currentStyle: FlagStyle,
    onStyleChange: (FlagStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsGroup(title = "主题模式", icon = Icons.Filled.Tune) {
            ThemeModeEntry(
                label = "跟随系统",
                selected = currentStyle.themeMode == ThemeMode.FOLLOW_SYSTEM,
                onClick = { onStyleChange(currentStyle.copy(themeMode = ThemeMode.FOLLOW_SYSTEM)) },
            )
            ThemeModeEntry(
                label = "浅色模式",
                selected = currentStyle.themeMode == ThemeMode.LIGHT,
                onClick = { onStyleChange(currentStyle.copy(themeMode = ThemeMode.LIGHT)) },
            )
            ThemeModeEntry(
                label = "深色模式",
                selected = currentStyle.themeMode == ThemeMode.DARK,
                onClick = { onStyleChange(currentStyle.copy(themeMode = ThemeMode.DARK)) },
            )
        }

        SettingsGroup(title = "旗标图标", icon = Icons.Filled.Palette) {
            ColorSelectorRow(
                label = "图标颜色",
                currentValue = currentStyle.flagIconColor,
                presets = FLAG_COLOR_PRESETS,
                onSelect = { onStyleChange(currentStyle.copy(flagIconColor = it.value)) },
            )
            ValueSliderRow(
                label = "图标大小",
                value = currentStyle.flagIconSize,
                valueRange = 16f..48f,
                valueText = "%.0f px".format(currentStyle.flagIconSize),
                onValueChange = { onStyleChange(currentStyle.copy(flagIconSize = it)) },
            )
        }

        SettingsGroup(title = "旗标文字", icon = Icons.Filled.TextFields) {
            ColorSelectorRow(
                label = "文字颜色",
                currentValue = currentStyle.flagTextColor,
                presets = TEXT_COLOR_PRESETS,
                onSelect = { onStyleChange(currentStyle.copy(flagTextColor = it.value)) },
            )
            ValueSliderRow(
                label = "文字大小",
                value = currentStyle.flagTextSize,
                valueRange = 12f..48f,
                valueText = "%.0f px".format(currentStyle.flagTextSize),
                onValueChange = { onStyleChange(currentStyle.copy(flagTextSize = it)) },
            )
        }

        SettingsGroup(title = "全局字体", icon = Icons.Filled.FormatSize) {
            ValueSliderRow(
                label = "字号缩放",
                value = currentStyle.globalTextScale,
                valueRange = 0.8f..1.5f,
                valueText = "%.1fx".format(currentStyle.globalTextScale),
                onValueChange = { onStyleChange(currentStyle.copy(globalTextScale = it)) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "字体样式",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                FontFamilyButton(
                    label = "默认",
                    family = FontFamily.Default,
                    selected = currentStyle.globalFontFamily == FontFamilyOption.DEFAULT,
                    onClick = { onStyleChange(currentStyle.copy(globalFontFamily = FontFamilyOption.DEFAULT)) },
                )
                FontFamilyButton(
                    label = "衬线",
                    family = FontFamily.Serif,
                    selected = currentStyle.globalFontFamily == FontFamilyOption.SERIF,
                    onClick = { onStyleChange(currentStyle.copy(globalFontFamily = FontFamilyOption.SERIF)) },
                )
                FontFamilyButton(
                    label = "等宽",
                    family = FontFamily.Monospace,
                    selected = currentStyle.globalFontFamily == FontFamilyOption.MONOSPACE,
                    onClick = { onStyleChange(currentStyle.copy(globalFontFamily = FontFamilyOption.MONOSPACE)) },
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun ThemeModeEntry(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ValueSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
        )
        Text(valueText, modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun ColorSelectorRow(
    label: String,
    currentValue: Long,
    presets: List<ColorPreset>,
    onSelect: (ColorPreset) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEach { preset ->
                val selected = preset.value == currentValue
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(preset.color, MaterialTheme.shapes.small)
                        .clickable { onSelect(preset) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = preset.label,
                            modifier = Modifier.size(16.dp),
                            tint = if (preset.isDark) Color.White else Color.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontFamilyButton(
    label: String,
    family: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Text(label, fontFamily = family)
    }
}

private data class ColorPreset(
    val label: String,
    val color: Color,
    val value: Long,
    val isDark: Boolean,
)

private val FLAG_COLOR_PRESETS = listOf(
    ColorPreset("红色", Color(0xFFC01428), 0xFFC01428L, true),
    ColorPreset("蓝色", Color(0xFF1976D2), 0xFF1976D2L, true),
    ColorPreset("绿色", Color(0xFF388E3C), 0xFF388E3CL, true),
    ColorPreset("橙色", Color(0xFFFF8F00), 0xFFFF8F00L, false),
    ColorPreset("紫色", Color(0xFF7B1FA2), 0xFF7B1FA2L, true),
    ColorPreset("青色", Color(0xFF00838F), 0xFF00838FL, true),
    ColorPreset("深橙", Color(0xFFD84315), 0xFFD84315L, true),
    ColorPreset("橄榄绿", Color(0xFF689F38), 0xFF689F38L, true),
)

private val TEXT_COLOR_PRESETS = listOf(
    ColorPreset("白色", Color(0xFFFFFFFF), 0xFFFFFFFFL, false),
    ColorPreset("黑色", Color(0xFF000000), 0xFF000000L, true),
    ColorPreset("黄色", Color(0xFFFFD600), 0xFFFFD600L, false),
    ColorPreset("亮青", Color(0xFF00E5FF), 0xFF00E5FFL, false),
    ColorPreset("亮红", Color(0xFFFF5252), 0xFFFF5252L, false),
)
