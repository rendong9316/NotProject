# 动画补全 + 测量历史/气泡 + 批量收藏 + 单框粘贴 规格说明书

## 一、问题、用户与目标

### 问题背景
当前 Locationer v1.1 已具备完整的地图、旗标、测距测面积、收藏、设置等核心功能，但交互层面有 5 个明确的体验短板：
1. **转场动画缺失**：面板折叠/卡片展开/页面切换均为直接跳变，"高级感"和丝滑度不足
2. **测量数据一次性**：完成测距/测面积后，关闭页面或重测即丢失，无法保留多次测量结果供对比
3. **测量过程中看不清每段距离**：必须返回工具页才能读到分段距离，地图摆放测点时无法实时感知每段长度
4. **旗标批量操作不完整**：工具页选中多个旗标后只能"计算距离/删除选中"，缺少最常用的"批量添加到收藏夹"
5. **坐标粘贴繁琐**：从微信/短信/网页复制的"116.397428, 39.90923"格式坐标，必须手动拆分成经度/纬度两个输入框，极易出错

### 目标用户
- 测量/工程用户：频繁使用测距测面积，对比多次测量结果
- 户外/巡查用户：常在移动端复制粘贴坐标、批量打点并收藏
- 所有用户：动画感知提升"App 流畅精致"主观评分

### 非目标（不做）
- 不接入新的导航SDK或路线算路（属后续规划）
- 不改变 SharedPreferences → Room 的存储架构（测量历史采用现有 SP JSON 即可，100 条记录以内无压力）
- 不引入 Compose Animation 之外的第三方动画库
- 不改变 Marker 差分渲染 / Bitmap 缓存（P0 级优化，后续独立实施）

---

## 二、功能需求（5 个子特性）

### 特性 1：动画补全（全局 7 处动画）

| 编号 | 位置 | 动画目标 | 触发时机 |
|------|------|---------|---------|
| A1 | 地图页 UnifiedPanel | 面板展开/收起使用 `AnimatedVisibility + expandVertically/shrinkVertically`（220ms FastOutSlowInEasing），并在 Card 上 `animateContentSize` | 用户点击标题栏折叠按钮、或测量模式驱动自动收起/展开 |
| A2 | 我的页面 MyScreen TabRow | Tab 指示器使用 `tabIndicatorOffset`，实现左右滑动/点击时的滑动动画；`pagerTabIndicatorOffset` 若可用优先 | 切换收藏夹 ↔ 设置 |
| A3 | 工具页 ToolsScreen：测量结果 | 测量完成后滚动到结果卡片时用 `animateScrollToItem(index, scrollOffset)` 代替 `scrollToItem`；同时卡片展开动作加 `animateContentSize` | 计算完成后自动滚动 |
| A4 | 工具页 / 收藏夹列表条目展开 | 旗标条目、收藏条目点击展开（编辑/删除/复制区域出现）时 `animateContentSize()` 包裹展开块 | 点击条目名称行 |
| A5 | 主 Activity 三 Tab 切换 | 当前 `alpha + zIndex` 切换改为 `AnimatedContent(targetState = selectedTab, transitionSpec = { slideIntoContainer(AnimatedContentScope.SlideDirection.Left/Right) + fadeThrough() })`，方向按"地图←→工具←→我的"Tab 序 | 点击底部导航、或导航事件跳转 |
| A6 | 收藏夹 Tab 切换子页 | 与主 Activity 同级别的 TabRow + HorizontalPager，已经有滑动手势；补充 `TabRowDefaults.Indicator` 用 `animate` 修饰符 | 切换收藏夹/设置 |
| A7 | 镜头跳转缓动 | `animateCamera(CU, durationMs, callback)` 统一跳转镜头时长：普通跳转 400ms、首次定位 600ms，并取消冲突动画（正在 animateCamera 时先 `stopAnimation`）| `jumpTarget` 变化或首次定位成功 |

#### 动画约束
- 所有动画时长不超过 320ms（面板/卡片）/ 400ms（镜头），避免拖慢节奏
- 动画被用户手势中断时（新的跳转、手动折叠）无异常抛错
- 深色/浅色模式切换时动画不会闪白/闪黑

---

### 特性 2：测量记录保存与历史浏览

