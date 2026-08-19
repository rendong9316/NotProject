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
            P("页面底部面板实时显示当前坐标，GCJ02（高德/火星坐标系）与 WGS84（GPS 原始坐标系）两套并列展示，精确到小数点后 6 位。下方显示定位精度半径（米）及定位模式（GPS 卫星定位 / GPS+网络融合 / 网络辅助定位），精度分为三级：≤10m 为「高精度」，≤50m 为「精度一般」，＞50m 为「精度较低」。")
            P("地图中央蓝色圆点标记为当前位置；开启方向跟踪时，标记会随设备朝向旋转，箭头指向手机当前朝向。首次成功定位后，镜头自动居中并放大至当前位置。")
            P("点击面板标题栏右侧的定位按钮可立即重新获取当前位置；点击整个标题栏可折叠 / 展开面板。折叠状态下面板收为一条薄条，最大化地图可视区域。")

            Heading("1.2 地址搜索")
            P("展开底部面板后，最上方为地址搜索框。输入关键词（如「北京市海淀区」）后，点击右侧搜索图标或键盘上的搜索键触发搜索。服务端返回结果后，列表按距离当前位置由近到远排序显示；点击任意一条结果，地图将跳转至该位置并放置一个临时红色标记，同时将经度/纬度填入输入框。")
            P("同一关键词 1 秒内连续搜索只会触发一次请求（冷却机制），重复搜索相同关键词会优先返回缓存结果，避免触发接口限流。")
            Note("地址搜索和逆地理编码均依赖网络连接，请确保设备已联网并授权定位权限。")

            Heading("1.3 坐标跳转与地址解析")
            P("在搜索框下方的经度 / 纬度输入框中输入坐标，选择坐标类型（GCJ02 或 WGS84），点击右侧跳转箭头按钮，地图将移动至目标坐标，并在该位置放置一个临时红色标记（类型为「跳转目标」，出现在工具页「跳转目标」分组中）。")
            P("跳转完成后点击「解析地址」按钮，调用逆地理编码将坐标转为中文地址，结果显示在输入框下方；点击地址结果条可将地址文本复制到剪贴板，并提示「已复制地址」。")
            P("经度 / 纬度输入框支持粘贴自动解析：从剪贴板粘贴包含一对坐标的文本（如「116.397428,39.90923」，中英文逗号、空格、换行均可作为分隔符），应用会自动拆分为两个输入框。若文本中包含「经度」「纬度」等关键字，自动识别先后顺序。")
            Note("粘贴解析失败时，内容仅填充当前编辑的输入框，不会触发拆分。")

            Heading("1.4 放置旗标")
            P("有两种方式：")
            BulletedItem("长按地图：在普通状态下，长按地图任意位置即可直接在该处放置一个编号旗标（名称默认为序号，如「1」「2」「3」…），无需进入任何特殊模式。")
            BulletedItem("拾取模式：点击地图右上角上方浮动按钮（放大镜图标）进入拾取模式，屏幕中心出现红色准星，手指拖动时准星始终跟随手指前方固定偏移量移动（不遮挡视线），松手在准星位置放置旗标；再次点击右上角浮动按钮（变为关闭图标）退出拾取模式。")
            Note("拾取模式开启时，底部面板会自动折叠，避免遮挡地图操作视野。退出拾取模式后面板自动恢复展开。")
            Note("每次放置或确认拾取时，设备会振动一次作为触觉反馈。")

            Heading("1.5 旗标标签显隐切换")
            P("地图右上角第二个浮动按钮（眼睛图标）用于切换「拾取旗标」的名称标签显示 / 隐藏状态。当旗标较多造成画面拥挤时，可关闭标签只保留圆点标记。当前位置标记和跳转目标的标签不受此开关影响。")

            Heading("1.6 图层切换")
            P("屏幕左上角有标准 / 卫星切换按钮，可在两种地图视图间切换，设置会自动记住上次选择。")
            P("当系统处于深色模式时，标准地图图层会自动切换为夜间暗色风格（无需手动操作）；切换回浅色模式后地图图层也自动还原。")

            Heading("1.7 连续跟踪模式")
            P("首次点击定位按钮成功后，应用自动进入连续跟踪模式：每 1 秒刷新一次位置，同时读取设备传感器数据，地图上的蓝色标记会随手机朝向旋转，指示当前面向的方向。")
            P("点击面板中的定位按钮可重新触发单次定位（重置跟踪状态）；在工具页切换测量模式时跟踪自动暂停，测量结束后恢复。")

            SectionDivider("二、工具页")

            P("工具页包含三张独立可折叠的功能卡片，每张卡片的展开 / 收起状态会自动记忆，下次打开保持原样。点击卡片标题栏即可切换展开状态。")

            Heading("2.1 旗标管理")
            P("第一张卡片显示所有已放置的旗标，按来源分为「拾取旗标」（含手动添加）和「跳转目标」两组，每组各自显示计数和分组清除按钮。")
            SubHeading("单条旗标操作（点击展开）")
            BulletedItem("点击名称行：将坐标填入地图页底部的跳转输入框，需手动点击跳转按钮完成移动（不会自动跳转）")
            BulletedItem("长按名称行：添加到收藏夹（若已在收藏中则提示重复）")
            BulletedItem("展开后下方显示 GCJ02 和 WGS84 两套坐标行，分别点击右侧复制图标可复制对应坐标；另提供编辑（重命名）和删除按钮")
            SubHeading("批量操作")
            BulletedItem("点击每条旗标左侧的眼睛图标可选中 / 取消选中（可见 = 已选中）")
            BulletedItem("选中 2 个及以上旗标后，出现批量操作栏：")
            BulletSubItem("计算距离（至少选 2 个）：以选中顺序的前两个旗标计算距离和正反方位角，结果即时显示在按钮下方")
            BulletSubItem("删除选中：删除所有选中旗标（删除有确认弹窗）")
            BulletSubItem("取消选择：清除所有选中状态")
            SubHeading("其他操作")
            BulletedItem("手动添加按钮：展开表单后填写名称（可留空自动生成编号）、经度、纬度，并选择坐标类型（GCJ02 / WGS84），点击放置按钮即可添加为拾取旗标；选 WGS84 时应用自动将输入值转换为 GCJ02 用于地图显示，同时保存两套坐标")
            BulletedItem("卡片右上角和每个分组标题行右侧均有清除按钮：卡片右上角清除全部旗标，分组清除按钮仅清除对应分组；均有确认弹窗")

            Heading("2.2 测距 / 测面积")
            P("第二张卡片，右上角有「测距 / 测面积」模式切换芯片（测量中不可切换）。本卡片除了跳转地图页测量外，直接在工具页也可撤销和清除。")
            P("操作流程：")
            var stepCount by remember { mutableIntStateOf(0) }
            StepItem("先切换测距或测面积模式，点击开始按钮，程序自动切换到地图页、收起底部面板并进入测量拾取模式", stepCount++)
            StepItem("在地图上依次放置测点（松手放置，拖动时准星随手指移动），同时可在工具页点击「撤销」删除最后一个点，或点击「清除」停止测量并清空所有点", stepCount++)
            StepItem("达到最低点数（测距 2 点，测面积 3 点）后，地图右下角出现「开始计算」浮动按钮，点击后测量完成并自动返回工具页展开结果，测量卡片自动滚动至视野内", stepCount++)
            StepItem("结果区域显示累计总长度（测距）或总面积（测面积）、各分段长度详情；测面积模式时自动闭合末段（最后一点 → 第一点）；下方列出每个测点的 GCJ02 与 WGS84 两套坐标", stepCount++)
            StepItem("测量完成后点击「保存到历史」，弹出命名对话框（默认填入当前时间戳），命名完成后记录存入「历史测量」Tab，可用于日后回放", stepCount++)

            Heading("2.3 角度换算")
            P("第三张卡片，支持双向实时换算，输入有误时显示红色错误提示：")
            BulletedItem("十进制度数 → 时分秒：在上方输入框输入度数，实时显示时分秒结果，点击结果右侧复制图标即可复制")
            BulletedItem("时分秒 → 十进制度数：在下方三个输入框分别输入度、分、秒（度必填，分秒留空视为 0），实时显示十进制结果，同样支持复制")
            Note("时分秒中间结果「度」留空或输入非法字符会显示错误提示；只要「度」为合法数字即可完成换算。")

            SectionDivider("三、收藏夹")

            P("底部导航栏最右侧为「我的」页面，内部使用顶部 Tab 栏 + 左右滑动切换「收藏夹」「历史测量」和「设置」三个子页面，上次停留的子页会被自动记忆。")


            P("收藏夹是独立的坐标快照列表，与地图上的旗标完全分离——删除旗标不会影响已收藏的坐标。收藏条目在应用卸载后仍可通过备份文件恢复。")
            SubHeading("添加收藏")
            P("在工具页「旗标管理」中，长按任意旗标名称即可添加到收藏夹（若坐标相同且名称相同则视为重复，不重复添加）。应用从旧版本升级时，原本标记为「已收藏」的旗标会自动迁移为新的收藏快照。")
            SubHeading("收藏条目操作（点击展开）")
            BulletedItem("点击名称行：自动切换到地图页，将目标坐标填入跳转输入框并执行跳转，镜头定位至该坐标")
            BulletedItem("点击坐标行右侧复制图标：复制单个坐标（GCJ02 或 WGS84）")
            BulletedItem("点击展开后的编辑按钮：重命名收藏条目")
            BulletedItem("点击展开后的删除按钮：删除单条收藏（有确认弹窗）")
            SubHeading("搜索、复制与清空")
            BulletedItem("顶部搜索框：输入关键词按名称过滤收藏条目，忽略大小写")
            BulletedItem("复制全部：将所有收藏以「名称 / GCJ02: x,y / WGS84: x,y」格式一次性复制到剪贴板")
            BulletedItem("清空按钮：删除全部收藏（有确认弹窗），清空后若存在公共目录备份文件，会额外询问是否同时删除备份")
            SubHeading("手动导入 / 导出（JSON 文件）")
            BulletedItem("手动导出：将所有收藏打包为 JSON 文件，调用系统文件选择器（ACTION_CREATE_DOCUMENT），默认文件名为 favorites.json，用户可选择保存位置")
            BulletedItem("从备份恢复：优先尝试读取系统下载目录中的自动备份文件（Locationer-favorites.json）；若备份不存在，则弹出文件选择器（ACTION_OPEN_DOCUMENT）让用户选择之前导出的 JSON 文件")
            BulletedItem("外部文件打开：在系统文件管理器中点击后缀为 .json 的收藏文件，选择用本应用打开即可直接导入（通过 Activity Intent 接收文件 URI）")
            SubHeading("自动备份与清理")
            P("当收藏列表非空时，应用会在系统公共下载目录中自动创建备份文件（文件名 Locationer-favorites.json）。备份成功时按钮下方显示绿色提示文字；失败（如 Android 14+ 缺少存储权限）显示红色提示，此时改用「手动导出」。")
            P("首次进入收藏夹且列表为空时，若检测到下载目录中有离线备份，会弹窗询问是否恢复（显示备份条目数量）。")
            P("自动备份可用时，右侧出现「清理旧备份」按钮：删除下载目录中旧格式的 favorites.json 和 favorites (N).json 文件，仅保留当前 Locationer-favorites.json。")

            SectionDivider("四、历史测量")
            P("历史测量记录了所有已完成的测量任务，支持回放地图轨迹和查看详细坐标。每条记录通过测量卡片中的「保存到历史」按钮生成。")
            SubHeading("列表操作")
            BulletedItem("搜索框：输入关键词按名称过滤测量记录，忽略大小写")
            BulletedItem("复制全部：将所有记录以文本格式一次性复制到剪贴板，格式包含每条记录的名称、模式、节点坐标及累计距离/面积")
            BulletedItem("手动导出：调用系统文件选择器（ACTION_CREATE_DOCUMENT）保存为 JSON 文件，默认文件名 measurements.json")
            BulletedItem("从备份恢复：优先读取系统下载目录中的自动备份文件（Locationer-saved-measurements.json）；若无备份，则弹出文件选择器让用户选择 JSON 文件导入")
            BulletedItem("清空：删除全部历史记录（有确认弹窗）；若存在公共目录备份，清空后会额外询问是否同时删除备份文件")
            SubHeading("单条记录操作（点击名称行展开）")
            BulletedItem("点击名称行：自动切换到地图页，加载该记录的测点数据并进入回放模式，地图上以蓝色折线和填充区域展示测量轨迹，镜头居中至所有节点的质心，顶部出现「返回历史」按钮退出回放")
            BulletedItem("展开后显示摘要信息：测量模式（测距 / 测面积）、节点数量、累计距离（测面积时额外显示面积），以及每个节点的 GCJ02 与 WGS84 坐标")
            BulletedItem("点击展开后的复制按钮：复制该条记录的完整文本（含所有节点坐标和结果）")
            BulletedItem("点击展开后的编辑按钮：弹出重命名对话框，修改记录名称")
            BulletedItem("点击展开后的删除按钮：删除单条记录（有确认弹窗）")
            SubHeading("自动备份")
            P("当历史记录非空时，应用会在系统公共下载目录中自动创建备份文件（文件名 Locationer-saved-measurements.json）。备份成功显示绿色提示，失败（如存储权限不足）显示红色提示。")
            P("首次进入历史测量页且列表为空时，若检测到下载目录中的离线备份，会弹窗询问是否恢复（显示备份条目数量）。")

            SectionDivider("五、设置")

            P("设置页是「我的」页面的第三个子页，通过顶部 Tab 栏或左右滑动切换。本页所有修改实时生效，无需重启应用。设置数据保存在 SharedPreferences 中，跨会话持久化。")
            BulletedItem("主题模式：跟随系统 / 浅色模式 / 深色模式；切换为深色后地图页标准图层会自动启用夜间暗色风格")
            BulletedItem("旗标图标颜色：8 种预设色块快速选择（红、橙、黄、绿、青、蓝、紫、品红）")
            BulletedItem("旗标文字颜色：8 种预设色块，含白色和黑色等常用色")
            BulletedItem("测点图标颜色：8 种预设色块，影响测量过程中节点圆点的颜色")
            BulletedItem("全局字号缩放：0.8x ~ 1.5x 滑块调节，等比缩放所有界面文字（不影响地图上的旗标标签）")
            BulletedItem("字体样式：默认（系统字体）/ 衬线 Serif / 等宽 Monospace，三选一")
            P("底部有「帮助文档」按钮，点击可跳转至当前帮助页。")

            SectionDivider("六、方位角术语")

            P("当计算两个旗标之间的距离时，结果会显示方位角，格式为「度数 + 英文缩写」，例如「45.2° ENE」。以下是 16 个罗盘方向的对照表，帮助您理解每次测量结果中的方向含义。")

            SubHeading("方位角对照表")
            P("方位角以正北为 0°，顺时针旋转，360° 为一周。系统将 360° 均匀分成 16 个方向，每格 22.5°：")
            DirectionTable()
            Note("方向缩写采用国际通用的 16 方位罗盘缩写，例如 NE 表示东北（45°），ESE 表示东东南（112.5°）。")

            SectionDivider("七、常见问题")

            FAQ("为什么坐标显示有两套（GCJ02 和 WGS84）？",
                "GCJ02 是中国国家测绘局制定的加密坐标系统，高德、腾讯等国内地图使用；WGS84 是国际通用的 GPS 原始坐标。两套坐标同时显示，方便您对照使用不同地图服务。剪贴板复制和工具输入框等单次操作场景允许使用单套坐标。")

            FAQ("地址搜索 / 逆地理编码没有返回结果？",
                "这两项功能均依赖网络，请确保设备已联网；同时请在系统设置中授予应用「定位」权限。若网络正常仍无结果，可能是该坐标位于高德服务覆盖区域之外。")

            FAQ("如何把收藏坐标用于其他 App？",
                "收藏条目展开后，点击坐标行右侧复制图标复制单个坐标；或点击「复制全部」按钮一次性复制所有收藏为文本格式。也可使用「手动导出」生成 JSON 文件，分享给其他设备导入。")

            FAQ("收藏夹的自动备份失败（显示红色提示）怎么办？",
                "Android 14 及以上版本对公共存储权限收紧，自动备份可能无法写入下载目录。此时请使用「手动导出」按钮，通过系统文件选择器指定保存位置，效果相同。")

            FAQ("坐标输入框粘贴后没有自动拆分？",
                "粘贴解析仅在文本中包含两个数值且含有分隔符（逗号、中文逗号、空格、换行、分号等）时触发。纯数字无分隔符不会被识别为坐标对，内容将填充到当前正在编辑的输入框中。")
            FAQ("收藏和旗标有什么区别？",
                "旗标是地图上的临时标记（拾取旗标、跳转目标两类），可创建、编辑、删除，随应用会话存在；收藏是独立的坐标快照，从旗标长按添加，不随旗标删除而消失，适合长期保存重要点位供日后快速跳转，且支持 JSON 导入导出、自动备份。")

            FAQ("地图旗标文字标签太密看不清怎么办？",
                "前往地图页，点击右上角第二个浮动按钮（眼睛图标），即可一键隐藏所有「拾取旗标」的名称标签，只保留圆点标记。当前位置和跳转目标标记的标签不受影响。")

            FAQ("如何退出应用？",
                "在首页（地图 / 工具 / 我的任意一屏）按系统返回键，会弹出「再按一次退出应用」提示；在两秒内再次按返回键即可退出。")

            FAQ("测量中途想取消，可以清除吗？",
                "可以。测量进行中时，工具页的测量卡片中「清除」按钮始终可用，点击后清空所有测点并结束测量；「撤销」按钮可逐个删除已放置的测点。")

            FAQ("手动添加旗标时，输入 WGS84 坐标可以吗？",
                "可以。手动添加表单内提供「GCJ02 / WGS84」坐标类型单选，选 WGS84 后应用自动将输入值转为 GCJ02 坐标用于地图显示，同时保存两套坐标供后续查看。")

            FAQ("历史测量记录点击后地图上没有显示轨迹？",
                "点击历史记录卡片名称行会自动切换到地图页并进入回放模式。若地图页没有切换，请检查是否授予了定位权限。")

            FAQ("收藏文件可以从其他设备导入吗？",
                "可以。应用导出的 JSON 文件为标准文本格式，可在任何设备上编辑或传输。在目标设备上通过系统文件管理器打开 JSON 文件，选择用本应用打开即可导入。")

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
