package com.example.locationer

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "我的"页面 — 帮助文档子页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帮助文档") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── 封面 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Help,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "定位器",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "操作帮助文档",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionDivider("一、地图页")

            Heading("1.1 查看当前位置")
            P("页面底部面板显示当前坐标（GCJ02 与 WGS84 两套并列），下方有定位精度信息（高精度 / 精度一般 / 精度较低）。点击面板右上角的定位按钮可刷新当前位置；面板最上方可点击折叠 / 展开。")

            Heading("1.2 坐标跳转与地址解析")
            P("展开底部面板，在经度 / 纬度输入框中输入坐标，选择下方坐标类型（GCJ02 或 WGS84），点击右侧跳转箭头按钮，地图将移动至目标坐标，并在该位置放置一个临时红色标记。")
            P("跳转后点击解析地址按钮，可将该坐标转为中文地址，显示在输入框下方。")

            Heading("1.3 放置旗标")
            P("有两种方式：")
            BulletedItem("长按地图：在普通状态下，长按地图任意位置即可直接在该处放置一个旗标，无需进入特殊模式。")
            BulletedItem("拾取模式：点击地图右上角的浮动按钮（Search 图标）进入拾取模式，此时准星跟随手指移动，松手在准星位置放置旗标；再次点击右上角浮动按钮（变为 Close 图标）退出拾取模式。")
            Note("拾取模式开启时，底部面板会自动折叠，避免遮挡地图。")

            Heading("1.4 图层切换")
            P("屏幕左上角有标准 / 卫星切换按钮，可在两种地图视图间切换，设置会自动记住上次选择。")

            SectionDivider("二、工具页")

            Heading("2.1 旗标管理")
            P("第一个卡片显示所有已放置的旗标，分为「拾取旗标」和「跳转目标」两组。")
            SubHeading("单条旗标操作（点击展开）")
            BulletedItem("点击名称行：将地图跳转至该坐标位置")
            BulletedItem("长按名称行：添加到收藏夹（若已在收藏中则提示）")
            BulletedItem("展开后下方有复制 GCJ02 / 复制 WGS84 / 编辑 / 删除按钮")
            SubHeading("批量操作")
            BulletedItem("点击每条旗标左侧的眼睛图标可选中，选中的旗标显示为「可见」状态")
            BulletedItem("选中多个旗标后，底部出现批量操作栏：")
            BulletSubItem("计算距离（至少选 2 个）：计算前两个选中旗标间的距离和方位角（含正反两个方向的方位角）")
            BulletSubItem("删除选中：删除所有选中旗标")
            BulletSubItem("取消选择：清除所有选中状态")
            SubHeading("其他操作")
            BulletedItem("手动添加按钮：弹出表单，可输入名称和经纬度，选择坐标类型后放置")
            BulletedItem("卡片右上角清除全部按钮：一键清除所有旗标")

            Heading("2.2 测距 / 测面积")
            P("第二个卡片，右上角有两个模式切换按钮：测距与测面积。")
            P("操作流程：")
            var stepCount by remember { mutableIntStateOf(0) }
            StepItem("点击开始按钮，程序自动切换到地图页并进入测量拾取模式", stepCount++)
            StepItem("在地图上依次放置测点（松手放置，可拖拽移动准星）", stepCount++)
            StepItem("放置过程中可随时点击撤销删除最后一个点，或点击清除停止测量并清空所有点", stepCount++)
            StepItem("达到最低点数（测距 2 点，测面积 3 点）后，地图右下角出现开始计算按钮，点击后返回工具页并展开结果", stepCount++)
            StepItem("结果区域显示：累计总长度（测距）或面积（测面积）、各分段详情、各测点坐标（GCJ02 + WGS84）", stepCount++)

            Heading("2.3 角度换算")
            P("第三个卡片，支持双向转换：")
            BulletedItem("十进制度数 → 时分秒：在上方输入框输入度数，实时显示时分秒结果，点击右侧复制图标即可复制")
            BulletedItem("时分秒 → 十进制度数：在下方三个输入框分别输入度、分、秒，实时显示十进制结果，同样支持复制")

            SectionDivider("三、收藏夹")

            P("收藏夹独立于地图旗标，用于保存重要的坐标点位。")
            SubHeading("添加收藏")
            P("在工具页「旗标管理」中，长按任意旗标名称即可添加到收藏夹（若坐标相同且名称相同则视为重复，不重复添加）。")
            SubHeading("收藏条目操作（点击展开）")
            BulletedItem("点击条目名称行：自动切换到地图页，将目标坐标填入跳转输入框并执行跳转")
            BulletedItem("点击坐标行右侧复制图标：复制单个坐标（GCJ02 或 WGS84）")
            BulletedItem("点击展开后的编辑按钮：重命名收藏条目")
            BulletedItem("点击展开后的删除按钮：删除单条收藏")
            SubHeading("批量操作")
            BulletedItem("顶部搜索框可过滤收藏名称")
            BulletedItem("导出全部收藏按钮：将所有收藏以「名称 / GCJ02: x,y / WGS84: x,y」格式一次性复制到剪贴板")
            BulletedItem("清空按钮：删除全部收藏（有确认弹窗）")

            SectionDivider("四、设置")

            P("设置页提供以下自定义选项：")
            BulletedItem("主题模式：跟随系统 / 浅色模式 / 深色模式")
            BulletedItem("旗标图标：图标颜色（预设色块选择）、图标直径（滑块调整）")
            BulletedItem("旗标文字：文字颜色（预设色块选择）、字号大小（滑块调整）")
            BulletedItem("测点图标：测量标记点的颜色（预设色块选择）")
            BulletedItem("全局字体：字号缩放比例（0.8x ~ 1.5x）、字体样式（默认 / 衬线 / 等宽）")
            P("所有设置修改后实时生效，无需重启应用。")

            SectionDivider("五、方位角术语")

            P("当计算两个旗标之间的距离时，结果会显示方位角，格式为「度数 + 英文缩写」，例如「45.2° ENE」。以下是 16 个罗盘方向的对照表，帮助您理解每次测量结果中的方向含义。")

            SubHeading("方位角对照表")
            P("方位角以正北为 0°，顺时针旋转，360° 为一周。系统将 360° 均匀分成 16 个方向，每格 22.5°：")
            DirectionTable()
            Note("方向缩写采用国际通用的 16 方位罗盘缩写，例如 NE 表示东北（45°），ESE 表示东东南（112.5°）。")

            SectionDivider("六、常见问题")

            FAQ("为什么坐标显示有两套（GCJ02 和 WGS84）？",
                "GCJ02 是中国国家测绘局制定的加密坐标系统，高德、腾讯等国内地图使用；WGS84 是国际通用的 GPS 原始坐标。两套坐标同时显示，方便您对照使用不同地图服务。")

            FAQ("逆地理编码有时没有返回结果？",
                "逆地理编码依赖网络和地理位置权限，请确保网络连接正常，并在系统设置中授予应用「定位」权限。")

            FAQ("如何把收藏坐标用于其他 App？",
                "收藏条目展开后，点击右侧复制图标复制坐标；或点击「导出全部收藏」一次性复制所有收藏。")

            FAQ("收藏和旗标有什么区别？",
                "旗标是地图上的临时标记，可创建、编辑、删除，存储在地图数据中；收藏是独立的坐标快照，从旗标长按添加，不随旗标删除而消失，适合保存重要点位供日后快速跳转。")

            Spacer(Modifier.height(24.dp))
            Text(
                "定位器 v1.0  ·  帮助文档",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── 帮助页排版辅助组件 ────────────────────────────────────────────────────────

@Composable
private fun SectionDivider(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun P(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 22.sp,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun BulletedItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text("· ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun BulletSubItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text("  · ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun StepItem(text: String, stepCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$stepCount、",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "注：",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun FAQ(question: String, answer: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Q：$question",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "A：$answer",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ─── 方位角术语对照表 ──────────────────────────────────────────────────────────

private data class DirectionEntry(
    val deg: Int,
    val abbr: String,
    val cn: String,
)

private val DIRECTION_TABLE = listOf(
    DirectionEntry(0,   "N",   "北"),
    DirectionEntry(22,  "NNE", "北北东"),
    DirectionEntry(45,  "NE",  "东北"),
    DirectionEntry(67,  "ENE", "东东北"),
    DirectionEntry(90,  "E",   "东"),
    DirectionEntry(112, "ESE", "东南东"),
    DirectionEntry(135, "SE",  "东南"),
    DirectionEntry(157, "SSE", "南东南"),
    DirectionEntry(180, "S",   "南"),
    DirectionEntry(202, "SSW", "南西南"),
    DirectionEntry(225, "SW",  "西南"),
    DirectionEntry(247, "WSW", "西西南"),
    DirectionEntry(270, "W",   "西"),
    DirectionEntry(292, "WNW", "西北西"),
    DirectionEntry(315, "NW",  "西北"),
    DirectionEntry(337, "NNW", "北北西"),
)

@Composable
private fun DirectionTable() {
    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // 表头
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("度数", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
            Text("缩写", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
            Text("中文", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 分隔线
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
        // 数据行
        DIRECTION_TABLE.forEach { entry ->
            DirectionRow(entry)
        }
    }
}

@Composable
private fun DirectionRow(entry: DirectionEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${entry.deg}°", fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(44.dp))
        Text(entry.abbr, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(44.dp))
        Text(entry.cn, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