#### 交互流程
1. **触发时机**：测距/测面积"开始计算"→结果展示后，工具页结果区右上角出现「💾 保存此测量」按钮
2. **命名对话框**：弹出 `AlertDialog`，内容为：
   - 标题："保存测量记录"
   - 单段结果摘要："测距 / 总长度 42.18m，3 个测点" 或 "测面积 / 面积 1,234.56 m²，6 个测点"
   - 名称输入框：默认名称自动生成 = `"${测距/测面积}_${MMdd_HHmm}"`（如"测距_0817_2235"），用户可编辑
   - 取消 / 保存按钮
3. **取消**：什么都不做；**保存**：写入持久化历史列表
4. **历史列表入口**：工具页测距/测面积卡片头部标题栏右侧新增"历史"按钮（📋），点击后卡片展开切换为"历史记录列表视图"；再次点击或左上角返回箭头回到默认测量视图
5. **历史条目**：按保存时间倒序显示，每条显示：名称、类型图标📏/📐、总结果摘要、保存时间、测点数量；**条目操作**：
   - ▶️ 载入：恢复该测量的 waypoints 到地图（状态=COMPLETED，自动切到地图页并展示折线与测点）
   - ✏️ 重命名
   - 🗑️ 删除
   - 顶部"清空历史"按钮（带确认弹窗）
6. **历史条目不超过 100 条时自动滚动；超过 100 条提示"历史已满"**（实际上限 500 条，100 条时提醒清理）

#### 数据模型
新增数据类 `MeasurementRecord`：
```
id: Long              // System.currentTimeMillis()
name: String          // 用户命名
mode: DISTANCE | AREA
totalDist: Double
totalArea: Double
waypoints: List<MeasurementPoint>   // 复用现有 data class
segments: List<Segment>             // 复用现有 data class
savedAt: Long           // 保存时间戳
```

#### 持久化
- 在 MapViewModel 所在 SP `map_state` 新增 key `"measurement_history"`，JSON 数组序列化（与现有 `measurement` key 方式一致）
- 存/读通过 `MapViewModel.historySave()` / `historyLoad()` 方法封装，不暴露 SP 细节

---

### 特性 3：地图页 Polyline 分段距离气泡

#### 视觉目标
在测距/测面积过程中，每放置完第 N 个测点（N≥2）后，在 **第 N-1 → N 段折线的中点坐标** 处，显示一个"白底圆角 + 深色文字"的小标签（类似高德 InfoWindow，但常驻），内容为 "📏 `xx.xx m`"（≥1000m 显示 km）。气泡跟随测点变动自动更新/删除/新增。

#### 实现方式（避免 setCustomTexture 复杂裁剪，使用更稳的动态 Marker 方案）
- 每段 Polyline 的中点坐标 = `(latlng1.lat+latlng2.lat)/2, (lng1+lng2)/2`（足够精确，球面近似中点误差<1%）
- 使用 AMap SDK `addMarker` + 自定义 BitmapDescriptor 实现：`createDistanceBubbleBitmap(text: String, width: Int)` → 绘制白色圆角矩形 + 9 号字灰色距离文字，anchor(0.5, 0.5)
- 管理结构：`segmentBubbleMarkers: SparseArray<Marker>`，index = 段序号（0-based）
- 更新策略（与 waypoint 渲染联动，复用同一个 LaunchedEffect）：
  - N 个 waypoint → N-1 条折线段 → N-1 个气泡（面积模式闭合段额外 1 个气泡，序号 = N）
  - 新增测点 → 仅为新增段生成 1 个气泡 Marker，重建其他 Marker 目前先全量重建（后续 Marker 差分优化统一处理，此特性不重复造轮子，保证正确性优先）
- 气泡显示状态：`measurementState == PLACING 或 COMPLETED` 期间保持显示；`CLEAR/STOP` 后全部 remove
- 冲突处理：气泡 Marker 不响应点击，设置 `marker.setClickable(false)`，避免遮挡旗标点击事件（优先级 flag > waypoint > bubble）

#### 可读性
- 文字颜色：夜间模式 #FFEAEAEA（近白），日间模式 #FF111111（近黑）；背景 92% 不透明白/深灰（与主题适配）
- 气泡宽度自适应文字，左右 padding 各 6dp，圆角 4dp，气泡下方无小箭头（小箭头在"跟点位走"的场景下反而挡视线）

---

### 特性 4：工具页旗标多选 → 批量加入收藏夹

#### UI 位置
- [ToolsScreen 旗标管理卡片]() 的"选中 2+ 个旗标后显示的批量操作栏"：从左到右顺序改为 `[计算距离] [＋ 添加到收藏夹] [🗑 删除选中] [取消选择]`（共 4 个按钮）

#### 逻辑
- 按钮触发：对每个已选中（visible=true）的旗标，调用 `FavoritesViewModel.addFromFlag(flag)`（现有 API 已存在且已去重）
- 执行前对列表分组计数：已在收藏（重复）N 条，新增成功 M 条；结束后 Toast 显示：
  - 全部新增：`"已添加 M 条到收藏夹"`
  - 有重复：`"已添加 M 条，N 条已在收藏夹中未重复添加"`
  - 全部重复：`"选中的 N 条都已在收藏夹中"`
- 操作完成后不取消选中（用户可能还要算距离/删除，自行取消选择）
- 当选中 0 条时，4 个操作栏按钮不显示（保持现有行为）

---

### 特性 5：单框粘贴坐标自动拆分

#### 触发入口
在地图页 UnifiedPanel 的经度 / 纬度输入框上方，新增一个 `OutlinedTextField`「💡 粘贴坐标 (支持 经度,纬度 或 纬度,经度 自动识别)」（建议放"搜索地址"输入框下方、"坐标跳转"区域上方，属于一个新的次级区域）

#### 支持的输入格式（正则识别）
1. `"116.397428, 39.90923"` → 经度,纬度（逗号分隔，支持空格可选）
2. `"116.397428，39.90923"` → 中文逗号
3. `"116.397428 39.90923"` → 空格分隔
4. `"116.397428;39.90923"` → 分号分隔
5. `"39.90923, 116.397428"` → 反序（纬度,经度）— 通过值域自动判断：第一个绝对值 > 90 必然是经度在前，否则提示用户选择

#### 反序判断与交互
- 如果两段数值：段 A `|val| ≤ 90` 且 段 B `|val| ≤ 180` 且 `|val| > 90` → 段 B 是经度（正常顺序 B,A 或 A,B 的自动识别见下方算法）
- **算法**：解析出两个 Double 为 x, y
  - 若 `|x| > 90 && |y| ≤ 90` → 顺序是 `(lon=x, lat=y)` 直接填入
  - 若 `|y| > 90 && |x| ≤ 90` → 顺序是 `(lon=y, lat=x)` 直接填入
  - 若两者都 ≤ 90（可能是 (lon,lat) 或 (lat,lon)，如 30.5, 110.3 — 110.3 实际是经度，30.5 既可能是东经也可能是北纬）→ 算法：因为经度范围 [-180,180]、纬度 [-90,90]，所以**取两者中绝对值较大者作经度**。这个启发式在大多数坐标（跨经度大的地点）下正确。边缘场景（如 70°, 80° 两个都在纬度范围内）：Toast 提示 "请确认自动识别是否正确，必要时手动调换"
- 填入后自动触发「解析地址」按钮（如果地图已就绪），同时将光标留在单框内便于继续粘贴
- 填入后单框清空（保持下次粘贴方便）或保留输入值便于核对（**建议保留**，值变灰色表示"已应用"，下次粘贴自动覆盖）
- 输入框 `trailingIcon` = 📋 粘贴图标；若用户没输入就点 📋：从 `ClipboardManager` 读取 primary clip 文本自动填入并立即解析

#### 错误处理
- 文本中无法解析出 2 个合法 Double：Toast 提示 `"格式错误，请输入 经度,纬度 格式（如 116.397428, 39.90923）"`
- 解析成功但数值越界（lon>180 或 lat>90）：Toast 提示 `"解析出的坐标超出有效范围"`
- 与现有 coordType 联动：填入后仍按当前选择的 GCJ02/WGS84 解释（单框不单独加 coordType，保持简单）

---

## 三、非功能需求

### NFR1：性能
- 动画不得引起明显掉帧（≤ 60fps，在 Pixel 4a 级别的真机上肉眼无卡顿）
- 新增距离气泡：当测点超过 50 个时总气泡数 50，地图滚动 FPS ≥ 45
- 测量历史列表 500 条：加载/滚动无 ANR

### NFR2：稳定性
- 所有新增状态（历史列表、单框解析结果、气泡 Marker 列表）在 **屏幕旋转/夜间切换/导航切回** 后状态一致不崩溃
- 动画中断不抛异常（使用 Compose 原生动画，天然可中断；`animateCamera` 使用 `cancelable = false` 的默认行为）

### NFR3：可访问性
- 新增的"保存测量/历史/批量收藏/单框输入框"等按钮均带 `contentDescription`
- 测量历史条目使用语义化 `Modifier.semantics { contentDescription = "测量记录，测距，总长 42.18m，保存于 2026-08-17 22:35" }`

### NFR4：向后兼容
- 现有 `measurement`（正在编辑的单条测量）SP key **完全保留**，不破坏旧数据
- 新增 `"measurement_history"` 为独立 key，缺省为空数组
- 旧版本升级到新版本后：**不迁移正在编辑的那条测量 到历史**（只在用户手动点保存时入历史）

### NFR5：与现有逻辑解耦
- 单框粘贴不改变现有 lonText/latText 更新逻辑，只作为"填充两个输入框"的 helper
- 批量收藏不修改 `addFromFlag()` 的去重逻辑，只在外层统计去重结果

---

## 四、约束、依赖、假设、开放问题

### 约束
- **不新增第三方依赖**：动画全部用 Compose Foundation/Animation 官方 API；历史存储复用现有 SharedPreferences；气泡复用 AMap Marker + 自定义 Bitmap
- **不修改 flagStyle 数据结构**（避免迁移问题）
- **不修改 build.gradle.kts compileSdk/targetSdk/minSdk**

### 依赖
- 已存在：`MapViewModel`、`FavoritesViewModel`（`addFromFlag` API 已就绪）、`ToolsScreen` 多选操作栏、`MapScreen` Waypoint 渲染 LaunchedEffect

### 假设
- 用户通过高德搜索结果跳转到的地点，lon/lat 值域启发式（较大值为经度）在 95% 以上常用场景成立；小概率误判用户可手动调换
- 测量历史 500 条以内 JSON SP 性能可接受；超过此阈值用户可主动清空

### 已解决的开放问题
- 距离气泡用 Marker 还是 setCustomTexture？→ **Marker 方案**（易实现、无需纹理 pack、点击穿透易控）
- 历史列表是独立对话框还是卡片内嵌切换？→ **卡片内嵌视图切换**（避免多层对话框叠加，流程更顺）
- 单框粘贴放在"搜索"下还是"跳转输入框"下？→ **搜索下**（都是外部文本输入类操作聚类，便于发现）

---

## 五、验收标准（Acceptance Criteria）

### 功能类（rule）

| ID | 类型 | 内容 | 验证方式 |
|----|------|------|---------|
| AC-A1 | rule | 地图页面板点击展开/收起、测量自动收起时均有 200ms+ 的高度过渡动画而非跳变 | 运行 App，肉眼观察 + 慢速录制回放确认动画存在 |
| AC-A2 | rule | 我的页面 Tab 切换时指示器横向平滑滑动（非跳变），Tab 文字颜色渐变过渡 | 左右滑动 + 点击 Tab 切换 |
| AC-A3 | rule | 测量完成后从地图返回工具页时，结果卡片展开是动画的且列表自动滚动平滑到位 | 放置 5+ 个测点计算后观察滚动是否非瞬间完成 |
| AC-A4 | rule | 工具页旗标列表和收藏夹列表点击条目展开/收起有高度动画 | 随机点 3 个条目确认 |
| AC-A5 | rule | 底部导航三 Tab 之间有方向感的滑动过渡（从左→右 Tab：向左滑入 + 淡出旧页面） | 地图 → 工具 → 我的 → 工具 → 地图 连续切换 |
| AC-A6 | rule | `animateCamera` 时长：跳转 400ms、首次定位 600ms，且新跳转发起会取消上一次动画 | 快速连续点击两次跳转，镜头不出现两次动画叠加 |
| AC-MH1 | rule | 测量完成后结果区右上出现「💾 保存此测量」按钮，点击弹出命名对话框，默认名含日期时间 | 测距 + 测面积各测一次验证 |
| AC-MH2 | rule | 保存后卡片标题栏右侧出现「📋 历史」按钮，点击切换到历史视图；按时间倒序显示 N 条记录，每条可载入/重命名/删除 | 保存 5 条后操作每条三个功能并验证结果回到地图时折线完整 |
| AC-MH3 | rule | 已保存的 5 条记录在 **杀掉 App 重开** 后依然存在，且"载入"能完整恢复 waypoints、segments、totalDist/Area | 冷启动验证 |
| AC-MH4 | rule | 历史条目上限 500 条；保存第 101 条时 Toast 提醒用户清理，保存第 501 条时拒绝并告知 | 构造 500 条 JSON 后尝试保存 |
| AC-DB1 | rule | 测距模式放置 2 个点后，Polyline 中点出现一个气泡显示该段距离（米或 km）；放置第 3 个点后有 2 个气泡 | 放置 5 个点，肉眼数气泡数=段数 |
| AC-DB2 | rule | 测面积模式 4 个点闭合时：有 4 段气泡（含 n→1 闭合段）| 手动数 + 对比 segments 数量 |
| AC-DB3 | rule | 按撤销/清除/停止测量时，气泡 Marker 同步删除，不残留 | 完整测完 → 清除 → 地图检查 Marker |
| AC-FB1 | rule | 工具页选中 3 条旗标，点击「添加到收藏夹」，FavoritesViewModel 数据 +3 | 收藏夹页验证数量增加 |
| AC-FB2 | rule | 选中的 3 条里有 1 条已在收藏，Toast 显示"已添加 2 条，1 条已在收藏夹未重复添加" | 先把 1 条收藏，再批量选 3 条（含它）|
| AC-FB3 | rule | 操作后选中状态保持，用户仍可继续删除/算距离（选中不自动取消） | 点"添加到收藏夹"后看眼睛图标是否仍全亮 |
| AC-SP1 | rule | 单框输入 "116.397428, 39.90923" → lon 得 116.397428，lat 得 39.90923；中文逗号/空格/分号分隔同样生效 | 4 种格式各输入一次 |
| AC-SP2 | rule | 单框输入 "39.90923, 116.397428"（反序，第二段 >90）→ 自动识别 lon=116.397428，lat=39.90923 | 反序输入验证 |
| AC-SP3 | rule | 单框点击📋图标时，系统剪贴板文本自动填入并解析；剪贴板无有效文本时 Toast 提示 | 复制 1 条坐标到剪贴板后点击📋 |
| AC-SP4 | rule | 单框输入 "hello, world" / "1000,39" / "91,181" 这类越界/非数字时 Toast 报错且不修改 lon/lat | 错误输入验证 |
| AC-SP5 | rule | 填入后 lonText/latText 的变化与手动直接输入效果相同：会持久化到 SP 并跳转按钮可用 | 填完后看底部面板输入框内容与 SP 持久化（杀进程恢复）|

### 质量类（rubric）

| ID | 维度 | 刻度 (0-4) | 通过阈值 | 证据来源 |
|----|------|-----------|---------|---------|
| QR-Ani-1 | 动画一致性 | 4=所有 A1-A7 动画时长统一风格、无突兀跳变；3=1 处不协调；2=2 处；1=3 处以上 | ≥3 | 真机主观录屏评分 |
| QR-Ani-2 | 动画流畅度 | 4=全程 60fps；3=偶见丢帧不影响；2=经常丢帧 | ≥3 | 真机 + Android Studio Profiler 查看帧时间 |
| QR-MH-1 | 历史列表性能 | 4=500 条滑动流畅无白块；3=轻微卡顿；2=严重 | ≥3 | 构造 500 条 JSON 后实测 |
| QR-DB-1 | 气泡可读性 | 4=距离文字清晰、与地图对比度足够、不挡旗标；3=个别遮挡；2=严重遮挡 | ≥3 | 夜间/白天模式下各拍 1 张截图评估 |
| QR-SP-1 | 单框交互直觉性 | 4=无需看说明即可使用；3=提示足够；2=易误解 | ≥3 | 2 名测试者无说明操作评分 |

---

## 六、变更影响范围（文件清单）

| 文件 | 改动内容 |
|------|---------|
| `MainActivity.kt` | AnimatedContent 替换 alpha/zIndex 切页 + 镜头动画统一封装 |
| `MapScreen.kt` | UnifiedPanel AnimatedVisibility；距离气泡 Marker 管理 + createDistanceBubbleBitmap；新单框粘贴输入区 + Clipboard 读取；镜头 animateCamera 冲突处理 |
| `MapViewModel.kt` | 新增 MeasurementRecord 数据类 + history CRUD 方法 + 单条保存 load/save JSON 封装（复用现有 saveMeasurement 思路）|
| `ToolsScreen.kt` | 测量卡片：结果区保存按钮、历史/默认视图切换、历史列表 LazyColumn；旗标卡片多选操作栏新增「＋添加到收藏夹」按钮 + 调用计数逻辑 |
| `MyScreen.kt` | TabRow tabIndicatorOffset 动画；卡片 animateContentSize 应用于子页 |
| `FavoritesScreen.kt`（若有）/ 收藏条目组件 | 展开 animateContentSize（如果收藏夹条目展开没有动画）|
